package com.varunkb.learnmateai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
abstract class ContentDao {
    @Query("SELECT * FROM subjects ORDER BY title")
    abstract fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY title")
    abstract suspend fun getSubjects(): List<SubjectEntity>

    @Query("SELECT subjectId AS subjectId, COUNT(*) AS count FROM questions GROUP BY subjectId")
    abstract fun observeQuestionCounts(): Flow<List<SubjectCount>>

    @Query("SELECT * FROM questions WHERE subjectId = :subjectId ORDER BY id")
    abstract suspend fun questionsFor(subjectId: String): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions")
    abstract suspend fun questionCount(): Int

    @Upsert
    abstract suspend fun upsertSubjects(items: List<SubjectEntity>)

    @Upsert
    abstract suspend fun upsertQuestions(items: List<QuestionEntity>)

    @Query("DELETE FROM questions WHERE subjectId = :subjectId AND id NOT IN (:keepIds)")
    abstract suspend fun deleteQuestionsNotIn(subjectId: String, keepIds: List<String>)

    /** Replaces a subject's question bank atomically; attempts and mastery are untouched. */
    @Transaction
    open suspend fun replaceSubject(subject: SubjectEntity, questions: List<QuestionEntity>) {
        upsertSubjects(listOf(subject))
        upsertQuestions(questions)
        deleteQuestionsNotIn(subject.id, questions.map { it.id })
    }
}

@Dao
abstract class LearningDao {
    @Insert
    abstract suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Insert
    abstract suspend fun insertAnswers(answers: List<AttemptAnswerEntity>)

    @Upsert
    abstract suspend fun upsertMastery(items: List<MasteryEntity>)

    /** Saves a finished quiz and the resulting mastery update in a single transaction. */
    @Transaction
    open suspend fun commitQuiz(
        attempt: AttemptEntity,
        answers: List<AttemptAnswerEntity>,
        mastery: List<MasteryEntity>
    ): Long {
        val id = insertAttempt(attempt)
        insertAnswers(answers.map { it.copy(attemptId = id) })
        upsertMastery(mastery)
        return id
    }

    @Query("SELECT * FROM attempts ORDER BY finishedAt DESC")
    abstract fun observeAttempts(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM mastery")
    abstract fun observeMastery(): Flow<List<MasteryEntity>>

    @Query("SELECT * FROM mastery WHERE subjectId = :subjectId")
    abstract suspend fun masteryFor(subjectId: String): List<MasteryEntity>

    @Query(
        "SELECT questionId FROM attempt_answers WHERE attemptId IN " +
            "(SELECT id FROM attempts WHERE subjectId = :subjectId ORDER BY finishedAt DESC LIMIT :attempts)"
    )
    abstract suspend fun recentQuestionIds(subjectId: String, attempts: Int): List<String>

    @Query("SELECT * FROM attempt_answers WHERE attemptId = :attemptId")
    abstract suspend fun answersFor(attemptId: Long): List<AttemptAnswerEntity>

    @Query("SELECT COUNT(*) FROM attempts")
    abstract suspend fun attemptCount(): Int

    @Query("DELETE FROM attempts WHERE subjectId = :subjectId")
    abstract suspend fun deleteAttemptsFor(subjectId: String)

    @Query("DELETE FROM mastery WHERE subjectId = :subjectId")
    abstract suspend fun deleteMasteryFor(subjectId: String)

    @Transaction
    open suspend fun clearSubjectProgress(subjectId: String) {
        deleteAttemptsFor(subjectId)
        deleteMasteryFor(subjectId)
    }
}

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    abstract fun observe(): Flow<List<ChatMessageEntity>>

    @Insert
    abstract suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    abstract suspend fun clear()

    @Query("DELETE FROM chat_messages WHERE id NOT IN (SELECT id FROM chat_messages ORDER BY id DESC LIMIT :keep)")
    abstract suspend fun trim(keep: Int)

