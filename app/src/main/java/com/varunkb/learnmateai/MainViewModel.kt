package com.varunkb.learnmateai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.varunkb.learnmateai.data.AttemptEntity
import com.varunkb.learnmateai.data.BenchmarkRunEntity
import com.varunkb.learnmateai.data.ChatMessageEntity
import com.varunkb.learnmateai.data.ProfileEntity
import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.Curriculum
import com.varunkb.learnmateai.domain.LearnerContext
import com.varunkb.learnmateai.domain.Lesson
import com.varunkb.learnmateai.domain.LessonProgress
import com.varunkb.learnmateai.domain.StudyRecommendation
import com.varunkb.learnmateai.domain.QuizItem
import com.varunkb.learnmateai.domain.QuizOutcome
import com.varunkb.learnmateai.domain.QuizScoring
import com.varunkb.learnmateai.domain.QuizSelector
import com.varunkb.learnmateai.domain.Recommendation
import com.varunkb.learnmateai.domain.Recommender
import com.varunkb.learnmateai.domain.Subject
import com.varunkb.learnmateai.domain.TopicMastery
import com.varunkb.learnmateai.perf.BenchmarkRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Welcome, Profile, Dashboard, Subjects, Quiz, Result, Assistant, Progress, Diagnostics, About, Learn, SubjectDetail, Lesson, Studio }

data class SubjectInfo(val subject: Subject, val questionCount: Int, val attempts: Int, val masteryPercent: Int?)

data class HomeState(
    val profile: ProfileEntity? = null,
    val subjects: List<SubjectInfo> = emptyList(),
    val active: Subject? = null,
    val attempts: List<AttemptEntity> = emptyList(),
    val mastery: List<TopicMastery> = emptyList(),
    val accuracy: Int = 0,
    val recommendation: Recommendation = Recommendation("", "", null)
)

data class QuizState(
    val subject: Subject,
    val items: List<QuizItem>,
    val index: Int,
    val chosen: List<Int>,
    val times: List<Long>,
    val startedAt: Long,
    val questionStartedAt: Long
) {
    val current: QuizItem get() = items[index]
    val selected: Int get() = chosen[index]
    val isLast: Boolean get() = index == items.lastIndex
}

data class ReviewItem(val prompt: String, val yourAnswer: String, val correctAnswer: String, val explanation: String)

data class ResultState(val subjectTitle: String, val outcome: QuizOutcome, val review: List<ReviewItem>)

