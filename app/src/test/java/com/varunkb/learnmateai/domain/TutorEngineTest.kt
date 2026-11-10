package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorEngineTest {
    private val lessons = TestContent.lessons
    private val index = Bm25Index(lessons)
    private val tutor = RetrievalTutorEngine(index, lessons)

    private fun ctx(subject: String = "c", title: String = "C Programming", attempts: Int = 0, mastery: List<TopicMastery> = emptyList()) =
        LearnerContext("Asha", subject, title, attempts, Recommender.accuracy(mastery), mastery)

    /** (query, active subject, expected lesson id). A hit counts if the expected lesson is the top result. */
    private val gold = listOf(
        Triple("Explain pointers in C", "c", "c-pointers"),
        Triple("what is a dereference operator", "c", "c-pointers"),
        Triple("how does malloc work", "c", "c-memory"),
        Triple("what causes a memory leak", "c", "c-memory"),
        Triple("difference between while and do while", "c", "c-loops"),
        Triple("what is a function prototype", "c", "c-functions"),
        Triple("why does 7/2 give 3", "c", "c-operators"),
        Triple("what does strlen return", "c", "c-strings"),
        Triple("how to open a file with fopen", "c", "c-files"),
        Triple("what is a struct", "c", "c-struct"),
        Triple("what causes a segmentation fault", "c", "c-debug"),
        Triple("what are C data types", "c", "c-types"),
        Triple("explain recursion with a base case", "c", "c-recursion"),
        Triple("what is a mutable default argument in a function", "python", "p-functions"),
        Triple("explain list comprehension", "python", "p-lambda"),
        Triple("how do dictionaries work", "python", "p-dicts"),
        Triple("difference between list and tuple", "python", "p-lists"),
        Triple("how do I handle exceptions with try except", "python", "p-exceptions"),
        Triple("what is self in a class", "python", "p-classes"),
        Triple("how to read a file safely", "python", "p-files"),
        Triple("what is string slicing", "python", "p-strings"),
        Triple("explain big o notation", "dsa", "d-bigo"),
        Triple("what is a linked list", "dsa", "d-linkedlist"),
        Triple("stack vs queue lifo fifo", "dsa", "d-stack"),
        Triple("how does binary search work", "dsa", "d-search"),
        Triple("explain bfs and dfs", "dsa", "d-traversal"),
        Triple("what is a hash collision", "dsa", "d-hash"),
        Triple("which sorting algorithm is stable", "dsa", "d-sorting"),
        Triple("what is a binary search tree", "dsa", "d-tree"),
        Triple("what is a primary key", "dbms", "s-keys"),
        Triple("explain inner join and left join", "dbms", "s-joins"),
        Triple("difference between where and having", "dbms", "s-agg"),
        Triple("what is normalization and 3nf", "dbms", "s-norm"),
        Triple("what does acid stand for", "dbms", "s-acid"),
        Triple("why use an index", "dbms", "s-index"),
        Triple("what is ddl and dml", "dbms", "s-sqlcats"),
        Triple("how to use group by", "dbms", "s-agg"),
        Triple("what is a many to many relationship", "dbms", "s-er")
    )

    @Test fun retrievalTop1AccuracyOnGoldSetIsHigh() {
        val misses = gold.filter { (q, s, expected) ->
            index.search(q, preferSubject = s, limit = 1).firstOrNull()?.lesson?.id != expected
        }
        val acc = 100.0 * (gold.size - misses.size) / gold.size
        println("RETRIEVAL top-1 accuracy: %.1f%% (%d/%d); misses=%s".format(acc, gold.size - misses.size, gold.size, misses.map { it.first }))
        assertTrue("accuracy $acc, misses: $misses", acc >= 85.0)
    }

    @Test fun retrievalTop3AccuracyOnGoldSetIsVeryHigh() {
        val hits = gold.count { (q, s, expected) -> index.search(q, preferSubject = s, limit = 3).any { it.lesson.id == expected } }
        println("RETRIEVAL top-3 accuracy: %.1f%% (%d/%d)".format(100.0 * hits / gold.size, hits, gold.size))
        assertTrue(hits.toDouble() / gold.size >= 0.92)
    }

    @Test fun lessonReplyCitesSourceAndIncludesExample() {
        val r = tutor.answer("Explain pointers in C", ctx())
        assertEquals(ReplyKind.Lesson, r.kind)
        assertEquals("Pointers", r.sourceTitle)
        assertTrue(r.text.contains("int *ptr"))
        assertTrue((r.confidence ?: 0) >= 40)
    }

    @Test fun unknownQuestionAdmitsItDoesNotKnow() {
        val r = tutor.answer("What is the capital of France?", ctx())
        assertEquals(ReplyKind.NoMatch, r.kind)
        assertTrue(r.text.contains("don't have a reliable answer"))
        val r2 = tutor.answer("explain quantum entanglement", ctx())
        assertEquals(ReplyKind.NoMatch, r2.kind)
    }

    @Test fun emptyInputIsHandled() {
        assertEquals(ReplyKind.Empty, tutor.answer("   ", ctx()).kind)
    }

    @Test fun greetingIsRecognised() {
        val r = tutor.answer("hello", ctx())
        assertEquals(ReplyKind.Greeting, r.kind)
        assertTrue(r.text.contains("Asha"))
    }

    @Test fun progressWithoutAttemptsPromptsAQuiz() {
        val r = tutor.answer("Show my progress", ctx())
        assertEquals(ReplyKind.Progress, r.kind)
        assertTrue(r.text.contains("not completed"))
    }

    @Test fun progressUsesMasteryData() {
        val m = listOf(TopicMastery("c", "Arrays", 0.9, 5, 5), TopicMastery("c", "Pointers", 0.3, 1, 5))
        val r = tutor.answer("Show my progress", ctx(attempts = 2, mastery = m))
        assertTrue(r.text.contains("Pointers"))
        assertTrue(r.text.contains("Arrays"))
        assertTrue(r.text.contains("60%"))
    }

    @Test fun studyPlanTargetsWeakestTopic() {
        val m = listOf(TopicMastery("c", "Arrays", 0.9, 5, 5), TopicMastery("c", "Pointers", 0.3, 1, 5))
        val r = tutor.answer("Which topic should I revise?", ctx(attempts = 2, mastery = m))
        assertEquals(ReplyKind.StudyPlan, r.kind)
        assertTrue(r.text.contains("Pointers"))
    }

    @Test fun honestAboutNotBeingAnLlm() {
        val r = tutor.answer("are you chatgpt or an llm?", ctx())
        assertEquals(ReplyKind.About, r.kind)
        assertTrue(r.text.contains("not a generative AI"))
    }

    @Test fun weakTopicNoteAppearsOnLessonAnswer() {
        val m = listOf(TopicMastery("c", "Pointers", 0.2, 1, 5))
        val r = tutor.answer("Explain pointers", ctx(attempts = 1, mastery = m))
        assertTrue(r.text.contains("weaker topics"))
    }

    @Test fun activeSubjectBreaksTiesForSharedConcepts() {
        val py = tutor.answer("explain loops", ctx("python", "Python Programming"))
        val c = tutor.answer("explain loops", ctx("c", "C Programming"))
        assertEquals("Loops: for, while, range", py.sourceTitle)
        assertEquals("Loops: for, while and do-while", c.sourceTitle)
    }

    @Test fun stemmerIsConsistentForPluralsAndGerunds() {
        assertEquals(TextAnalyzer.stem("pointers"), TextAnalyzer.stem("pointer"))
        assertEquals(TextAnalyzer.stem("looping"), TextAnalyzer.stem("loop"))
        assertEquals(TextAnalyzer.stem("indexes"), TextAnalyzer.stem("index"))
        assertEquals(TextAnalyzer.stem("queues"), TextAnalyzer.stem("queue"))
    }
}
