package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentPackTest {
    @Test fun coreSubjectsAreBundled() {
        assertTrue(TestContent.packs.map { it.subject.id }.toSet().containsAll(setOf("c", "python", "dsa", "dbms", "cn")))
    }

    @Test fun everySubjectHasAtLeast12Questions() {
        TestContent.packs.forEach { assertTrue("${it.subject.id} has ${it.questions.size}", it.questions.size >= 12) }
    }

    @Test fun questionIdsAreGloballyUnique() {
        val ids = TestContent.questions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun questionsAreWellFormed() {
        TestContent.questions.forEach { q ->
            assertEquals(4, q.choices.size)
            assertEquals("duplicate choices in ${q.id}", 4, q.choices.toSet().size)
            assertTrue(q.answerIndex in 0..3)
            assertTrue(q.explanation.length > 10)
        }
    }

    @Test fun everyTopicHasAtLeastOneLessonInItsSubject() {
        TestContent.packs.forEach { p ->
            val lessonTopics = p.lessons.map { it.topic }.toSet()
            p.questions.map { it.topic }.toSet().forEach { t ->
                assertTrue("No lesson for topic '$t' in ${p.subject.id}", t in lessonTopics)
            }
        }
    }

    @Test fun originalCQuestionsArePreserved() {
        val prompts = TestContent.pack("c").questions.map { it.prompt }
        assertTrue("Which function is the usual entry point of a C program?" in prompts)
        assertTrue("What does a pointer variable store?" in prompts)
        assertEquals(12, TestContent.pack("c").questions.count { it.id.removePrefix("c").toInt() <= 12 })
    }

    @Test fun parserRejectsBadAnswerIndex() {
        val bad = """{"id":"x","title":"X","description":"d","questions":[{"id":"q","topic":"t","prompt":"p",
            "choices":["a","b","c","d"],"answer":7,"explanation":"e"}],"lessons":[]}"""
        assertThrows(IllegalArgumentException::class.java) { ContentParser.parse(bad) }
    }

    @Test fun parserRejectsWrongChoiceCount() {
        val bad = """{"id":"x","title":"X","description":"d","questions":[{"id":"q","topic":"t","prompt":"p",
            "choices":["a","b"],"answer":0,"explanation":"e"}],"lessons":[]}"""
        assertThrows(IllegalArgumentException::class.java) { ContentParser.parse(bad) }
    }
}
