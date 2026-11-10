package com.varunkb.learnmateai.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.HomeState
import com.varunkb.learnmateai.SubjectInfo
import com.varunkb.learnmateai.data.ProfileEntity

@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    AppPage {
        Spacer(Modifier.height(35.dp))
        Brand()
        Spacer(Modifier.height(28.dp))
        Heading("Learn smarter.\nGrow faster.", "Your personal learning companion, designed around your goals, your pace and your progress.")
        Spacer(Modifier.height(14.dp))
        InfoCard(
            "Your learning. Your way.",
            "A personal study space",
            "• Lessons, a Verilog simulator and adaptive quizzes\n• Topic mastery tracked on your phone\n• Offline tutor, no internet needed"
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Let's Begin", onClick = onBegin)
        Text("Your learning journey starts here.", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun ProfileScreen(
    initial: ProfileEntity?,
    subjects: List<SubjectInfo>,
    defaultSubjectId: String?,
    onBack: () -> Unit,
    onSave: (name: String, education: String, goal: String, subjectId: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var education by remember(initial) { mutableStateOf(initial?.education ?: "Undergraduate") }
    var goal by remember(initial) { mutableStateOf(initial?.goal ?: "Programming") }
    var subjectId by remember(initial, defaultSubjectId) {
        mutableStateOf(initial?.activeSubjectId ?: defaultSubjectId ?: "c")
    }
    AppPage {
        TextButton(onClick = onBack) { Text("← Back", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("Let's get to know you", "Your profile helps LearnMate tailor practice and recommendations.")
        InputField("Your name", name) { name = it }
        InputField("Education level", education) { education = it }
        InputField("Learning goal", goal) { goal = it }
        SectionLabel("Preferred subject")
        subjects.forEach { info ->
            ChoiceButton(info.subject.title, selected = subjectId == info.subject.id) { subjectId = info.subject.id }
        }
        PrimaryButton("Save profile", enabled = name.isNotBlank()) {
            if (name.isNotBlank()) onSave(name, education, goal, subjectId)
        }
        Text("All profile information is stored locally on this device.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun SubjectsScreen(home: HomeState, onBack: () -> Unit, onSelect: (String) -> Unit) {
    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("Choose a subject", "Each subject has its own question bank, lessons and mastery tracking.")
        home.subjects.forEach { info ->
            val status = buildString {
                append("${info.questionCount} questions · ${info.attempts} quiz attempt(s)")
                info.masteryPercent?.let { append(" · $it% average mastery") }
            }
            InfoCard(if (home.active?.id == info.subject.id) "Current subject" else "Subject", info.subject.title, info.subject.description + "\n" + status)
            SecondaryButton(
                if (home.active?.id == info.subject.id) "Selected" else "Switch to ${info.subject.title}",
                enabled = home.active?.id != info.subject.id
            ) { onSelect(info.subject.id) }
        }
    }
}

@Composable
fun DashboardScreen(
    home: HomeState,
    onQuiz: () -> Unit,
    onAssistant: () -> Unit,
    onProfile: () -> Unit,
    onProgress: () -> Unit,
    onSubjects: () -> Unit,
    onLearn: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit
) {
    val subject = home.active
    val latest = home.attempts.firstOrNull()
    AppPage {
        Brand()
        Heading("Hello, ${home.profile?.name?.ifBlank { "Learner" } ?: "Learner"}!", "Ready to continue your learning journey?")
        InfoCard(
            "Your learning subject",
            subject?.title ?: "No subject",
            "Goal: ${home.profile?.goal.orEmpty()}\nEducation: ${home.profile?.education.orEmpty()}"
        )
        SecondaryButton("Change subject", onClick = onSubjects)
        InfoCard(
            "Learning progress",
            "${home.accuracy}% overall accuracy",
            "${home.attempts.size} quiz attempt(s) saved on this phone. Latest: ${latest?.let { "${it.correct}/${it.total}" } ?: "No quiz yet"}."
        )
        InfoCard("Recommended next step", home.recommendation.title, home.recommendation.detail)
        PrimaryButton("Browse lessons", onClick = onLearn)
        SecondaryButton("Start a practice quiz", enabled = subject != null, onClick = onQuiz)
        SecondaryButton("Ask LearnMate (Offline)", onClick = onAssistant)
        SecondaryButton("View progress and topic performance", onClick = onProgress)
        TextButton(onClick = onProfile, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Edit learner profile", color = Forest, fontWeight = FontWeight.Bold)
        }
        if (home.mastery.isNotEmpty()) {
            SectionLabel("Topic mastery")
            home.mastery.forEach { MasteryRow(it) }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDiagnostics, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Device diagnostics and benchmark", color = Forest)
        }
        TextButton(onClick = onAbout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("About the AI in this app", color = Forest)
        }
        Text("Your learning data stays on this device.", color = Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
