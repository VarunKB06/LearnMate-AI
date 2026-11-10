package com.varunkb.learnmateai.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.HomeState
import java.text.DateFormat
import java.util.Date

@Composable
fun ProgressScreen(home: HomeState, onBack: () -> Unit, onClear: () -> Unit) {
    val attempts = home.attempts
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("Your progress", "${home.active?.title ?: "Subject"}: a record of learning activity saved on this phone.")
        InfoCard(
            "Overall accuracy",
            "${home.accuracy}%",
            "${attempts.size} completed quiz attempt(s); ${attempts.sumOf { it.correct }} correct answers out of ${attempts.sumOf { it.total }}."
        )
        SectionLabel("Topic mastery")
        if (home.mastery.isEmpty()) {
            Text("No topic results yet. Complete a practice quiz to start tracking progress.", color = Muted)
        } else {
            home.mastery.forEach { MasteryRow(it) }
            Text(
                "Mastery is an estimate from Bayesian Knowledge Tracing, not just a percentage of right answers: recent answers and question difficulty history both matter.",
                color = Muted, fontSize = 13.sp
            )
        }
        SectionLabel("Quiz history")
        if (attempts.isEmpty()) Text("No quizzes completed yet.", color = Muted)
        attempts.forEachIndexed { index, a ->
            val pct = if (a.total == 0) 0 else a.correct * 100 / a.total
            val when_ = if (a.finishedAt > 0) formatter.format(Date(a.finishedAt)) else "Earlier version"
            InfoCard(
                "Attempt ${attempts.size - index}${if (a.source == "legacy") " (imported)" else ""}",
                "${a.correct}/${a.total} correct",
                "$pct% accuracy · $when_"
            )
        }
        SecondaryButton("Clear ${home.active?.title ?: "subject"} history", onClick = onClear)
        Text("This clears quiz results and mastery for this subject only. Your profile and other subjects remain saved.", color = Muted, fontSize = 13.sp)
    }
}
