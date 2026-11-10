package com.varunkb.learnmateai.domain

enum class LessonStatus { NotStarted, InProgress, Completed }

data class LessonProgress(
    val lessonId: String,
    val subjectId: String,
    val status: LessonStatus,
    val bookmarked: Boolean,
    val lastAccessed: Long,
    val completedAt: Long
)

data class LearningPath(val name: String, val subjects: List<Subject>)

data class SubjectProgress(val total: Int, val completed: Int, val inProgress: Int) {
    val percent: Int get() = if (total == 0) 0 else completed * 100 / total
}

/** In-memory index over all content packs: subjects in learning-path order, lessons in reading order. */
class Catalog(packs: List<ContentPack>) {
    private val pathOrder = listOf("Programming Foundations", "Computer Systems", "Digital Design and VLSI", "AI and Machine Learning")

    val subjects: List<Subject> = packs.map { it.subject }.sortedWith(
        compareBy<Subject>({ pathOrder.indexOf(it.path).let { i -> if (i < 0) 99 else i } }, { it.pathOrder }, { it.title })
    )
    private val bySubject: Map<String, List<Lesson>> = packs.associate { it.subject.id to it.lessons }
    private val byId: Map<String, Lesson> = packs.flatMap { it.lessons }.associateBy { it.id }
    private val questionsBySubject: Map<String, List<Question>> = packs.associate { it.subject.id to it.questions }

    val paths: List<LearningPath> = subjects.groupBy { it.path }.map { (n, s) -> LearningPath(n, s) }
        .sortedBy { pathOrder.indexOf(it.name).let { i -> if (i < 0) 99 else i } }

    val allLessons: List<Lesson> get() = subjects.flatMap { lessons(it.id) }
    fun subject(id: String): Subject? = subjects.firstOrNull { it.id == id }
    fun lessons(subjectId: String): List<Lesson> = bySubject[subjectId].orEmpty()
    fun lesson(id: String): Lesson? = byId[id]
    fun topics(subjectId: String): List<String> = questionsBySubject[subjectId].orEmpty().map { it.topic }.distinct()
    fun questionCount(subjectId: String) = questionsBySubject[subjectId].orEmpty().size

    /** Previous and next lesson in the same subject. */
    fun neighbours(lessonId: String): Pair<Lesson?, Lesson?> {
        val l = byId[lessonId] ?: return null to null
        val list = lessons(l.subjectId)
        val i = list.indexOfFirst { it.id == lessonId }
        return list.getOrNull(i - 1) to list.getOrNull(i + 1)
    }

    fun lessonsForTopic(subjectId: String, topic: String): List<Lesson> = lessons(subjectId).filter { it.topic == topic }
}

enum class RecommendationKind { Revise, ContinueLearning, FirstLesson, TakeQuiz, NextSubject, AllDone }

data class StudyRecommendation(val kind: RecommendationKind, val title: String, val detail: String, val lesson: Lesson?)

object Curriculum {
    fun progressFor(lessons: List<Lesson>, progress: Map<String, LessonProgress>): SubjectProgress {
        val done = lessons.count { progress[it.id]?.status == LessonStatus.Completed }
        val started = lessons.count { progress[it.id]?.status == LessonStatus.InProgress }
        return SubjectProgress(lessons.size, done, started)
    }

    /** First unfinished lesson at or after the last-opened one, otherwise the first unfinished lesson. */
    fun nextLesson(lessons: List<Lesson>, progress: Map<String, LessonProgress>, lastLessonId: String? = null): Lesson? {
        fun open(l: Lesson) = progress[l.id]?.status != LessonStatus.Completed
        val start = lessons.indexOfFirst { it.id == lastLessonId }
        if (start >= 0) lessons.drop(start).firstOrNull { open(it) }?.let { return it }
        return lessons.firstOrNull { open(it) }
    }

    /** Subjects whose recommended prerequisites are not yet at least half complete. */
    fun missingPrerequisites(subject: Subject, catalog: Catalog, progress: Map<String, LessonProgress>): List<Subject> =
        subject.prerequisites.mapNotNull { catalog.subject(it) }.filter { pre ->
            progressFor(catalog.lessons(pre.id), progress).percent < 50
        }

    /**
     * Combines lesson progress with BKT mastery:
     * 1. a weak topic (mastery < 60% after >= 3 answers) -> revise its lesson,
     * 2. else the next unfinished lesson of the subject,
     * 3. else suggest a quiz or the next subject in the path.
     */
    fun recommend(
        catalog: Catalog,
        subjectId: String,
        mastery: List<TopicMastery>,
        progress: Map<String, LessonProgress>,
        lastLessonId: String?
    ): StudyRecommendation {
        val subject = catalog.subject(subjectId)
        val lessons = catalog.lessons(subjectId)
        val weak = mastery.filter { it.subjectId == subjectId && it.total >= 3 && it.pKnown < 0.6 }.minByOrNull { it.pKnown }
        if (weak != null) {
            val l = catalog.lessonsForTopic(subjectId, weak.topic).let { ls ->
                ls.firstOrNull { progress[it.id]?.status != LessonStatus.Completed } ?: ls.firstOrNull()
            }
            if (l != null) {
                return StudyRecommendation(
                    RecommendationKind.Revise, "Revise: ${l.title}",
                    "Your estimated mastery of ${weak.topic} is ${weak.masteryPercent}% (${weak.correct}/${weak.total} correct). Re-read this lesson, then retry a quiz.", l
                )
            }
        }
        val next = nextLesson(lessons, progress, lastLessonId)
        if (next != null) {
            val anyStarted = lessons.any { progress[it.id] != null }
            return if (!anyStarted) {
                StudyRecommendation(RecommendationKind.FirstLesson, "Start with: ${next.title}", "The first lesson of ${subject?.title ?: "this subject"}, about ${next.minutes} min.", next)
            } else {
                StudyRecommendation(RecommendationKind.ContinueLearning, "Continue: ${next.title}", "Next unfinished lesson in ${subject?.title ?: "this subject"}, about ${next.minutes} min.", next)
            }
        }
        if (lessons.isNotEmpty()) {
            val nextSubject = subject?.let { s ->
                catalog.subjects.filter { it.path == s.path && it.pathOrder > s.pathOrder }
                    .firstOrNull { progressFor(catalog.lessons(it.id), progress).percent < 100 }
            }
            if (nextSubject != null) {
                return StudyRecommendation(RecommendationKind.NextSubject, "Next in your path: ${nextSubject.title}", "You finished every lesson in ${subject?.title}. Continue along the ${nextSubject.path} path.", catalog.lessons(nextSubject.id).firstOrNull())
            }
            return StudyRecommendation(RecommendationKind.AllDone, "All lessons complete", "Take a quiz to check what you remember, or explore another subject.", null)
        }
        return StudyRecommendation(RecommendationKind.TakeQuiz, "Take a quiz", "Practise this subject with an adaptive quiz.", null)
    }
}
