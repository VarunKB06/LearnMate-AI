package com.varunkb.learnmateai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.varunkb.learnmateai.MainViewModel
import com.varunkb.learnmateai.Screen

@Composable
fun LearnMateRoot(vm: MainViewModel) {
    val ready by vm.ready.collectAsState()
    val error by vm.error.collectAsState()
    val screen by vm.screen.collectAsState()
    val home by vm.home.collectAsState()
    val quiz by vm.quiz.collectAsState()
    val result by vm.result.collectAsState()
    val chat by vm.chat.collectAsState()
    val bench by vm.bench.collectAsState()
    val benchHistory by vm.benchmarkHistory.collectAsState()
    val goal by vm.goal.collectAsState()
    val plan by vm.plan.collectAsState()
    val busy by vm.busy.collectAsState()
    val llm by vm.llmState.collectAsState()
    val generating by vm.generating.collectAsState()
    val useLlm by vm.useLlm.collectAsState()
    val modelReport by vm.modelReport.collectAsState()

    val progress by vm.lessonProgress.collectAsState()
    val openSubject by vm.openSubjectId.collectAsState()
    val openLesson by vm.openLessonId.collectAsState()
    val tabScreens = setOf(Screen.Dashboard, Screen.Learn, Screen.Assistant, Screen.Progress, Screen.SubjectDetail, Screen.Lesson, Screen.Studio)
    val showBar = ready && error == null && screen in tabScreens

    BackHandler(enabled = ready && screen != Screen.Dashboard && screen != Screen.Welcome) { vm.back() }

    Surface(modifier = Modifier.fillMaxSize(), color = Page) {
      Column(Modifier.fillMaxSize()) {
       Box(Modifier.weight(1f)) {
        CompositionLocalProvider(
            LocalPageInsets provides (if (showBar) WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else null)
        ) {
        when {
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text(error.orEmpty(), color = Warn) }

            !ready -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Forest)
                Text("Preparing your offline study space…", color = Muted, modifier = Modifier.padding(top = 16.dp))
            }

            else -> when (screen) {
                Screen.Welcome -> WelcomeScreen(onBegin = { vm.go(Screen.Profile) })
                Screen.Profile -> ProfileScreen(
                    initial = home.profile,
                    subjects = home.subjects,
                    defaultSubjectId = home.active?.id,
                    onBack = { vm.back() },
                    onSave = { name, education, goal, subjectId -> vm.saveProfile(name, education, goal, subjectId) }
                )
                Screen.Dashboard -> DashboardScreen(
                    home = home,
                    onQuiz = { vm.startQuiz() },
                    onAssistant = { vm.go(Screen.Assistant) },
                    onProfile = { vm.go(Screen.Profile) },
                    onProgress = { vm.go(Screen.Progress) },
                    onSubjects = { vm.go(Screen.Subjects) },
                    onLearn = { vm.go(Screen.Learn) },
                    onDiagnostics = { vm.go(Screen.Diagnostics) },
                    onAbout = { vm.go(Screen.About) },
                    goal = goal,
                    plan = plan,
                    onPlan = { vm.go(Screen.Plan) },
                    onModel = { vm.go(Screen.Model) }
                )
                Screen.Subjects -> SubjectsScreen(home, onBack = { vm.back() }, onSelect = { vm.selectSubject(it) })
                Screen.Quiz -> {
                    val q = quiz
                    if (q != null) {
                        QuizScreen(q, onSelect = { vm.selectAnswer(it) }, onNext = { vm.nextQuestion() }, onExit = { vm.exitQuiz() })
                    } else {
                        CircularProgressIndicator(color = Forest)
                    }
                }
                Screen.Result -> ResultScreen(
                    result = result,
                    onAgain = { vm.startQuiz() },
                    onDashboard = { vm.go(Screen.Dashboard) },
                    onProgress = { vm.go(Screen.Progress) }
                )
                Screen.Assistant -> AssistantScreen(
                    subject = home.active,
                    chat = chat,
                    enabled = ready,
                    onAsk = { vm.ask(it) },
                    onClear = { vm.clearChat() },
                    onBack = { vm.back() },
                    onOpenLesson = { vm.openLesson(it) },
                    llmInstalled = llm.installed && useLlm,
                    generating = generating,
                    onCancel = { vm.cancelGeneration() }
                )
                Screen.Plan -> PlanScreen(
                    goal = goal, plan = plan, catalog = vm.catalog, progress = progress, busy = busy, llm = llm,
                    onSaveGoal = { t, i, h, w -> vm.saveGoal(t, i, h, w) },
                    onReplan = { vm.replanNow() },
                    onCoach = { vm.askCoach() },
                    onOpenLesson = { vm.openLessonFromPlan(it) },
                    onQuiz = { vm.quizSubject(it) },
                    onModel = { vm.go(Screen.Model) },
                    onBack = { vm.back() }
                )
                Screen.Model -> ModelScreen(
                    state = llm, busy = busy, report = modelReport, useLlm = useLlm,
                    onUseLlm = { vm.setUseLlm(it) },
                    onImport = { uri, kind -> vm.importModelFile(uri, kind) },
                    onRescan = { vm.rescanModel() },
                    onLoad = { vm.loadModel() },
                    onTest = { vm.testModel() },
                    onUnload = { vm.unloadModel() },
                    onDelete = { vm.deleteImportedModel() },
                    onBack = { vm.back() }
                )
                Screen.Progress -> ProgressScreen(home, onBack = { vm.back() }, onClear = { vm.clearProgress() })
                Screen.Diagnostics -> DiagnosticsScreen(
                    bench = bench,
                    history = benchHistory,
                    startup = vm.startupSummary(),
                    onQuick = { vm.runQuickBenchmark() },
                    onSustained = { vm.runSustainedBenchmark(it) },
                    onBack = { vm.back() }
                )
                Screen.About -> AboutScreen(onBack = { vm.back() })
                Screen.Studio -> HdlStudioScreen(vm.hdl)
                Screen.Learn -> LearnScreen(
                    catalog = vm.catalog, progress = progress, lastLessonId = vm.lastLessonId(),
                    onOpenSubject = { vm.openSubject(it) }, onOpenLesson = { vm.openLesson(it) }
                )
                Screen.SubjectDetail -> {
                    val id = openSubject
                    SubjectDetailScreen(
                        subject = id?.let { vm.catalog.subject(it) },
                        catalog = vm.catalog,
                        progress = progress,
                        recommendation = id?.let { vm.recommendationFor(it) },
                        onBack = { vm.back() },
                        onOpenLesson = { vm.openLesson(it) },
                        onOpenSubject = { vm.openSubject(it) },
                        onQuiz = { id?.let { vm.quizSubject(it) } },
                        onAsk = { id?.let { vm.activateSubject(it) { vm.go(Screen.Assistant) } } }
                    )
                }
                Screen.Lesson -> {
                    val l = openLesson?.let { vm.catalog.lesson(it) }
                    LessonScreen(
                        lesson = l, catalog = vm.catalog, progress = l?.let { progress[it.id] },
                        onBack = { vm.back() },
                        onNavigate = { vm.openLesson(it) },
                        onToggleComplete = { lesson, c -> vm.setLessonCompleted(lesson, c) },
                        onBookmark = { vm.toggleBookmark(it) },
                        onQuiz = { vm.quizSubject(it) },
                        onAsk = { vm.askAboutLesson(it) },
                        onOpenInStudio = { vm.openLessonInStudio(it) }
                    )
                }
            }
        }
        }
       }
       if (showBar) {
        val tab = when (screen) {
            Screen.Dashboard -> 0
            Screen.Learn, Screen.SubjectDetail, Screen.Lesson -> 1
            Screen.Studio -> 2
            Screen.Assistant -> 3
            Screen.Progress -> 4
            else -> 0
        }
        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
            listOf("🏠" to "Home", "📚" to "Learn", "🔧" to "Studio", "💬" to "Tutor", "📈" to "Progress").forEachIndexed { i, (icon, label) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = {
                        vm.go(when (i) { 0 -> Screen.Dashboard; 1 -> Screen.Learn; 2 -> Screen.Studio; 3 -> Screen.Assistant; else -> Screen.Progress })
                    },
                    icon = { Text(icon, fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(selectedTextColor = Forest, indicatorColor = Selected)
                )
            }
        }
       }
      }
    }
}
