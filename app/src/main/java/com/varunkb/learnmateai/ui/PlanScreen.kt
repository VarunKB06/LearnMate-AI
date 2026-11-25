package com.varunkb.learnmateai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.GoalProfile
import com.varunkb.learnmateai.domain.LessonProgress
import com.varunkb.learnmateai.domain.LessonStatus
import com.varunkb.learnmateai.domain.PlanItem
import com.varunkb.learnmateai.domain.PlanItemKind
import com.varunkb.learnmateai.domain.StudyPlan
import com.varunkb.learnmateai.llm.LlmState

/** Goal entry, personalised study plan, and Llama coaching note. */
@Composable
fun PlanScreen(
    goal: GoalProfile?,
    plan: StudyPlan?,
    catalog: Catalog,
    progress: Map<String, LessonProgress>,
    busy: String?,
    llm: LlmState,
    onSaveGoal: (text: String, interests: String, hours: String, weeks: String) -> Unit,
    onReplan: () -> Unit,
    onCoach: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onQuiz: (String) -> Unit,
    onModel: () -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf(goal == null) }
    var text by remember(goal) { mutableStateOf(goal?.goalText.orEmpty()) }
    var interests by remember(goal) { mutableStateOf(goal?.interests?.joinToString(", ").orEmpty()) }
    var hours by remember(goal) { mutableStateOf(goal?.hoursPerWeek?.toString().orEmpty()) }
    var weeks by remember(goal) { mutableStateOf(goal?.weeks?.toString().orEmpty()) }

    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("My study plan", "Tell LearnMate what you want to achieve. It builds a week-by-week plan from your goal, your skill gaps and your time, and rebuilds it as you learn.")

        if (busy != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Forest)
                Text(busy, color = Ink)
            }
        }

        if (goal != null && !editing) {
            val subjects = goal.subjectIds.mapNotNull { catalog.subject(it)?.title }.joinToString(", ").ifBlank { "none" }
            InfoCard(
                "Your goal",
                goal.goalText,
                "${goal.targetType} · ${goal.hoursPerWeek} h/week for ${goal.weeks} week(s) · level ${goal.level}\n" +
                    "Subjects: $subjects\n" +
                    (if (goal.interests.isNotEmpty()) "Interests: ${goal.interests.joinToString(", ")}\n" else "") +
                    "Goal understood by: " + if (goal.source == GoalProfile.SOURCE_LLAMA) "Llama on-device" else "keyword rules"
            )
            SecondaryButton("Edit goal", enabled = busy == null) { editing = true }
        } else {
            SectionLabel("Your goal")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("e.g. I want to become a VLSI design verification engineer in 3 months, I can study 8 hours a week") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink, unfocusedTextColor = Ink,
                    focusedBorderColor = Forest, unfocusedBorderColor = Outline, cursorColor = Forest
                )
            )
            InputField("Interests (optional, comma separated)", interests) { interests = it }
            InputField("Hours per week (optional)", hours) { hours = it.filter { c -> c.isDigit() }.take(2) }
            InputField("Weeks available (optional)", weeks) { weeks = it.filter { c -> c.isDigit() }.take(2) }
            Text(
                if (llm.installed) "Llama on-device will read your goal and pick the subjects."
                else "No Llama model is installed, so a keyword reader will understand your goal.",
                color = Muted, fontSize = 14.sp
            )
            if (!llm.installed) TextButton(onClick = onModel) { Text("Set up the Llama model →", color = Forest, fontWeight = FontWeight.Bold) }
            PrimaryButton("Build my plan", enabled = text.isNotBlank() && busy == null) {
                onSaveGoal(text, interests, hours, weeks)
                editing = false
            }
        }

        if (goal != null) {
            if (plan == null) {
                InfoCard("No plan yet", "Building…", "Your plan appears here as soon as it is ready.")
            } else {
                val notes = plan.notes.joinToString("\n") { "• $it" }
                InfoCard(
                    "Plan, revision ${plan.revision}",
                    "${plan.weeks.size} week(s), ${plan.totalItems} step(s)",
                    notes.ifBlank { "Each week ends with a short checkpoint quiz that updates your mastery. The plan then adapts." }
                )
                val note = plan.coachNote
                if (note != null) {
                    InfoCard(
                        if (plan.coachStale) "Coaching note (written before your latest progress)" else "Coaching note",
                        "From Llama, on this phone",
                        note
                    )
                }
                if (llm.installed) {
                    SecondaryButton(if (note == null) "Ask Llama for a coaching note" else "Refresh coaching note", enabled = busy == null, onClick = onCoach)
                } else if (note == null) {
                    TextButton(onClick = onModel) { Text("Install Llama to get a coaching note →", color = Forest, fontWeight = FontWeight.Bold) }
                }
                SecondaryButton("Rebuild plan from my latest progress", enabled = busy == null, onClick = onReplan)

                plan.weeks.forEach { week ->
                    SectionLabel((if (week.number == 1) "This week · " else "Week ${week.number} · ") + week.theme + " · ${week.minutes} min")
                    week.items.forEach { item ->
                        PlanItemRow(item, progress, onOpenLesson, onQuiz)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanItemRow(
    item: PlanItem,
    progress: Map<String, LessonProgress>,
    onOpenLesson: (String) -> Unit,
    onQuiz: (String) -> Unit
) {
    val lessonId = item.lessonId
    val done = lessonId != null && progress[lessonId]?.status == LessonStatus.Completed
    val icon = when (item.kind) {
        PlanItemKind.Learn -> "📖"
        PlanItemKind.Revise -> "🔁"
        PlanItemKind.Quiz -> "📝"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((if (done) "✓ " else "") + icon + " " + item.title, color = if (done) Muted else Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("${item.minutes} min · ${item.reason}", color = Muted, fontSize = 13.sp)
            TextButton(onClick = { if (lessonId != null) onOpenLesson(lessonId) else onQuiz(item.subjectId) }) {
                Text(if (lessonId != null) "Open lesson →" else "Start quiz →", color = Forest, fontWeight = FontWeight.Bold)
            }
        }
    }
}
