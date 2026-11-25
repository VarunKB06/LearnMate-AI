package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlannerTest {
    private val catalog = Catalog(TestContent.packs)
    private val vlsiGoal = GoalProfile("VLSI verification engineer", "Career", listOf("sv"), emptyList(), 6, 8, "Beginner")

    private fun plan(goal: GoalProfile = vlsiGoal, mastery: List<TopicMastery> = emptyList(), progress: Map<String, LessonProgress> = emptyMap(), previous: StudyPlan? = null) =
        StudyPlanner.plan(catalog, goal, mastery, progress, 1000L, previous)

    private fun done(id: String, subject: String) = LessonProgress(id, subject, LessonStatus.Completed, false, 1L, 1L)

    @Test
    fun goalSubjectsAndPrerequisitesAreScheduled() {
        val p = plan()
        val subjects = p.weeks.flatMap { it.items }.filter { it.kind != PlanItemKind.Quiz }.map { it.subjectId }.toSet()
        assertTrue("goal subject present", "sv" in subjects)
        val prereqs = catalog.subject("sv")!!.prerequisites.filter { catalog.subject(it) != null }
        prereqs.forEach { assertTrue("prerequisite $it present", it in subjects) }
    }

    @Test
    fun prerequisitesComeBeforeTheSubjectThatNeedsThem() {
        val items = plan().weeks.flatMap { it.items }.filter { it.kind == PlanItemKind.Learn }
        val firstSv = items.indexOfFirst { it.subjectId == "sv" }
        val lastPrereq = items.indexOfLast { it.subjectId in catalog.subject("sv")!!.prerequisites }
        assertTrue(firstSv < 0 || lastPrereq < firstSv)
    }

    @Test
    fun everyWeekFitsTheHoursAndEndsWithAQuiz() {
        val p = plan()
        assertTrue(p.weeks.isNotEmpty())
        p.weeks.forEach { w ->
            assertEquals(PlanItemKind.Quiz, w.items.last().kind)
            val lessonMinutes = w.items.filter { it.kind != PlanItemKind.Quiz }.sumOf { it.minutes }
            // one oversized first item is allowed, otherwise the budget holds
            if (w.items.size > 2) assertTrue(lessonMinutes <= vlsiGoal.hoursPerWeek * 60 - StudyPlanner.QUIZ_MINUTES)
        }
        assertTrue(p.weeks.size <= vlsiGoal.weeks)
    }

    @Test
    fun completedLessonsAreSkipped() {
        val first = plan().weeks.first().items.first { it.lessonId != null }
        val p = plan(progress = mapOf(first.lessonId!! to done(first.lessonId!!, first.subjectId)))
        assertTrue(p.weeks.flatMap { it.items }.none { it.lessonId == first.lessonId })
        assertTrue(p.skippedLessons >= 1)
    }

    @Test
    fun masteredTopicsAreSkippedAndWeakTopicsBecomeRevision() {
        val base = plan()
        val item = base.weeks.flatMap { it.items }.first { it.kind == PlanItemKind.Learn }
        val lesson = catalog.lesson(item.lessonId!!)!!
        val strong = plan(mastery = listOf(TopicMastery(lesson.subjectId, lesson.topic, 0.95, 9, 10)))
        assertTrue(strong.weeks.flatMap { it.items }.none { it.lessonId == lesson.id })

        val weak = plan(mastery = listOf(TopicMastery(lesson.subjectId, lesson.topic, 0.2, 1, 6)))
        val revise = weak.weeks.flatMap { it.items }.filter { it.kind == PlanItemKind.Revise }
        assertTrue(revise.isNotEmpty())
        assertEquals(PlanItemKind.Revise, weak.weeks.first().items.first().kind)
    }

    @Test
    fun tooLittleTimeReportsUnscheduledLessons() {
        val tight = vlsiGoal.copy(hoursPerWeek = 1, weeks = 1)
        val p = plan(tight)
        assertEquals(1, p.weeks.size)
        assertTrue(p.unscheduledLessons > 0)
        assertTrue(p.notes.any { it.contains("do not fit") })
    }

    @Test
    fun replanIncreasesRevisionAndNotesCompletedWork() {
        val first = plan()
        val doneIds = first.weeks.first().items.mapNotNull { it.lessonId }
        val progress = doneIds.associateWith { done(it, catalog.lesson(it)!!.subjectId) }
        val second = plan(progress = progress, previous = first)
        assertEquals(first.revision + 1, second.revision)
        assertTrue(second.notes.any { it.contains("completed") })
    }

    @Test
    fun everythingDoneGivesEmptyPlanWithANote() {
        val all = catalog.subjects.flatMap { catalog.lessons(it.id) }.associate { it.id to done(it.id, it.subjectId) }
        val p = plan(progress = all)
        assertTrue(p.weeks.isEmpty())
        assertTrue(p.notes.isNotEmpty())
    }

    @Test
    fun planJsonRoundTrips() {
        val p = plan().copy(coachNote = "Keep going \"today\".\nTwo tips.", coachStale = true)
        val back = PlanJson.planFromJson(PlanJson.planToJson(p))
        assertNotNull(back)
        assertEquals(p, back)
        assertEquals(vlsiGoal, PlanJson.goalFromJson(PlanJson.goalToJson(vlsiGoal)))
        assertNull(PlanJson.planFromJson("not json"))
    }
}
