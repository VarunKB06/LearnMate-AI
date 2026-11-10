package com.varunkb.learnmateai.perf

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import androidx.room.Room
import com.varunkb.learnmateai.data.AppContainer
import com.varunkb.learnmateai.data.AttemptAnswerEntity
import com.varunkb.learnmateai.data.AttemptEntity
import com.varunkb.learnmateai.data.LearnMateDatabase
import com.varunkb.learnmateai.data.MasteryEntity
import com.varunkb.learnmateai.data.SubjectEntity
import com.varunkb.learnmateai.domain.Bm25Index
import com.varunkb.learnmateai.domain.LearnerContext
import com.varunkb.learnmateai.domain.QuizSelector
import com.varunkb.learnmateai.domain.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device benchmark. It measures real latency of the app's own work (database, adaptive quiz
 * selection, tutor retrieval, index build), plus memory, battery and temperature, on whatever
 * phone it runs on. Numbers are only meaningful when produced on the target device.
 * Writes go to a temporary database file, so the learner's data is never modified.
 */
class BenchmarkRunner(private val context: Context, private val container: AppContainer) {

    private val queries = listOf(
        "Explain pointers in C", "how does malloc work", "what is a linked list", "explain big o notation",
        "how does binary search work", "what is a primary key", "explain inner join and left join",
        "what is normalization", "how do dictionaries work", "what is a hash collision",
        "difference between list and tuple", "what does acid stand for", "explain bfs and dfs",
        "what is a function prototype", "how do I handle exceptions", "what causes a memory leak",
        "Show my progress", "Which topic should I revise?", "what is the capital of France", "hello"
    )

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private inline fun timeMs(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000.0
    }

    private class BatterySnapshot(val percent: Int, val tempC: Double, val charging: Boolean)

    private fun battery(): BatterySnapshot {
        val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return BatterySnapshot(if (level < 0) -1 else level * 100 / scale, temp, plugged)
    }

    private fun memoryLine(): String {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val javaUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1_048_576.0
        return "PSS=%.1f MB, Java heap used=%.1f MB, native heap=%.1f MB".format(mi.totalPss / 1024.0, javaUsedMb, nativeMb)
    }

