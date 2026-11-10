package com.varunkb.learnmateai.data

import com.varunkb.learnmateai.domain.LessonProgress
import com.varunkb.learnmateai.domain.Question
import com.varunkb.learnmateai.domain.QuizItem
import com.varunkb.learnmateai.domain.QuizOutcome
import com.varunkb.learnmateai.domain.Subject
import com.varunkb.learnmateai.domain.TopicMastery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single access point for all persisted learning data. Everything is local (Room/SQLite). */
class LearnRepository(private val db: LearnMateDatabase) {
    private val profileDao = db.profileDao()
    private val contentDao = db.contentDao()
    private val learningDao = db.learningDao()
    private val chatDao = db.chatDao()
    private val benchmarkDao = db.benchmarkDao()
    private val lessonDao = db.lessonDao()
    private val hdlDao = db.hdlDao()

    val profile: Flow<ProfileEntity?> = profileDao.observe()
    val subjects: Flow<List<Subject>> = contentDao.observeSubjects().map { list -> list.map { it.toDomain() } }
    val questionCounts: Flow<Map<String, Int>> =
        contentDao.observeQuestionCounts().map { rows -> rows.associate { it.subjectId to it.count } }
    val attempts: Flow<List<AttemptEntity>> = learningDao.observeAttempts()
    val mastery: Flow<List<TopicMastery>> = learningDao.observeMastery().map { rows -> rows.map { it.toDomain() } }
    val chat: Flow<List<ChatMessageEntity>> = chatDao.observe()
    val benchmarks: Flow<List<BenchmarkRunEntity>> = benchmarkDao.observeRecent()

    val lessonProgress: Flow<Map<String, LessonProgress>> =
        lessonDao.observeAll().map { rows -> rows.map { it.toDomain() }.associateBy { it.lessonId } }
    val hdlProjects: Flow<List<HdlProjectEntity>> = hdlDao.observeProjects()

    suspend fun openLesson(lessonId: String, subjectId: String) =
        lessonDao.open(lessonId, subjectId, System.currentTimeMillis())
    suspend fun setLessonCompleted(lessonId: String, subjectId: String, completed: Boolean) =
        lessonDao.setCompleted(lessonId, subjectId, completed, System.currentTimeMillis())
    suspend fun setLessonBookmarked(lessonId: String, subjectId: String, bookmarked: Boolean) =
        lessonDao.setBookmarked(lessonId, subjectId, bookmarked, System.currentTimeMillis())

    // ---- HDL Studio projects ----
    suspend fun hdlFiles(projectId: Long): List<HdlFileEntity> = hdlDao.filesFor(projectId)
    suspend fun hdlProjectForLesson(lessonId: String): HdlProjectEntity? = hdlDao.projectForLesson(lessonId)
    suspend fun hdlProject(id: Long): HdlProjectEntity? = hdlDao.project(id)
    suspend fun createHdlProject(name: String, lessonId: String?, files: List<HdlFileEntity>): Long {
        val now = System.currentTimeMillis()
        return hdlDao.createProject(HdlProjectEntity(name = name, lessonId = lessonId, createdAt = now, updatedAt = now), files)
    }
    suspend fun saveHdlFiles(projectId: Long, files: List<HdlFileEntity>) =
        hdlDao.saveFiles(projectId, files, System.currentTimeMillis())
    suspend fun addHdlFile(projectId: Long, name: String, text: String, tb: Boolean): Long {
        val id = hdlDao.insertFile(HdlFileEntity(projectId = projectId, name = name, text = text, isTestbench = tb))
        hdlDao.touch(projectId, System.currentTimeMillis())
        return id
    }
    suspend fun renameHdlProject(id: Long, name: String) = hdlDao.renameProject(id, name, System.currentTimeMillis())
    suspend fun renameHdlFile(id: Long, name: String) = hdlDao.renameFile(id, name)
    suspend fun deleteHdlFile(id: Long) = hdlDao.deleteFile(id)
    suspend fun deleteHdlProject(id: Long) = hdlDao.deleteProject(id)

    suspend fun getProfile(): ProfileEntity? = profileDao.get()
    suspend fun saveProfile(p: ProfileEntity) = profileDao.upsert(p)

    suspend fun questions(subjectId: String): List<Question> =
        contentDao.questionsFor(subjectId).map { it.toDomain() }

    suspend fun masteryMap(subjectId: String): Map<String, TopicMastery> =
        learningDao.masteryFor(subjectId).map { it.toDomain() }.associateBy { it.topic }

    suspend fun recentQuestionIds(subjectId: String, lastAttempts: Int = 3): Set<String> =
        learningDao.recentQuestionIds(subjectId, lastAttempts).toSet()

    suspend fun commitQuiz(
        subjectId: String,
        startedAt: Long,
        items: List<QuizItem>,
        chosen: List<Int>,
        timesMs: List<Long>,
        outcome: QuizOutcome,
        now: Long = System.currentTimeMillis()
    ): Long {
        val attempt = AttemptEntity(
            subjectId = subjectId, startedAt = startedAt, finishedAt = now,
            correct = outcome.correct, total = outcome.total
        )
        val answers = items.mapIndexed { i, item ->
            AttemptAnswerEntity(
                attemptId = 0,
                questionId = item.question.id,
                topic = item.question.topic,
                chosenIndex = chosen[i],
                isCorrect = chosen[i] == item.correctIndex,
                timeMs = timesMs.getOrElse(i) { 0L }
            )
        }
        return learningDao.commitQuiz(attempt, answers, outcome.updatedMastery.map { it.toEntity(now) })
    }

    suspend fun clearProgress(subjectId: String) = learningDao.clearSubjectProgress(subjectId)

    suspend fun addChat(user: ChatMessageEntity, tutor: ChatMessageEntity) = chatDao.addPair(user, tutor, keep = 200)
    suspend fun clearChat() = chatDao.clear()

    suspend fun saveBenchmark(run: BenchmarkRunEntity) = benchmarkDao.insert(run)

    // Used by seeding, the legacy importer and tests.
    suspend fun replaceSubject(subject: Subject, questions: List<Question>) =
        contentDao.replaceSubject(subject.toEntity(), questions.map { it.toEntity() })

    suspend fun questionCount(): Int = contentDao.questionCount()
    suspend fun subjectList(): List<Subject> = contentDao.getSubjects().map { it.toDomain() }
    suspend fun attemptCount(): Int = learningDao.attemptCount()

    suspend fun importLegacy(attempts: List<AttemptEntity>, mastery: List<MasteryEntity>) {
        attempts.forEach { learningDao.insertAttempt(it) }
        learningDao.upsertMastery(mastery)
    }
}
