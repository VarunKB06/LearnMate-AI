package com.varunkb.learnmateai.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.varunkb.learnmateai.domain.ContentParser
import com.varunkb.learnmateai.domain.QuizItem
import com.varunkb.learnmateai.domain.QuizScoring
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests: run on a phone/emulator with `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class LearnMateDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LearnMateDatabase
    private lateinit var repo: LearnRepository

    private fun loadPacks() = context.assets.list(AppContainer.CONTENT_DIR).orEmpty()
        .filter { it.endsWith(".json") }
        .map { ContentParser.parse(context.assets.open("${AppContainer.CONTENT_DIR}/$it").bufferedReader().use { r -> r.readText() }) }

    @Before fun setUp() {
        db = LearnMateDatabase.inMemory(context)
        repo = LearnRepository(db)
    }

    @After fun tearDown() { db.close() }

    @Test fun bundledContentSeedsAllFourSubjects() = runBlocking {
        val packs = loadPacks()
        packs.forEach { repo.replaceSubject(it.subject, it.questions) }
        assertEquals(4, repo.subjectList().size)
        assertEquals(packs.sumOf { it.questions.size }, repo.questionCount())
        assertTrue(repo.questionCount() >= 120)
        assertEquals(30, repo.questions("c").size)
    }

    @Test fun reseedingKeepsAttemptsAndMastery() = runBlocking {
        val c = loadPacks().first { it.subject.id == "c" }
        repo.replaceSubject(c.subject, c.questions)
        val items = c.questions.take(3).map { QuizItem(it, it.choices, it.answerIndex) }
        val outcome = QuizScoring.score("c", items, items.map { it.correctIndex }, emptyMap())
        repo.commitQuiz("c", 1L, items, items.map { it.correctIndex }, listOf(10, 20, 30), outcome)

        repo.replaceSubject(c.subject, c.questions) // simulates a content update
        assertEquals(1, repo.attempts.first().size)
        assertTrue(repo.masteryMap("c").isNotEmpty())
        assertEquals(30, repo.questions("c").size)
    }

    @Test fun replaceSubjectRemovesQuestionsDroppedFromThePack() = runBlocking {
        val c = loadPacks().first { it.subject.id == "c" }
        repo.replaceSubject(c.subject, c.questions)
        repo.replaceSubject(c.subject, c.questions.take(20))
        assertEquals(20, repo.questions("c").size)
    }

    @Test fun commitQuizPersistsAttemptAnswersAndMastery() = runBlocking {
        val c = loadPacks().first { it.subject.id == "c" }
        repo.replaceSubject(c.subject, c.questions)
        val items = c.questions.take(5).map { QuizItem(it, it.choices, it.answerIndex) }
        val chosen = items.mapIndexed { i, it -> if (i < 3) it.correctIndex else (it.correctIndex + 1) % 4 }
        val outcome = QuizScoring.score("c", items, chosen, emptyMap())
        val id = repo.commitQuiz("c", 100L, items, chosen, List(5) { 500L }, outcome, now = 200L)

        val attempt = repo.attempts.first().single()
        assertEquals(id, attempt.id)
        assertEquals(3, attempt.correct)
        assertEquals(5, attempt.total)
        assertEquals(5, db.learningDao().answersFor(id).size)
        assertEquals(3, db.learningDao().answersFor(id).count { it.isCorrect })
        assertEquals(5, repo.masteryMap("c").values.sumOf { it.total })
        assertEquals(items.map { it.question.id }.toSet(), repo.recentQuestionIds("c").toSet())
    }

    @Test fun clearProgressOnlyAffectsThatSubject() = runBlocking {
        val packs = loadPacks()
        packs.forEach { repo.replaceSubject(it.subject, it.questions) }
        for (id in listOf("c", "python")) {
            val q = packs.first { it.subject.id == id }.questions.take(2)
            val items = q.map { QuizItem(it, it.choices, it.answerIndex) }
            val outcome = QuizScoring.score(id, items, items.map { it.correctIndex }, emptyMap())
            repo.commitQuiz(id, 1L, items, items.map { it.correctIndex }, listOf(1, 1), outcome)
        }
        repo.clearProgress("c")
        val remaining = repo.attempts.first()
        assertEquals(1, remaining.size)
        assertEquals("python", remaining.single().subjectId)
        assertTrue(repo.masteryMap("c").isEmpty())
        assertTrue(repo.masteryMap("python").isNotEmpty())
    }

    @Test fun profileRoundTrips() = runBlocking {
        repo.saveProfile(ProfileEntity(name = "Asha", education = "UG", goal = "Placement", activeSubjectId = "dbms"))
        val p = repo.profile.first()
        assertEquals("Asha", p?.name)
        assertEquals("dbms", p?.activeSubjectId)
    }

    @Test fun chatIsTrimmedToTheMostRecentMessages() = runBlocking {
        repeat(150) { i ->
            repo.addChat(
                ChatMessageEntity(role = "user", text = "q$i", createdAt = i.toLong()),
                ChatMessageEntity(role = "tutor", text = "a$i", createdAt = i.toLong())
            )
        }
        val chat = repo.chat.first()
        assertEquals(200, chat.size)
        assertEquals("a149", chat.last().text)
    }

    @Test fun legacyImportCreatesAttemptsAndMastery() = runBlocking {
        val c = loadPacks().first { it.subject.id == "c" }
        repo.replaceSubject(c.subject, c.questions)
        val legacyJson = """[{"date":1700000000000,"correct":4,"total":5,"topics":{"Pointers":{"correct":1,"total":2},"Arrays":{"correct":3,"total":3}}}]"""
        val attempts = com.varunkb.learnmateai.domain.LegacyMigration.parseAttempts(legacyJson)
        val mastery = com.varunkb.learnmateai.domain.LegacyMigration.foldTopics(attempts).map { (t, s) ->
            com.varunkb.learnmateai.domain.LegacyMigration.masteryFromCounts("c", t, s.first, s.second).toEntity(0L)
        }
        repo.importLegacy(
            attempts.map { AttemptEntity(subjectId = "c", startedAt = it.date, finishedAt = it.date, correct = it.correct, total = it.total, source = "legacy") },
            mastery
        )
        assertEquals("legacy", repo.attempts.first().single().source)
        assertEquals(2, repo.masteryMap("c").size)
    }
}
