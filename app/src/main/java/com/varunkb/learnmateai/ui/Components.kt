package com.varunkb.learnmateai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.domain.TopicMastery

val Forest = Color(0xFF176B4A)
val ForestDark = Color(0xFF123F30)
val Page = Color(0xFFF4F7F4)
val Ink = Color(0xFF173B2F)
val Muted = Color(0xFF52645D)
val Outline = Color(0xFFB8C4BE)
val Track = Color(0xFFDCE7E0)
val Selected = Color(0xFFE0F0E7)
val Warn = Color(0xFF8A4B00)

/** Insets applied by [AppPage]; the root narrows this when a bottom navigation bar already handles the bottom edge. */
val LocalPageInsets = compositionLocalOf<WindowInsets?> { null }

@Composable
fun AppPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPageInsets.current ?: WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun Brand() {
    Text("LEARNMATE AI", color = Forest, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
}

@Composable
fun Heading(title: String, subtitle: String? = null) {
    Text(title, color = Ink, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = Muted, fontSize = 17.sp, lineHeight = 25.sp)
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Forest, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp, fontSize = 14.sp)
}

@Composable
fun InfoCard(label: String, title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(label.uppercase(), color = Forest, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp)
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
            if (body.isNotBlank()) Text(body, color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Forest)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Full-width selectable option used for quiz answers and subject picking. */
@Composable
fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Selected else Color.White,
            contentColor = Ink
        ),
        border = BorderStroke(1.dp, if (selected) Forest else Outline)
    ) {
        Text(text, modifier = Modifier.fillMaxWidth(), fontSize = 16.sp)
    }
}

@Composable
fun InputField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions.Default,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Forest,
                unfocusedBorderColor = Outline,
                cursorColor = Forest
            )
        )
    }
}

/** Mastery bar: BKT estimated mastery with the raw correct/total alongside. */
@Composable
fun MasteryRow(m: TopicMastery) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${m.topic}: ${m.masteryPercent}% mastery (${m.correct}/${m.total} correct)", color = Ink, fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { m.masteryPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = Forest,
            trackColor = Track
        )
    }
}
