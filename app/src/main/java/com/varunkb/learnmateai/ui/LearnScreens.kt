package com.varunkb.learnmateai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.Curriculum
import com.varunkb.learnmateai.domain.Lesson
import com.varunkb.learnmateai.domain.LessonProgress
import com.varunkb.learnmateai.domain.LessonStatus
import com.varunkb.learnmateai.domain.RecommendationKind
import com.varunkb.learnmateai.domain.StudyRecommendation
import com.varunkb.learnmateai.domain.Subject

private fun statusMark(p: LessonProgress?) = when (p?.status) {
    LessonStatus.Completed -> "✓"
    LessonStatus.InProgress -> "●"
    else -> "○"
}

private fun statusWord(p: LessonProgress?) = when (p?.status) {
    LessonStatus.Completed -> "Completed"
    LessonStatus.InProgress -> "In progress"
    else -> "Not started"
}

@Composable
private fun TappableCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Forest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Selected, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun ProgressBar(percent: Int) {
    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Forest, trackColor = Track)
}

@Composable
private fun RecommendationCard(rec: StudyRecommendation, onOpen: (Lesson) -> Unit) {
    val label = when (rec.kind) {
        RecommendationKind.Revise -> "Recommended: revise"
        RecommendationKind.ContinueLearning -> "Recommended next lesson"
        RecommendationKind.FirstLesson -> "Start here"
        RecommendationKind.NextSubject -> "Next in your path"
        RecommendationKind.AllDone -> "Well done"
        RecommendationKind.TakeQuiz -> "Practice"
    }
    InfoCard(label, rec.title, rec.detail)
    rec.lesson?.let { PrimaryButton("Open lesson", onClick = { onOpen(it) }) }
}

