package com.varunkb.learnmateai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.BenchState
import com.varunkb.learnmateai.data.BenchmarkRunEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(
    bench: BenchState,
    history: List<BenchmarkRunEntity>,
    startup: String,
    onQuick: () -> Unit,
    onSustained: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("Device diagnostics", "Measure how LearnMate performs on this phone: latency, memory, battery and temperature.")
        InfoCard("Start-up", "Last launch", startup)
        InfoCard(
            "Before you run",
            "For meaningful numbers",
            "Close other apps, keep the screen on, and unplug the charger for the sustained test. " +
                "The tests use a temporary database and never change your quiz data."
        )
        PrimaryButton("Run quick benchmark (about 5 s)", enabled = !bench.running, onClick = onQuick)
        SecondaryButton("Run sustained test (2 min)", enabled = !bench.running) { onSustained(120) }
        SecondaryButton("Run sustained test (10 min, battery)", enabled = !bench.running) { onSustained(600) }
        if (bench.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Forest)
                Text(bench.status, color = Ink)
            }
        } else if (bench.status.isNotBlank()) {
            Text(bench.status, color = Muted)
        }
        if (bench.report.isNotBlank()) {
            ReportCard("Latest report", bench.report, context)
        }
        if (history.isNotEmpty()) {
            SectionLabel("Saved reports")
            history.forEach { run ->
                ReportCard("${run.name} · ${formatter.format(Date(run.createdAt))}", run.report, context)
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, report: String, context: Context) {
    InfoCard("Benchmark", title, "")
    SelectionContainer {
        Text(report, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
    }
    SecondaryButton("Copy report") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LearnMate benchmark", report))
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("About the AI in LearnMate", "What runs on your phone, and what does not.")
        InfoCard(
            "Adaptive learning model",
            "Bayesian Knowledge Tracing",
            "Each topic has a mastery probability that is updated after every answer, accounting for lucky guesses and careless slips. " +
                "It decides which topics your next quiz emphasises and what to revise. Parameters are fixed defaults, not trained on real learner data."
        )
        InfoCard(
            "Offline tutor",
            "BM25 lesson retrieval",
            "Questions are matched against a bundled lesson pack with BM25 ranking. The tutor shows the lesson that best matches, with its source and match score, " +
                "and says it does not know when nothing matches well."
        )
        InfoCard(
            "What it is not",
            "Not a generative LLM",
            "LearnMate does not generate free-form text and has no neural network inside. It cannot answer questions outside its lesson pack."
        )
        InfoCard(
            "Privacy",
            "Fully offline",
            "The app has no internet permission. Your profile, quiz history, mastery and chat are stored in a local database on this phone."
        )
    }
}