    private fun deviceInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
            "ABI ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} | cores ${Runtime.getRuntime().availableProcessors()} | " +
            "total RAM %.1f GB | low-memory-class ${am.isLowRamDevice}".format(mem.totalMem / 1_073_741_824.0)
    }

    private fun learnerContext() = LearnerContext("Benchmark", "c", "C Programming", 0, 0, emptyList())

    /** Fixed-iteration benchmark; takes a few seconds. */
    suspend fun runQuick(onStatus: (String) -> Unit): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.appendLine("LearnMate AI quick benchmark | ${now()}")
        sb.appendLine(deviceInfo())
        sb.appendLine("Memory before: ${memoryLine()}")
        val b0 = battery()

        val lessons = container.lessons
        val questions = container.repository.questions("c")
        val mastery = container.repository.masteryMap("c")
        val recent = container.repository.recentQuestionIds("c")

        onStatus("Building retrieval index…")
        repeat(3) { Bm25Index(lessons) } // warm-up
        val build = List(10) { timeMs { Bm25Index(lessons) } }
        sb.appendLine(Stats.summary("Tutor index build (${lessons.size} lessons)", build))

        onStatus("Reading question bank from Room…")
        container.repository.questions("c")
        val dbRead = ArrayList<Double>()
        repeat(100) { dbRead += timeMs { container.repository.questions("c") } }
        sb.appendLine(Stats.summary("Room read (30 questions)", dbRead))

        onStatus("Selecting adaptive quizzes…")
        repeat(50) { QuizSelector.select(questions, mastery, recent, 10) }
        val select = List(300) { timeMs { QuizSelector.select(questions, mastery, recent, 10) } }
        sb.appendLine(Stats.summary("Adaptive quiz selection (10 of 30)", select))

        onStatus("Running tutor queries…")
        val ctx = learnerContext()
        queries.forEach { container.tutor.answer(it, ctx) } // warm-up
        val tutor = ArrayList<Double>()
        repeat(10) { queries.forEach { q -> tutor += timeMs { container.tutor.answer(q, ctx) } } }
        sb.appendLine(Stats.summary("Tutor answer (${queries.size} queries x10)", tutor))

        onStatus("Measuring database writes (temporary DB)…")
        val writes = measureWrites()
        sb.appendLine(Stats.summary("Room write: quiz commit (10 answers + mastery)", writes))

        val b1 = battery()
        sb.appendLine("Memory after: ${memoryLine()}")
        sb.appendLine("Battery: ${b0.percent}% -> ${b1.percent}% | temp ${b0.tempC} -> ${b1.tempC} C | charging=${b1.charging}")
        sb.appendLine("Startup (this launch): parse content ${container.metrics.parseContentMs} ms, seed DB ${container.metrics.seedDatabaseMs} ms, " +
            "legacy import ${container.metrics.legacyImportMs} ms, build index ${container.metrics.buildIndexMs} ms")
        sb.toString()
    }

    /**
     * Sustained load for [seconds]: repeatedly runs tutor queries, quiz selection and Room reads and
     * records throughput each second. A falling ops/s over time suggests thermal throttling.
     * For battery cost, run it unplugged for several minutes (battery percentage is coarse).
     */
    suspend fun runSustained(seconds: Int, onStatus: (String) -> Unit): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.appendLine("LearnMate AI sustained benchmark (${seconds}s) | ${now()}")
        sb.appendLine(deviceInfo())
        sb.appendLine("Memory before: ${memoryLine()}")
        val b0 = battery()
        if (b0.charging) sb.appendLine("WARNING: device is charging, so battery drain numbers are not meaningful.")

        val questions = container.repository.questions("c")
        val mastery = container.repository.masteryMap("c")
        val recent = container.repository.recentQuestionIds("c")
        val ctx = learnerContext()
        val perSecond = ArrayList<Int>()
        val opLatency = ArrayList<Double>()
        val startMs = System.currentTimeMillis()
        var windowStart = startMs
        var ops = 0
        var qi = 0
        while (System.currentTimeMillis() - startMs < seconds * 1000L) {
            opLatency += timeMs {
                container.tutor.answer(queries[qi % queries.size], ctx)
                QuizSelector.select(questions, mastery, recent, 10)
                container.repository.questions("c")
            }
            qi++
            ops++
            val t = System.currentTimeMillis()
            if (t - windowStart >= 1000) {
                perSecond += ops
                ops = 0
                windowStart = t
                onStatus("Sustained load… ${perSecond.size}/$seconds s (${perSecond.last()} ops/s)")
            }
        }
        val b1 = battery()
        val n = perSecond.size
        if (n >= 10) {
            val tenth = maxOf(1, n / 10)
            val first = Stats.mean(perSecond.take(tenth).map { it.toDouble() })
            val last = Stats.mean(perSecond.takeLast(tenth).map { it.toDouble() })
            sb.appendLine("Throughput: first 10%% = %.0f ops/s, last 10%% = %.0f ops/s (ratio %.2f; well below 1.0 suggests throttling)".format(first, last, last / first))
        }
        sb.appendLine(Stats.summary("Per-iteration latency (tutor + select + Room read)", opLatency))
        sb.appendLine("Total iterations: ${opLatency.size}")
        sb.appendLine("Memory after: ${memoryLine()}")
        sb.appendLine("Battery: ${b0.percent}% -> ${b1.percent}% | temp ${b0.tempC} -> ${b1.tempC} C | charging=${b1.charging}")
        sb.toString()
    }

    private suspend fun measureWrites(): List<Double> {
        val name = "learnmate_bench.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context.applicationContext, LearnMateDatabase::class.java, name).build()
        try {
            db.contentDao().upsertSubjects(listOf(SubjectEntity("bench", "Bench", "Benchmark only")))
            val out = ArrayList<Double>()
            repeat(20) { i ->
                val t0 = System.nanoTime()
                db.learningDao().commitQuiz(
                    AttemptEntity(subjectId = "bench", startedAt = 0, finishedAt = i.toLong(), correct = 7, total = 10),
                    List(10) { AttemptAnswerEntity(attemptId = 0, questionId = "q$it", topic = "t${it % 3}", chosenIndex = 0, isCorrect = it % 2 == 0, timeMs = 1000) },
                    List(3) { MasteryEntity("bench", "t$it", 0.5, 1, 2, 0) }
                )
                out += (System.nanoTime() - t0) / 1_000_000.0
            }
            return out
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