/** The lesson catalogue: learning paths, subjects with progress, saved lessons and a continue card. */
@Composable
fun LearnScreen(
    catalog: Catalog,
    progress: Map<String, LessonProgress>,
    lastLessonId: String?,
    onOpenSubject: (String) -> Unit,
    onOpenLesson: (String) -> Unit
) {
    val last = lastLessonId?.let { catalog.lesson(it) }
    val saved = catalog.allLessons.filter { progress[it.id]?.bookmarked == true }
    val totalLessons = catalog.allLessons.size
    val done = catalog.allLessons.count { progress[it.id]?.status == LessonStatus.Completed }
    AppPage {
        Brand()
        Heading("Learn", "$totalLessons lessons in ${catalog.subjects.size} subjects. $done completed.")
        if (last != null) {
            val p = progress[last.id]
            TappableCard({ onOpenLesson(last.id) }) {
                SectionLabel(if (p?.status == LessonStatus.Completed) "Last opened" else "Continue where you left off")
                Text(last.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${catalog.subject(last.subjectId)?.title.orEmpty()} · ${last.minutes} min · ${statusWord(p)}", color = Muted, fontSize = 14.sp)
            }
        }
        if (saved.isNotEmpty()) {
            SectionLabel("Saved lessons (${saved.size})")
            saved.forEach { l ->
                TappableCard({ onOpenLesson(l.id) }) {
                    Text("★ ${l.title}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(catalog.subject(l.subjectId)?.title.orEmpty(), color = Muted, fontSize = 13.sp)
                }
            }
        }
        catalog.paths.forEach { path ->
            SectionLabel(path.name)
            path.subjects.forEach { s ->
                val lessons = catalog.lessons(s.id)
                val sp = Curriculum.progressFor(lessons, progress)
                TappableCard({ onOpenSubject(s.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.icon, fontSize = 26.sp)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("${lessons.size} lessons · ${sp.completed} completed · ${sp.percent}%", color = Muted, fontSize = 14.sp)
                        }
                    }
                    ProgressBar(sp.percent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(s.level)
                        if (sp.inProgress > 0) Chip("${sp.inProgress} in progress")
                        if (s.prerequisites.isNotEmpty()) {
                            Chip("After: " + s.prerequisites.mapNotNull { catalog.subject(it)?.title }.joinToString(", "))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectDetailScreen(
    subject: Subject?,
    catalog: Catalog,
    progress: Map<String, LessonProgress>,
    recommendation: StudyRecommendation?,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenSubject: (String) -> Unit,
    onQuiz: () -> Unit,
    onAsk: () -> Unit
) {
    if (subject == null) {
        AppPage {
            TextButton(onClick = onBack) { Text("← Learn", color = Forest, fontWeight = FontWeight.Bold) }
            Text("This subject is not available.", color = Muted)
        }
        return
    }
    val lessons = catalog.lessons(subject.id)
    val sp = Curriculum.progressFor(lessons, progress)
    val missing = Curriculum.missingPrerequisites(subject, catalog, progress)
    AppPage {
        TextButton(onClick = onBack) { Text("← Learn", color = Forest, fontWeight = FontWeight.Bold) }
        Text("${subject.icon}  ${subject.title}", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(subject.description, color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(subject.level)
            Chip("${lessons.size} lessons")
            Chip("${catalog.questionCount(subject.id)} questions")
        }
        InfoCard("Your progress", "${sp.completed} of ${sp.total} lessons completed (${sp.percent}%)", if (sp.inProgress > 0) "${sp.inProgress} in progress" else "")
        ProgressBar(sp.percent)
        if (missing.isNotEmpty()) {
            InfoCard("Before you start", "Recommended first: " + missing.joinToString(", ") { it.title }, "These subjects teach ideas this one builds on. You can still open any lesson.")
            missing.forEach { m -> SecondaryButton("Open ${m.title}") { onOpenSubject(m.id) } }
        }
        recommendation?.let { rec ->
            RecommendationCard(rec, onOpen = { lesson ->
                onOpenLesson(lesson.id)
            })
        }
        SectionLabel("All lessons")
        lessons.forEachIndexed { i, l ->
            val p = progress[l.id]
            TappableCard({ onOpenLesson(l.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(statusMark(p), color = Forest, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${l.title}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("${l.topic} · ${l.level} · ${l.minutes} min · ${statusWord(p)}", color = Muted, fontSize = 13.sp)
                    }
                    if (p?.bookmarked == true) Text("★", color = Forest, fontSize = 20.sp)
                }
            }
        }
        PrimaryButton("Practice quiz for ${subject.title}", onClick = onQuiz)
        SecondaryButton("Ask the tutor about ${subject.title}", onClick = onAsk)
    }
}

@Composable
private fun Block(label: String, content: @Composable () -> Unit) {
    SectionLabel(label)
    content()
}

@Composable
private fun CodeBox(text: String) {
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF10261E), RoundedCornerShape(14.dp)).horizontalScroll(rememberScrollState()).padding(14.dp)
    ) {
        Text(text, color = Color(0xFFE6F4EC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, softWrap = false)
    }
}

@Composable
private fun PracticeRow(index: Int, q: String, a: String) {
    var shown by remember(q) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${index + 1}. $q", color = Ink, fontSize = 16.sp, lineHeight = 23.sp)
        if (shown) {
            Text(a, color = Forest, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.background(Selected, RoundedCornerShape(10.dp)).padding(10.dp).fillMaxWidth())
        }
        TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide answer" else "Show answer", color = Forest) }
    }
}

@Composable
fun LessonScreen(
    lesson: Lesson?,
    catalog: Catalog,
    progress: LessonProgress?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onToggleComplete: (Lesson, Boolean) -> Unit,
    onBookmark: (Lesson) -> Unit,
    onQuiz: (String) -> Unit,
    onAsk: (Lesson) -> Unit,
    onOpenInStudio: (Lesson) -> Unit
) {
    if (lesson == null) {
        AppPage {
            TextButton(onClick = onBack) { Text("← Back", color = Forest, fontWeight = FontWeight.Bold) }
            Text("This lesson is not available.", color = Muted)
        }
        return
    }
    val (prev, next) = catalog.neighbours(lesson.id)
    val done = progress?.status == LessonStatus.Completed
    val marked = progress?.bookmarked == true
    AppPage {
        TextButton(onClick = onBack) { Text("← ${catalog.subject(lesson.subjectId)?.title ?: "Lessons"}", color = Forest, fontWeight = FontWeight.Bold) }
        Text(lesson.title, color = Ink, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(lesson.level); Chip("${lesson.minutes} min"); Chip(if (done) "Completed" else statusWord(progress))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallAction(if (marked) "★ Saved" else "☆ Save", Modifier.weight(1f)) { onBookmark(lesson) }
            SmallAction(if (done) "Mark as not done" else "✓ Mark complete", Modifier.weight(1f)) { onToggleComplete(lesson, !done) }
        }
        if (lesson.objectives.isNotEmpty()) Block("What you will learn") {
            lesson.objectives.forEach { Text("• $it", color = Ink, fontSize = 16.sp, lineHeight = 23.sp) }
        }
        Block("Explanation") {
            lesson.body.split("\n\n").filter { it.isNotBlank() }.forEach { Text(it.trim(), color = Ink, fontSize = 16.sp, lineHeight = 25.sp) }
        }
        if (lesson.definitions.isNotEmpty()) Block("Key definitions") {
            lesson.definitions.forEach {
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(it.term, color = Forest, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(it.meaning, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
        if (lesson.example != null) Block("Worked example") {
            CodeBox(lesson.example)
            lesson.exampleNote?.let { Text(it, color = Muted, fontSize = 15.sp, lineHeight = 22.sp) }
        }
        if (lesson.diagram != null) Block("Diagram") { CodeBox(lesson.diagram) }
        if (lesson.hdl != null) {
            InfoCard("Try it", lesson.hdl.title, "Open this design and its testbench in HDL Studio, then compile and simulate it on your phone.")
            PrimaryButton("Open in HDL Studio", onClick = { onOpenInStudio(lesson) })
        }
        if (lesson.mistakes.isNotEmpty()) Block("Common mistakes") {
            lesson.mistakes.forEach { Text("⚠ $it", color = Warn, fontSize = 15.sp, lineHeight = 22.sp) }
        }
        if (lesson.practice.isNotEmpty()) Block("Practice questions") {
            lesson.practice.forEachIndexed { i, p -> PracticeRow(i, p.question, p.answer) }
        }
        if (lesson.exercises.isNotEmpty()) Block("Exercises") {
            lesson.exercises.forEach { Text("▸ $it", color = Ink, fontSize = 16.sp, lineHeight = 23.sp) }
        }
        if (lesson.summary.isNotEmpty()) Block("Summary") {
            lesson.summary.forEach { Text("• $it", color = Ink, fontSize = 16.sp, lineHeight = 23.sp) }
        }
        PrimaryButton(if (done) "Completed ✓ (tap to undo)" else "Mark lesson complete", onClick = { onToggleComplete(lesson, !done) })
        SecondaryButton("Ask the tutor about this lesson") { onAsk(lesson) }
        SecondaryButton("Quiz me on ${catalog.subject(lesson.subjectId)?.title ?: "this subject"}") { onQuiz(lesson.subjectId) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallAction("← Previous", Modifier.weight(1f), enabled = prev != null) { prev?.let { onNavigate(it.id) } }
            SmallAction("Next →", Modifier.weight(1f), enabled = next != null) { next?.let { onNavigate(it.id) } }
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun SmallAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) Forest else Outline),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Forest)
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}