    @Transaction
    open suspend fun addPair(user: ChatMessageEntity, tutor: ChatMessageEntity, keep: Int) {
        insert(user)
        insert(tutor)
        trim(keep)
    }
}

@Dao
interface BenchmarkDao {
    @Insert
    suspend fun insert(run: BenchmarkRunEntity): Long

    @Query("SELECT * FROM benchmark_runs ORDER BY createdAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<BenchmarkRunEntity>>
}

@Dao
abstract class LessonDao {
    @Query("SELECT * FROM lesson_progress")
    abstract fun observeAll(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :id")
    abstract suspend fun get(id: String): LessonProgressEntity?

    @Upsert
    abstract suspend fun upsert(item: LessonProgressEntity)

    /** Opening a lesson marks it in progress (never downgrades a completed one) and stamps last-accessed. */
    @Transaction
    open suspend fun open(lessonId: String, subjectId: String, now: Long) {
        val cur = get(lessonId)
        upsert(
            if (cur == null) LessonProgressEntity(lessonId, subjectId, "in_progress", false, now, 0L)
            else cur.copy(status = if (cur.status == "completed") "completed" else "in_progress", lastAccessed = now)
        )
    }

    @Transaction
    open suspend fun setCompleted(lessonId: String, subjectId: String, completed: Boolean, now: Long) {
        val cur = get(lessonId) ?: LessonProgressEntity(lessonId, subjectId, "not_started", false, now, 0L)
        upsert(cur.copy(status = if (completed) "completed" else "in_progress", completedAt = if (completed) now else 0L, lastAccessed = now))
    }

    @Transaction
    open suspend fun setBookmarked(lessonId: String, subjectId: String, bookmarked: Boolean, now: Long) {
        val cur = get(lessonId) ?: LessonProgressEntity(lessonId, subjectId, "not_started", false, now, 0L)
        upsert(cur.copy(bookmarked = bookmarked))
    }
}

@Dao
abstract class HdlDao {
    @Query("SELECT * FROM hdl_projects ORDER BY updatedAt DESC")
    abstract fun observeProjects(): Flow<List<HdlProjectEntity>>

    @Query("SELECT * FROM hdl_files WHERE projectId = :projectId ORDER BY id")
    abstract suspend fun filesFor(projectId: Long): List<HdlFileEntity>

    @Query("SELECT * FROM hdl_projects WHERE lessonId = :lessonId ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun projectForLesson(lessonId: String): HdlProjectEntity?

    @Query("SELECT * FROM hdl_projects WHERE id = :id")
    abstract suspend fun project(id: Long): HdlProjectEntity?

    @Insert
    abstract suspend fun insertProject(p: HdlProjectEntity): Long

    @Insert
    abstract suspend fun insertFile(f: HdlFileEntity): Long

    @Upsert
    abstract suspend fun upsertFile(f: HdlFileEntity)

    @Query("UPDATE hdl_projects SET name = :name, updatedAt = :now WHERE id = :id")
    abstract suspend fun renameProject(id: Long, name: String, now: Long)

    @Query("UPDATE hdl_projects SET updatedAt = :now WHERE id = :id")
    abstract suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM hdl_projects WHERE id = :id")
    abstract suspend fun deleteProject(id: Long)

    @Query("DELETE FROM hdl_files WHERE id = :id")
    abstract suspend fun deleteFile(id: Long)

    @Query("UPDATE hdl_files SET name = :name WHERE id = :id")
    abstract suspend fun renameFile(id: Long, name: String)

    @Transaction
    open suspend fun createProject(p: HdlProjectEntity, files: List<HdlFileEntity>): Long {
        val id = insertProject(p)
        files.forEach { insertFile(it.copy(id = 0, projectId = id)) }
        return id
    }

    @Transaction
    open suspend fun saveFiles(projectId: Long, files: List<HdlFileEntity>, now: Long) {
        files.forEach { upsertFile(it.copy(projectId = projectId)) }
        touch(projectId, now)
    }
}
