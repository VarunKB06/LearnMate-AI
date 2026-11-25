package com.varunkb.learnmateai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.data.ChatMessageEntity
import com.varunkb.learnmateai.domain.Subject

private val SUGGESTIONS = listOf(
    "Show my progress",
    "What should I study this week?",
    "Which topic should I revise?",
    "Explain pointers",
    "Explain loops",
    "What is a primary key?",
    "How does binary search work?",
    "Difference between stack and queue",
    "Give me practice questions on pointers",
    "Explain it simply",
    "Give an example",
    "Are you an LLM?"
)

@Composable
fun AssistantScreen(
    subject: Subject?,
    chat: List<ChatMessageEntity>,
    enabled: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit = {},
    llmInstalled: Boolean = false,
    generating: Boolean = false,
    onCancel: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    fun send() {
        if (input.isNotBlank() && enabled) {
            onAsk(input)
            input = ""
        }
    }
    AppPage {
        Heading("Ask LearnMate", "On-device tutor for ${subject?.title ?: "your subject"}. It runs on this phone and needs no internet. Paste Verilog or a compiler error and I will explain it.")
        InfoCard(
            "How it works",
            if (llmInstalled) "Lessons plus on-device Llama" else "Lessons, not guesses",
            if (llmInstalled)
                "LearnMate first finds the best matching lesson. Llama, running on this phone through ExecuTorch, then explains it using only that lesson and your own progress. " +
                    "Answers take a while on a phone. Code, error, quiz and progress questions use fast built-in rules."
            else
                "LearnMate finds the best matching lesson in its built-in pack and shows it, with the source and a match score. " +
                    "No Llama model is installed yet, so it only answers topics in its lessons. Install one from Model setup on the home screen."
        )
        if (generating) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Forest)
                Text("Llama is thinking on this phone…", color = Ink)
            }
            SecondaryButton("Stop and use the offline answer", onClick = onCancel)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Type a question, then tap Ask…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Forest,
                unfocusedBorderColor = Outline,
                cursorColor = Forest
            )
        )
        PrimaryButton("Ask LearnMate", enabled = enabled && !generating && input.isNotBlank()) { send() }

        SectionLabel("Quick questions")
        SUGGESTIONS.forEach { s ->
            TextButton(onClick = { input = s }) { Text(s, color = Forest) }
        }

        if (chat.isNotEmpty()) {
            SectionLabel("Conversation (saved on this phone)")
            // Newest first so the latest answer is right below the input.
            chat.asReversed().forEach { m -> ChatBubble(m, onOpenLesson) }
            SecondaryButton("Clear conversation", onClick = onClear)
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessageEntity, onOpenLesson: (String) -> Unit) {
    val mine = m.role == "user"
    Card(
        colors = CardDefaults.cardColors(containerColor = if (mine) Selected else Color.White),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (mine) "YOU" else "LEARNMATE", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp)
            SelectionContainer {
                Text(m.text, color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
            }
            if (!mine && m.sourceTitle != null) {
                Text("Source lesson: ${m.sourceTitle}" + (m.confidence?.let { " · match $it%" } ?: ""), color = Muted, fontSize = 13.sp)
            }
            if (!mine && m.sourceLessonId != null) {
                TextButton(onClick = { onOpenLesson(m.sourceLessonId) }) { Text("Open this lesson →", color = Forest, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
