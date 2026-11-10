package com.varunkb.learnmateai.domain

/** Pure Kotlin domain models. No Android imports so they can be unit-tested on the JVM. */

data class Subject(
    val id: String,
    val title: String,
    val description: String,
    val level: String = "Beginner",
    val path: String = "Programming Foundations",
    val pathOrder: Int = 0,
    val prerequisites: List<String> = emptyList(),
    val icon: String = "📘"
)

data class Question(
    val id: String,
    val subjectId: String,
    val topic: String,
    val difficulty: Int,
    val prompt: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explanation: String
)

data class Definition(val term: String, val meaning: String)
data class PracticeItem(val question: String, val answer: String)
data class HdlFile(val name: String, val isTestbench: Boolean, val text: String)
data class LessonHdl(val title: String, val files: List<HdlFile>)

data class Lesson(
    val id: String,
    val subjectId: String,
    val topic: String,
    val title: String,
    val keywords: List<String>,
    val body: String,
    val example: String?,
    val level: String = "Beginner",
    val minutes: Int = 5,
    val objectives: List<String> = emptyList(),
    val definitions: List<Definition> = emptyList(),
    val exampleNote: String? = null,
    val diagram: String? = null,
    val mistakes: List<String> = emptyList(),
    val practice: List<PracticeItem> = emptyList(),
    val exercises: List<String> = emptyList(),
    val summary: List<String> = emptyList(),
    val hdl: LessonHdl? = null
)

data class ContentPack(val subject: Subject, val questions: List<Question>, val lessons: List<Lesson>)

/** Estimated mastery of one topic. [pKnown] is the Bayesian Knowledge Tracing probability. */
data class TopicMastery(
    val subjectId: String,
    val topic: String,
    val pKnown: Double,
    val correct: Int,
    val total: Int
) {
    val accuracyPercent: Int get() = if (total == 0) 0 else correct * 100 / total
    val masteryPercent: Int get() = (pKnown * 100).toInt().coerceIn(0, 100)
}

/** A question as shown to the learner: choices are shuffled, [correctIndex] points into [choices]. */
data class QuizItem(val question: Question, val choices: List<String>, val correctIndex: Int)

data class QuizOutcome(
    val correct: Int,
    val total: Int,
    val topicScores: Map<String, Pair<Int, Int>>,
    val updatedMastery: List<TopicMastery>
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}