data class BenchState(val running: Boolean = false, val status: String = "", val report: String = "")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as LearnMateApp).container
    private val repo = container.repository

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _screen = MutableStateFlow(Screen.Welcome)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _quiz = MutableStateFlow<QuizState?>(null)
    val quiz: StateFlow<QuizState?> = _quiz.asStateFlow()

    private val _result = MutableStateFlow<ResultState?>(null)
    val result: StateFlow<ResultState?> = _result.asStateFlow()

    private val _bench = MutableStateFlow(BenchState())
    val bench: StateFlow<BenchState> = _bench.asStateFlow()

    val hdl = HdlController(repo, viewModelScope)

    val catalog: Catalog get() = container.catalog

    val lessonProgress: StateFlow<Map<String, LessonProgress>> =
        repo.lessonProgress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _subjectId = MutableStateFlow<String?>(null)
    val openSubjectId: StateFlow<String?> = _subjectId.asStateFlow()
    private val _lessonId = MutableStateFlow<String?>(null)
    val openLessonId: StateFlow<String?> = _lessonId.asStateFlow()

    val chat: StateFlow<List<ChatMessageEntity>> =
        repo.chat.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val benchmarkHistory: StateFlow<List<BenchmarkRunEntity>> =
        repo.benchmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val home: StateFlow<HomeState> = combine(
        repo.profile, repo.subjects, repo.questionCounts, repo.attempts, repo.mastery
    ) { profile, subjects, counts, attempts, mastery ->
        val activeId = profile?.activeSubjectId ?: subjects.firstOrNull()?.id
        val active = subjects.firstOrNull { it.id == activeId } ?: subjects.firstOrNull()
        val infos = subjects.map { s ->
            val m = mastery.filter { it.subjectId == s.id && it.total > 0 }
            SubjectInfo(
                subject = s,
                questionCount = counts[s.id] ?: 0,
                attempts = attempts.count { it.subjectId == s.id },
                masteryPercent = if (m.isEmpty()) null else (m.map { it.pKnown }.average() * 100).toInt()
            )
        }
        val subjectAttempts = attempts.filter { it.subjectId == active?.id }
        val subjectMastery = mastery.filter { it.subjectId == active?.id }
        HomeState(
            profile = profile,
            subjects = infos,
            active = active,
            attempts = subjectAttempts,
            mastery = subjectMastery.sortedBy { it.topic },
            accuracy = Recommender.accuracy(subjectMastery),
            recommendation = Recommender.recommend(active?.title ?: "your subject", subjectMastery, subjectAttempts.size)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    init {
        viewModelScope.launch {
            try {
                container.initialize()
                _screen.value = if (repo.getProfile() != null) Screen.Dashboard else Screen.Welcome
                _ready.value = true
            } catch (e: Exception) {
                _error.value = "Could not open the local database: ${e.message}"
            }
        }
    }

    // ---- navigation ----
    fun go(screen: Screen) { _screen.value = screen }

    fun back() {
        when (_screen.value) {
            Screen.Profile -> _screen.value = if (home.value.profile == null) Screen.Welcome else Screen.Dashboard
            Screen.Quiz -> exitQuiz()
            Screen.Welcome, Screen.Dashboard -> Unit
            Screen.Lesson -> _screen.value = Screen.SubjectDetail
            Screen.Studio -> if (hdl.state.value.projectId != null) hdl.closeProject() else _screen.value = Screen.Dashboard
            Screen.SubjectDetail -> _screen.value = Screen.Learn
            else -> _screen.value = Screen.Dashboard
        }
    }

    // ---- lessons ----
    fun openSubject(subjectId: String) {
        _subjectId.value = subjectId
        _screen.value = Screen.SubjectDetail
    }

    fun openLesson(lessonId: String) {
        val lesson = catalog.lesson(lessonId) ?: return
        _subjectId.value = lesson.subjectId
        _lessonId.value = lessonId
        _screen.value = Screen.Lesson
        viewModelScope.launch { repo.openLesson(lessonId, lesson.subjectId) }
    }

    fun setLessonCompleted(lesson: Lesson, completed: Boolean) {
        viewModelScope.launch { repo.setLessonCompleted(lesson.id, lesson.subjectId, completed) }
    }

    fun toggleBookmark(lesson: Lesson) {
        val now = lessonProgress.value[lesson.id]?.bookmarked ?: false
        viewModelScope.launch { repo.setLessonBookmarked(lesson.id, lesson.subjectId, !now) }
    }

    /** Most recently opened lesson across all subjects, or null if none opened yet. */
    fun lastLessonId(): String? = lessonProgress.value.values.filter { it.lastAccessed > 0 }.maxByOrNull { it.lastAccessed }?.lessonId

    fun recommendationFor(subjectId: String): StudyRecommendation {
        val all = home.value.mastery
        return Curriculum.recommend(catalog, subjectId, all, lessonProgress.value, lastLessonId())
    }

    /** Make [subjectId] the active subject (used for quizzes and the tutor), then run [then]. */
    fun activateSubject(subjectId: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val p = repo.getProfile()
            if (p != null && p.activeSubjectId != subjectId) repo.saveProfile(p.copy(activeSubjectId = subjectId))
            then()
        }
    }

    fun openLessonInStudio(lesson: Lesson) {
        hdl.openFromLesson(lesson) { _screen.value = Screen.Studio }
    }

    fun quizSubject(subjectId: String) = activateSubject(subjectId) { startQuiz() }

    fun askAboutLesson(lesson: Lesson) {
        _screen.value = Screen.Assistant
        activateSubject(lesson.subjectId) { ask("Explain ${lesson.title}", lesson.subjectId) }
    }

    // ---- profile & subjects ----
    fun saveProfile(name: String, education: String, goal: String, subjectId: String) {
        viewModelScope.launch {
            repo.saveProfile(
                ProfileEntity(
                    name = name.trim(),
                    education = education.trim().ifBlank { "Learner" },
                    goal = goal.trim().ifBlank { "Learning" },
                    activeSubjectId = subjectId
                )
            )
            _screen.value = Screen.Dashboard
        }
    }

    fun selectSubject(subjectId: String) {
        viewModelScope.launch {
            val p = repo.getProfile()
            if (p != null) repo.saveProfile(p.copy(activeSubjectId = subjectId))
            _screen.value = Screen.Dashboard
        }
    }

    fun clearProgress() {
        val id = home.value.active?.id ?: return
        viewModelScope.launch { repo.clearProgress(id) }
    }

    // ---- quiz ----
    fun startQuiz() {
        val subject = home.value.active ?: return
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) {
                val questions = repo.questions(subject.id)
                QuizSelector.select(questions, repo.masteryMap(subject.id), repo.recentQuestionIds(subject.id), QuizSelector.DEFAULT_SIZE)
            }
            if (items.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            _quiz.value = QuizState(
                subject = subject, items = items, index = 0,
                chosen = List(items.size) { -1 }, times = List(items.size) { 0L },
                startedAt = now, questionStartedAt = now
            )
            _result.value = null
            _screen.value = Screen.Quiz
        }
    }

    fun selectAnswer(choice: Int) {
        _quiz.update { q ->
            q?.copy(chosen = q.chosen.toMutableList().also { it[q.index] = choice })
        }
    }

    fun nextQuestion() {
        val q = _quiz.value ?: return
        if (q.selected < 0) return
        val now = System.currentTimeMillis()
        val times = q.times.toMutableList().also { it[q.index] = now - q.questionStartedAt }
        if (q.isLast) {
            _quiz.value = q.copy(times = times)
            finishQuiz()
        } else {
            _quiz.value = q.copy(index = q.index + 1, times = times, questionStartedAt = now)
        }
    }

    fun exitQuiz() {
        _quiz.value = null
        _screen.value = Screen.Dashboard
    }

    private fun finishQuiz() {
        val q = _quiz.value ?: return
        viewModelScope.launch {
            val prior = repo.masteryMap(q.subject.id)
            val outcome = QuizScoring.score(q.subject.id, q.items, q.chosen, prior)
            repo.commitQuiz(q.subject.id, q.startedAt, q.items, q.chosen, q.times, outcome)
            val review = q.items.mapIndexedNotNull { i, item ->
                if (q.chosen[i] == item.correctIndex) null
                else ReviewItem(
                    prompt = item.question.prompt,
                    yourAnswer = item.choices.getOrNull(q.chosen[i]) ?: "No answer",
                    correctAnswer = item.choices[item.correctIndex],
                    explanation = item.question.explanation
                )
            }
            _result.value = ResultState(q.subject.title, outcome, review)
            _quiz.value = null
            _screen.value = Screen.Result
        }
    }

    // ---- assistant ----
    fun ask(text: String, subjectId: String? = null) {
        val h = home.value
        val subject = (subjectId?.let { id -> h.subjects.firstOrNull { it.subject.id == id }?.subject } ?: h.active) ?: return
        if (text.isBlank() || !_ready.value) return
        viewModelScope.launch {
            val mastery = repo.masteryMap(subject.id).values.toList()
            val attempts = h.attempts.count { it.subjectId == subject.id }
            val lastLesson = repo.chat.first().lastOrNull { it.role == "tutor" }?.sourceLessonId
            val reply = withContext(Dispatchers.Default) {
                container.tutor.answer(
                    text,
                    LearnerContext(h.profile?.name.orEmpty(), subject.id, subject.title, attempts, com.varunkb.learnmateai.domain.Recommender.accuracy(mastery), mastery, lastLesson)
                )
            }
            val now = System.currentTimeMillis()
            repo.addChat(
                ChatMessageEntity(role = "user", text = text.trim(), createdAt = now),
                ChatMessageEntity(role = "tutor", text = reply.text, sourceTitle = reply.sourceTitle, confidence = reply.confidence, createdAt = now + 1, sourceLessonId = reply.lessonId)
            )
        }
    }

    fun clearChat() { viewModelScope.launch { repo.clearChat() } }

    // ---- diagnostics ----
    fun runQuickBenchmark() = runBenchmark("Quick benchmark") { r, status -> r.runQuick(status) }

    fun runSustainedBenchmark(seconds: Int) =
        runBenchmark("Sustained ${seconds}s") { r, status -> r.runSustained(seconds, status) }

    private fun runBenchmark(name: String, block: suspend (BenchmarkRunner, (String) -> Unit) -> String) {
        if (_bench.value.running || !_ready.value) return
        _bench.value = BenchState(running = true, status = "Starting…", report = "")
        viewModelScope.launch {
            try {
                val runner = BenchmarkRunner(getApplication<Application>(), container)
                val report = block(runner) { s -> _bench.update { it.copy(status = s) } }
                repo.saveBenchmark(BenchmarkRunEntity(createdAt = System.currentTimeMillis(), name = name, report = report))
                _bench.value = BenchState(running = false, status = "Finished", report = report)
            } catch (e: Exception) {
                _bench.value = BenchState(running = false, status = "Failed", report = "Benchmark failed: ${e.message}")
            }
        }
    }

    fun startupSummary(): String = container.metrics.let {
        "Start-up: parse content ${it.parseContentMs} ms, seed DB ${it.seedDatabaseMs} ms, legacy import ${it.legacyImportMs} ms, " +
            "build tutor index ${it.buildIndexMs} ms (total ${it.totalMs} ms). Seeded this launch: ${it.seeded}. Legacy data imported: ${it.legacyImported}."
    }
}
