package com.varunkb.learnmateai.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.QuizState
import com.varunkb.learnmateai.ResultState

@Composable
fun QuizScreen(
    quiz: QuizState,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val item = quiz.current
    val total = quiz.items.size
    AppPage {
        TextButton(onClick = onExit) { Text("← Exit quiz", color = Forest) }
        Brand()
        Heading("${quiz.subject.title} quiz", "Question ${quiz.index + 1} of $total")
        LinearProgressIndicator(
            progress = { (quiz.index + 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = Forest,
            trackColor = Track
        )
        InfoCard("Topic", item.question.topic, item.question.prompt)
        item.choices.forEachIndexed { i, choice ->
            ChoiceButton("${'A' + i}. $choice", selected = quiz.selected == i) { onSelect(i) }
        }
        PrimaryButton(if (quiz.isLast) "Finish quiz" else "Next question", enabled = quiz.selected >= 0, onClick = onNext)
        if (quiz.selected < 0) Text("Choose an answer to continue.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun ResultScreen(result: ResultState?, onAgain: () -> Unit, onDashboard: () -> Unit, onProgress: () -> Unit) {
    val outcome = result?.outcome
    val score = outcome?.correct ?: 0
    val total = outcome?.total ?: 0
    val percent = outcome?.percent ?: 0
    AppPage {
        Spacer(Modifier.height(35.dp))
        Text("QUIZ COMPLETE", color = Forest, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            if (percent >= 80) "Excellent work!" else "Good effort!",
            color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text("$score / $total", color = Forest, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("$percent% correct", color = Muted, fontSize = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        InfoCard(
            "Your learning insight",
            if (percent >= 80) "Strong performance" else "Keep building your foundation",
            if (percent >= 80) "You answered most questions correctly. Try a more challenging set next."
            else "Review the explanations below and take another short quiz to strengthen your understanding."
        )
        if (outcome != null) {
            SectionLabel("Topic scores")
            outcome.topicScores.forEach { (topic, s) ->
                Text("$topic: ${s.first}/${s.second}", color = Ink, fontWeight = FontWeight.SemiBold)
            }
            SectionLabel("Updated mastery")
            outcome.updatedMastery.sortedBy { it.topic }.forEach { MasteryRow(it) }
        }
        if (result != null && result.review.isNotEmpty()) {
            SectionLabel("Review your mistakes")
            result.review.forEach { r ->
                InfoCard("Question", r.prompt, "Your answer: ${r.yourAnswer}\nCorrect answer: ${r.correctAnswer}\n${r.explanation}")
            }
        }
        PrimaryButton("Try again", onClick = onAgain)
        SecondaryButton("View progress", onClick = onProgress)
        SecondaryButton("Back to dashboard", onClick = onDashboard)
    }
}
