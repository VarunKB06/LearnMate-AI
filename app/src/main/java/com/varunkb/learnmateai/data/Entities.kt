package com.varunkb.learnmateai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val education: String,
    val goal: String,
    val activeSubjectId: String
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String
)

@Entity(
    tableName = "questions",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subjectId")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val topic: String,
    val difficulty: Int,
    val prompt: String,
    /** Four choices joined with the ASCII unit separator (U+001F). */
    val choices: String,
    val answerIndex: Int,
    val explanation: String
)

@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subjectId")]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val correct: Int,
    val total: Int,
    /** "quiz" for attempts taken in this version, "legacy" for ones imported from SharedPreferences. */
    val source: String = "quiz"
)

@Entity(
    tableName = "attempt_answers",
    foreignKeys = [ForeignKey(
        entity = AttemptEntity::class,
        parentColumns = ["id"],
        childColumns = ["attemptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("attemptId"), Index("questionId")]
)
data class AttemptAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: Long,
    val questionId: String,
    val topic: String,
    val chosenIndex: Int,
    val isCorrect: Boolean,
    val timeMs: Long
)

@Entity(tableName = "mastery", primaryKeys = ["subjectId", "topic"])
data class MasteryEntity(
    val subjectId: String,
    val topic: String,
    val pKnown: Double,
    val correct: Int,
    val total: Int,
    val updatedAt: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" or "tutor". */
    val role: String,
    val text: String,
    val sourceTitle: String? = null,
    val confidence: Int? = null,
    val createdAt: Long,
    /** Lesson the answer came from, so the chat can open it. Added in schema v2. */
    val sourceLessonId: String? = null,
    /** External reference link (online research answers). Added in schema v2. */
    val sourceUrl: String? = null
)

@Entity(tableName = "lesson_progress", indices = [Index("subjectId")])
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val subjectId: String,
    /** "not_started", "in_progress" or "completed". */
    val status: String,
    val bookmarked: Boolean,
    val lastAccessed: Long,
    val completedAt: Long
)

@Entity(tableName = "hdl_projects")
data class HdlProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Lesson this project was opened from, if any. */
    val lessonId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "hdl_files",
    foreignKeys = [ForeignKey(
        entity = HdlProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class HdlFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val text: String,
    val isTestbench: Boolean
)

@Entity(tableName = "benchmark_runs")
data class BenchmarkRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val name: String,
    val report: String
)

/** Result row of the per-subject question count query. */
data class SubjectCount(val subjectId: String, val count: Int)
