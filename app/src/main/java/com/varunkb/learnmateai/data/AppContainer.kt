package com.varunkb.learnmateai.data

import android.content.Context
import android.os.SystemClock
import com.varunkb.learnmateai.domain.Bm25Index
import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.ContentPack
import com.varunkb.learnmateai.domain.ContentParser
import com.varunkb.learnmateai.domain.LegacyMigration
import com.varunkb.learnmateai.domain.Lesson
import com.varunkb.learnmateai.domain.RetrievalTutorEngine
import com.varunkb.learnmateai.domain.TutorEngine
import com.varunkb.learnmateai.llm.LlmAdvisor
import com.varunkb.learnmateai.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Time spent in each start-up step, shown on the Diagnostics screen. */
data class StartupMetrics(
    val parseContentMs: Long = 0,
    val seedDatabaseMs: Long = 0,
    val legacyImportMs: Long = 0,
    val buildIndexMs: Long = 0,
    val seeded: Boolean = false,
    val legacyImported: Boolean = false
) {
    val totalMs: Long get() = parseContentMs + seedDatabaseMs + legacyImportMs + buildIndexMs
}

/** Manual dependency container (no DI framework needed for an app this size). */
class AppContainer(private val context: Context) {
    val database: LearnMateDatabase by lazy { LearnMateDatabase.build(context) }
    val repository: LearnRepository by lazy { LearnRepository(database) }
    val planStore: PlanStore by lazy { PlanStore(context) }

    /** The on-device Llama model (ExecuTorch). It loads lazily and only when a model file is installed. */
    val llm: LlmManager by lazy { LlmManager(context) }

    lateinit var packs: List<ContentPack>
        private set
    lateinit var catalog: Catalog
        private set
    lateinit var lessons: List<Lesson>
        private set
    lateinit var index: Bm25Index
        private set
    lateinit var tutor: TutorEngine
        private set
    lateinit var advisor: LlmAdvisor
        private set

    @Volatile
    var metrics: StartupMetrics = StartupMetrics()
        private set

    @Volatile
    private var ready = false
    private val lock = Mutex()

    /** Idempotent: loads content, seeds Room, migrates legacy data and builds the tutor index. */
    suspend fun initialize() {
        if (ready) return
        lock.withLock {
            if (ready) return
            withContext(Dispatchers.Default) {
                var t = SystemClock.elapsedRealtime()
                fun lap(): Long {
                    val now = SystemClock.elapsedRealtime()
                    return (now - t).also { t = now }
                }

                val files = context.assets.list(CONTENT_DIR).orEmpty().filter { it.endsWith(".json") }.sorted()
                packs = files.map { f ->
                    ContentParser.parse(context.assets.open("$CONTENT_DIR/$f").bufferedReader().use { it.readText() })
                }
                catalog = Catalog(packs)
                lessons = catalog.allLessons
                val parseMs = lap()

                val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
                val expectedQuestions = packs.sumOf { it.questions.size }
                val needsSeed = meta.getInt(KEY_CONTENT_VERSION, 0) != CONTENT_VERSION ||
                    repository.questionCount() != expectedQuestions
                if (needsSeed) {
                    packs.forEach { repository.replaceSubject(it.subject, it.questions) }
                    meta.edit().putInt(KEY_CONTENT_VERSION, CONTENT_VERSION).apply()
                }
                val seedMs = lap()

                val imported = LegacyPrefsImporter(context, repository).importIfNeeded()
                val legacyMs = lap()

                index = Bm25Index(lessons)
                tutor = RetrievalTutorEngine(index, lessons)
                advisor = LlmAdvisor(llm, catalog)
                val indexMs = lap()

                metrics = StartupMetrics(parseMs, seedMs, legacyMs, indexMs, needsSeed, imported)
            }
            ready = true
        }
    }

    companion object {
        const val CONTENT_DIR = "content"
        const val META_PREFS = "learnmate_meta"
        const val KEY_CONTENT_VERSION = "content_version"

        /** Bump when any file in assets/content changes so installed apps re-seed the question banks. */
        const val CONTENT_VERSION = 2
    }
}

/** One-time import of the SharedPreferences data used before Room. The old file is left untouched. */
class LegacyPrefsImporter(private val context: Context, private val repository: LearnRepository) {
    suspend fun importIfNeeded(): Boolean {
        val meta = context.getSharedPreferences(AppContainer.META_PREFS, Context.MODE_PRIVATE)
        if (meta.getBoolean(KEY_DONE, false)) return false
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val name = legacy.getString("name", "").orEmpty()
        val attempts = LegacyMigration.parseAttempts(legacy.getString("attempts", null))
        if (name.isBlank() && attempts.isEmpty()) {
            meta.edit().putBoolean(KEY_DONE, true).apply()
            return false
        }
        val subjects = repository.subjectList()
        if (subjects.isEmpty()) return false // seeding has not happened; try again next launch

        if (repository.getProfile() == null && name.isNotBlank()) {
            repository.saveProfile(
                ProfileEntity(
                    name = name,
                    education = legacy.getString("education", "Undergraduate").orEmpty(),
                    goal = legacy.getString("goal", "Programming").orEmpty(),
                    activeSubjectId = LegacyMigration.subjectIdFor(legacy.getString("subject", "C").orEmpty(), subjects)
                )
            )
        }
        // The old app only contained C questions, so its quiz history belongs to the C subject.
        val subjectId = subjects.firstOrNull { it.id == "c" }?.id ?: subjects.first().id
        val now = System.currentTimeMillis()
        val attemptEntities = attempts.map {
            AttemptEntity(
                subjectId = subjectId, startedAt = it.date, finishedAt = it.date,
                correct = it.correct, total = it.total, source = "legacy"
            )
        }
        val mastery = LegacyMigration.foldTopics(attempts).map { (topic, s) ->
            LegacyMigration.masteryFromCounts(subjectId, topic, s.first, s.second).toEntity(now)
        }
        repository.importLegacy(attemptEntities, mastery)
        meta.edit().putBoolean(KEY_DONE, true).apply()
        return true
    }

    companion object {
        const val LEGACY_PREFS = "learnmate_local_v1"
        const val KEY_DONE = "legacy_import_done"
    }
}
