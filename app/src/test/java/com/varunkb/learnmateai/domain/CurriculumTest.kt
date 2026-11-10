package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumTest {
    private val catalog = Catalog(TestContent.packs)
    private fun prog(id: String, subject: String, st: LessonStatus, last: Long = 1) =
        LessonProgress(id, subject, st, false, last, if (st == LessonStatus.Completed) last else 0)

    @Test fun catalogueListsEverySubjectAndLesson() {
        assertEquals(TestContent.packs.size, catalog.subjects.size)
        assertEquals(TestContent.packs.sumOf { it.lessons.size }, catalog.allLessons.size)
        assertTrue(catalog.paths.isNotEmpty())
    }

    @Test fun progressCountsCompletedAndStarted() {
        val ls = catalog.lessons("c")
        val p = mapOf(
            ls[0].id to prog(ls[0].id, "c", LessonStatus.Completed),
            ls[1].id to prog(ls[1].id, "c", LessonStatus.InProgress)
        )
        val sp = Curriculum.progressFor(ls, p)
        assertEquals(1, sp.completed); assertEquals(1, sp.inProgress); assertEquals(ls.size, sp.total)
        assertEquals(100 / ls.size, sp.percent)
    }

    @Test fun nextLessonSkipsCompleted() {
        val ls = catalog.lessons("c")
        val p = mapOf(ls[0].id to prog(ls[0].id, "c", LessonStatus.Completed))
        assertEquals(ls[1].id, Curriculum.nextLesson(ls, p)!!.id)
        assertEquals(ls[0].id, Curriculum.nextLesson(ls, emptyMap())!!.id)
    }

    @Test fun nextLessonResumesAfterLastOpened() {
        val ls = catalog.lessons("c")
        val p = mapOf(ls[0].id to prog(ls[0].id, "c", LessonStatus.Completed), ls[3].id to prog(ls[3].id, "c", LessonStatus.Completed))
        assertEquals(ls[4].id, Curriculum.nextLesson(ls, p, ls[3].id)!!.id)
    }

    @Test fun allCompleteGivesNoNextLesson() {
        val ls = catalog.lessons("c")
        val p = ls.associate { it.id to prog(it.id, "c", LessonStatus.Completed) }
        assertNull(Curriculum.nextLesson(ls, p))
    }

    @Test fun neighboursAreInSubjectOrder() {
        val ls = catalog.lessons("c")
        val (prev, next) = catalog.neighbours(ls[1].id)
        assertEquals(ls[0].id, prev!!.id); assertEquals(ls[2].id, next!!.id)
        assertNull(catalog.neighbours(ls.first().id).first)
        assertNull(catalog.neighbours(ls.last().id).second)
    }

    @Test fun weakTopicTriggersRevision() {
        val topic = catalog.lessons("c")[2].topic
        val m = listOf(TopicMastery("c", topic, 0.35, 1, 5))
        val rec = Curriculum.recommend(catalog, "c", m, emptyMap(), null)
        assertEquals(RecommendationKind.Revise, rec.kind)
        assertEquals(topic, rec.lesson!!.topic)
    }

    @Test fun freshLearnerGetsFirstLesson() {
        val rec = Curriculum.recommend(catalog, "c", emptyList(), emptyMap(), null)
        assertEquals(RecommendationKind.FirstLesson, rec.kind)
        assertEquals(catalog.lessons("c").first().id, rec.lesson!!.id)
    }

    @Test fun startedLearnerContinues() {
        val ls = catalog.lessons("c")
        val p = mapOf(ls[0].id to prog(ls[0].id, "c", LessonStatus.Completed))
        val rec = Curriculum.recommend(catalog, "c", emptyList(), p, ls[0].id)
        assertEquals(RecommendationKind.ContinueLearning, rec.kind)
        assertEquals(ls[1].id, rec.lesson!!.id)
    }

    @Test fun finishedSubjectSuggestsAllDoneOrNextSubject() {
        val ls = catalog.lessons("c")
        val p = ls.associate { it.id to prog(it.id, "c", LessonStatus.Completed) }
        val rec = Curriculum.recommend(catalog, "c", emptyList(), p, null)
        assertTrue(rec.kind == RecommendationKind.AllDone || rec.kind == RecommendationKind.NextSubject)
    }

    @Test fun prerequisitesWarnWhenUnmet() {
        val s = Subject("x", "X", "d", prerequisites = listOf("c"))
        val cat = Catalog(TestContent.packs + ContentPack(s, emptyList(), emptyList()))
        assertEquals(listOf("c"), Curriculum.missingPrerequisites(s, cat, emptyMap()).map { it.id })
        val ls = cat.lessons("c")
        val done = ls.associate { it.id to prog(it.id, "c", LessonStatus.Completed) }
        assertTrue(Curriculum.missingPrerequisites(s, cat, done).isEmpty())
    }

    @Test fun everyLessonTopicAndEveryQuestionTopicLineUp() {
        catalog.subjects.forEach { s ->
            val lessonTopics = catalog.lessons(s.id).map { it.topic }.toSet()
            catalog.topics(s.id).forEach { assertTrue("${s.id}/$it", it in lessonTopics) }
        }
        assertNotNull(catalog.lesson(catalog.allLessons.first().id))
    }
}
