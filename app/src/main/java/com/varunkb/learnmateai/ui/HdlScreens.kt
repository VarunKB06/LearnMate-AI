package com.varunkb.learnmateai.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.EditorFile
import com.varunkb.learnmateai.HdlController
import com.varunkb.learnmateai.data.HdlProjectEntity
import com.varunkb.learnmateai.hdl.VcdWriter
import com.varunkb.learnmateai.hdl.VerilogHighlighter
import com.varunkb.learnmateai.hdl.Waveform
import java.text.DateFormat
import java.util.Date

private val CodeBg = Color(0xFFFCFDFC)
private val ErrorRed = Color(0xFFB3261E)
private val MONO = FontFamily.Monospace

// ------------------------------------------------------------------ entry

@Composable
fun HdlStudioScreen(c: HdlController) {
    val st by c.state.collectAsState()
    val projects by c.projects.collectAsState(initial = emptyList())
    if (st.projectId == null) ProjectListScreen(c, projects, st.message) else EditorScreen(c)
}

// ------------------------------------------------------------------ project list

@Composable
private fun ProjectListScreen(c: HdlController, projects: List<HdlProjectEntity>, message: String?) {
    var deleting by remember { mutableStateOf<HdlProjectEntity?>(null) }
    AppPage {
        Brand()
        Heading("HDL Studio", "Write Verilog, compile it and simulate it on this phone. Everything runs offline.")
        if (message != null) {
            Text(message, color = ErrorRed)
            TextButton(onClick = { c.dismissMessage() }) { Text("Dismiss", color = Forest) }
        }
        PrimaryButton("New blank project") { c.newBlank() }
        SectionLabel("Your projects")
        if (projects.isEmpty()) {
            InfoCard("Nothing here yet", "No projects", "Create a blank project, start from a template below, or open a runnable example from any Verilog lesson in Learn.")
        }
        projects.forEach { p ->
            Column(
                Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(18.dp))
                    .clickable { c.openProject(p.id) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(p.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Edited " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.updatedAt)) +
                        if (p.lessonId != null) " · from a lesson" else "",
                    color = Muted, fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { c.openProject(p.id) }) { Text("Open", color = Forest, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { deleting = p }) { Text("Delete", color = ErrorRed) }
                }
            }
        }
        SectionLabel("Start from a template")
        c.templates.filter { it.id != "blank" }.forEach { t ->
            InfoCard(if (t.files.size > 1) "Design + testbench" else "Template", t.title, t.description)
            SecondaryButton("Create project: ${t.title}") { c.createFromTemplate(t.id) }
        }
        Text(
            "The simulator supports a teaching subset of Verilog: modules, assign, always/initial, blocking and non-blocking assignments, case, loops, memories, " +
                "parameters, \$display/\$monitor/\$finish. Unsupported constructs produce a clear error. See the README for the full list.",
            color = Muted, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete project?") },
            text = { Text("\"${p.name}\" and all its files will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { c.deleteProject(p.id); deleting = null }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ------------------------------------------------------------------ editor screen

@Composable
private fun SmallBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        border = BorderStroke(1.dp, if (enabled) Forest else Outline),
        colors = if (primary) ButtonDefaults.outlinedButtonColors(containerColor = Forest, contentColor = Color.White)
        else ButtonDefaults.outlinedButtonColors(contentColor = Forest)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun EditorScreen(c: HdlController) {
    val st by c.state.collectAsState()
    var tab by remember { mutableStateOf(0) } // 0 console, 1 waveform
    var insert by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var insertCounter by remember { mutableStateOf(0L) }
    var dialog by remember { mutableStateOf<String?>(null) } // "rename_project", "add", "rename_file", "delete_file"
    val file = st.activeFile
    val insets = LocalPageInsets.current ?: WindowInsets.safeDrawing

    LaunchedEffect(st.waveform) { if (st.waveform != null) tab = 0 }

    Column(Modifier.fillMaxSize().windowInsetsPadding(insets).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { c.closeProject() }) { Text("← Projects", color = Forest, fontWeight = FontWeight.Bold) }
            Text(
                st.projectName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                modifier = Modifier.weight(1f).clickable { dialog = "rename_project" }
            )
            TextButton(onClick = { dialog = "rename_project" }) { Text("Rename", color = Forest, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            st.files.forEachIndexed { i, f ->
                val sel = i == st.active
                Text(
                    (if (f.isTestbench) "🧪 " else "") + f.name,
                    color = if (sel) Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(if (sel) Forest else Selected, RoundedCornerShape(10.dp)).clickable { c.select(i) }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Text("+ File", color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { dialog = "add" }.padding(horizontal = 10.dp, vertical = 8.dp))
            if (file != null) {
                Text("Rename", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { dialog = "rename_file" }.padding(6.dp))
                Text("Delete", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { dialog = "delete_file" }.padding(6.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("Compile", Modifier.weight(1f), enabled = !st.busy) { c.compile() }
            SmallBtn(if (st.busy) "Running…" else "▶ Run", Modifier.weight(1f), enabled = !st.busy, primary = true) { c.run() }
            SmallBtn("Format", Modifier.weight(1f)) { c.format() }
            SmallBtn(if (st.busy) "Stop" else "Clear", Modifier.weight(1f)) { if (st.busy) c.stop() else c.clearConsole() }
        }
        if (file != null) SymbolBar(onInsert = { insertCounter += 1; insert = insertCounter to it })
        Box(Modifier.weight(0.55f).fillMaxWidth().border(1.dp, Outline, RoundedCornerShape(12.dp)).background(CodeBg, RoundedCornerShape(12.dp))) {
            if (file != null) CodeEditor(file, insert, onInsertDone = { insert = null }, onChange = { c.edit(it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Console", color = if (tab == 0) Forest else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.clickable { tab = 0 }.padding(vertical = 4.dp))
            Text(
                "Waveform", color = if (tab == 1) Forest else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                modifier = Modifier.clickable { tab = 1 }.padding(vertical = 4.dp)
            )
            if (file != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { c.setTestbench(!file.isTestbench) }) {
                    Checkbox(checked = file.isTestbench, onCheckedChange = { c.setTestbench(it) }, colors = CheckboxDefaults.colors(checkedColor = Forest))
                    Text("testbench", color = Muted, fontSize = 13.sp)
                }
            }
        }
        Box(Modifier.weight(0.45f).fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
            if (tab == 0) ConsoleView(st.console) else WaveView(st.waveform)
        }
    }

    when (dialog) {
        "rename_project" -> TextDialog("Rename project", st.projectName, "Project name", onDismiss = { dialog = null }) { c.renameProject(it); dialog = null }
        "rename_file" -> TextDialog("Rename file", file?.name.orEmpty(), "File name (.v or .sv)", onDismiss = { dialog = null }) { c.renameFile(it); dialog = null }
        "add" -> AddFileDialog(onDismiss = { dialog = null }) { n, tb -> c.addFile(n, tb); dialog = null }
        "delete_file" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete file?") },
            text = { Text("\"${file?.name.orEmpty()}\" will be removed from this project.") },
            confirmButton = { TextButton(onClick = { c.deleteActiveFile(); dialog = null }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
    st.message?.let { m ->
        AlertDialog(
            onDismissRequest = { c.dismissMessage() },
            confirmButton = { TextButton(onClick = { c.dismissMessage() }) { Text("OK") } },
            text = { Text(m) }
        )
    }
}

@Composable
private fun TextDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { v = it }, singleLine = true, label = { Text(label) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, cursorColor = Forest)
            )
        },
        confirmButton = { TextButton(onClick = { onOk(v) }, enabled = v.isNotBlank()) { Text("OK", color = Forest) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddFileDialog(onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("new_module.v") }
    var tb by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add file") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, label = { Text("File name (.v or .sv)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, cursorColor = Forest)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { tb = !tb }) {
                    Checkbox(checked = tb, onCheckedChange = { tb = it }, colors = CheckboxDefaults.colors(checkedColor = Forest))
                    Text("This is a testbench")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(name, tb) }) { Text("Add", color = Forest) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ editor

private val SYMBOLS = listOf(";", "(", ")", "[", "]", "{", "}", "=", "<=", "@", "#", "'", "\"", "~", "&", "|", "^", "?", ":", "\$")

/** Symbols are inserted through a shared insertion request consumed by the editor (see [CodeEditor]). */
@Composable
private fun SymbolBar(onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Tab", color = Forest, fontWeight = FontWeight.Bold, fontFamily = MONO,
            modifier = Modifier.background(Selected, RoundedCornerShape(8.dp)).clickable { onInsert("  ") }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        SYMBOLS.forEach { sym ->
            Text(
                sym, color = Ink, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 16.sp,
                modifier = Modifier.background(Selected, RoundedCornerShape(8.dp)).clickable { onInsert(sym) }.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

private class VerilogTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text.text)
        for (sp in VerilogHighlighter.spans(text.text)) {
            val style = when (sp.kind) {
                VerilogHighlighter.Kind.Keyword -> SpanStyle(color = Color(0xFF0B6B3A), fontWeight = FontWeight.Bold)
                VerilogHighlighter.Kind.Number -> SpanStyle(color = Color(0xFF1A5FB4))
                VerilogHighlighter.Kind.Comment -> SpanStyle(color = Color(0xFF7A8680))
                VerilogHighlighter.Kind.String -> SpanStyle(color = Color(0xFFB55A00))
                VerilogHighlighter.Kind.SystemTask -> SpanStyle(color = Color(0xFF7B3FA0))
                VerilogHighlighter.Kind.Directive -> SpanStyle(color = Color(0xFF8A4B00))
            }
            b.addStyle(style, sp.start.coerceIn(0, text.length), sp.end.coerceIn(0, text.length))
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val INDENT_MORE = Regex("(\\bbegin|\\bcase[zx]?\\s*\\(.*\\)|^module\\b.*;)\\s*(//.*)?$")

@Composable
private fun CodeEditor(file: EditorFile, insert: Pair<Long, String>?, onInsertDone: () -> Unit, onChange: (String) -> Unit) {
    var tfv by remember(file.id) { mutableStateOf(TextFieldValue(file.text)) }
    var lastEmitted by remember(file.id) { mutableStateOf(file.text) }
    // Changes that did not come from typing here (Format) replace the text but keep the cursor in range.
    LaunchedEffect(file.text) {
        if (file.text != lastEmitted) {
            lastEmitted = file.text
            tfv = TextFieldValue(file.text, TextRange(minOf(tfv.selection.start, file.text.length)))
        }
    }
    LaunchedEffect(insert) {
        if (insert != null) {
            val sel = tfv.selection
            val t = tfv.text.substring(0, sel.min) + insert.second + tfv.text.substring(sel.max)
            tfv = TextFieldValue(t, TextRange(sel.min + insert.second.length))
            lastEmitted = t
            onChange(t)
            onInsertDone()
        }
    }
    val lines = tfv.text.count { it == '\n' } + 1
    val numbers = remember(lines) { (1..lines).joinToString("\n") }
    val transform = remember { VerilogTransformation() }
    val style = TextStyle(fontFamily = MONO, fontSize = 13.sp, lineHeight = 19.sp, color = Ink)
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        Row(Modifier.defaultMinSize(minWidth = 360.dp, minHeight = 200.dp)) {
            Text(
                numbers, style = style.copy(color = Muted), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(38.dp).background(Color(0xFFEFF4F1)).padding(end = 6.dp, top = 8.dp, bottom = 8.dp)
            )
            BasicTextField(
                value = tfv,
                onValueChange = { new ->
                    var nv = new
                    val old = tfv
                    val p = new.selection.start
                    if (new.selection.collapsed && new.text.length == old.text.length + 1 && p > 0 && new.text[p - 1] == '\n') {
                        val prevStart = new.text.lastIndexOf('\n', p - 2) + 1
                        val prev = new.text.substring(prevStart, p - 1)
                        var indent = prev.takeWhile { it == ' ' }
                        if (INDENT_MORE.containsMatchIn(prev.trim())) indent += "  "
                        if (indent.isNotEmpty()) nv = TextFieldValue(new.text.substring(0, p) + indent + new.text.substring(p), TextRange(p + indent.length))
                    }
                    tfv = nv
                    lastEmitted = nv.text
                    onChange(nv.text)
                },
                textStyle = style,
                cursorBrush = SolidColor(Forest),
                visualTransformation = transform,
                modifier = Modifier.defaultMinSize(minWidth = 320.dp).padding(8.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ console

@Composable
private fun ConsoleView(lines: List<com.varunkb.learnmateai.ConsoleLine>) {
    if (lines.isEmpty()) {
        Text("Press Compile to check the code or Run to simulate it. Output from \$display appears here.", color = Muted, fontSize = 14.sp)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Column {
                lines.forEach {
                    Text(
                        it.text, fontFamily = MONO, fontSize = 13.sp, lineHeight = 18.sp,
                        color = when (it.kind) { 1 -> ErrorRed; 2 -> Warn; 3 -> Forest; else -> Ink }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ waveform

private fun busLabel(bits: String): String {
    if (bits.contains('x')) return if (bits.all { it == 'x' }) "x" else "x?"
    val n = bits.toULongOrNull(2) ?: return bits
    return n.toString(16).uppercase()
}

@Composable
private fun WaveView(wave: Waveform?) {
    if (wave == null) {
        Text("Run a design that has a testbench to see its signals here.", color = Muted, fontSize = 14.sp)
        return
    }
    val ctx = LocalContext.current
    var factor by remember(wave) { mutableFloatStateOf(1f) }
    val signals = wave.signals.take(48)
    val rowH = 30.dp
    val axisH = 20.dp
    val labelW = 96.dp
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0 – ${wave.endTime}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            SmallBtn("−") { factor = (factor / 1.6f).coerceAtLeast(0.25f) }
            SmallBtn("Fit") { factor = 1f }
            SmallBtn("+") { factor = (factor * 1.6f).coerceAtMost(60f) }
            SmallBtn("VCD") {
                val vcd = VcdWriter.write(wave)
                val text = if (vcd.length > 300_000) vcd.substring(0, 300_000) + "\n" else vcd
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, "LearnMate waveform.vcd") }
                ctx.startActivity(Intent.createChooser(i, "Share VCD"))
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availablePx = with(density) { (maxWidth - labelW).toPx() }.coerceAtLeast(120f)
            val end = maxOf(wave.endTime, 1L).toFloat()
            val fit = availablePx / end
            val pxPerTick = (fit * factor).coerceAtMost(60000f / end)
            val totalPx = maxOf(end * pxPerTick, availablePx)
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.width(labelW)) {
                    Box(Modifier.height(axisH))
                    signals.forEach { s ->
                        Box(Modifier.height(rowH), contentAlignment = Alignment.CenterStart) {
                            Text(s.name.substringAfter('.'), color = Ink, fontSize = 11.sp, fontFamily = MONO, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(with(density) { totalPx.toDp() }).height(axisH + rowH * signals.size)) {
                        val aH = axisH.toPx(); val rH = rowH.toPx()
                        // time axis
                        val steps = listOf(1L, 2L, 5L, 10L, 20L, 50L, 100L, 200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L, 50000L, 100000L, 200000L, 500000L)
                        val step = steps.firstOrNull { it * pxPerTick >= 60f } ?: 1000000L
                        var t = 0L
                        while (t <= wave.endTime) {
                            val x = t * pxPerTick
                            drawLine(Color(0xFFD5E0D9), Offset(x, aH), Offset(x, size.height), 1f)
                            drawText(measurer, t.toString(), Offset(x + 2f, 0f), TextStyle(fontSize = 10.sp, color = Muted))
                            t += step
                        }
                        signals.forEachIndexed { row, s ->
                            val top = aH + row * rH + 4f
                            val bot = aH + (row + 1) * rH - 4f
                            val mid = (top + bot) / 2
                            s.changes.forEachIndexed { i, ch ->
                                val x0 = ch.time * pxPerTick
                                val x1 = (s.changes.getOrNull(i + 1)?.time ?: wave.endTime) * pxPerTick
                                if (s.width == 1) {
                                    val v = ch.bits[0]
                                    if (v == 'x') drawRect(Color(0x33B3261E), Offset(x0, top), Size(maxOf(x1 - x0, 1f), bot - top))
                                    val y = if (v == '1') top else bot
                                    drawLine(if (v == 'x') ErrorRed else Forest, Offset(x0, if (v == 'x') mid else y), Offset(x1, if (v == 'x') mid else y), 3f)
                                    val prev = s.changes.getOrNull(i - 1)
                                    if (prev != null) drawLine(Forest, Offset(x0, top), Offset(x0, bot), 2f)
                                } else {
                                    val col = if (ch.bits.contains('x')) ErrorRed else Forest
                                    val inset = minOf(4f, (x1 - x0) / 3f)
                                    drawLine(col, Offset(x0 + inset, top), Offset(x1 - inset, top), 2.5f)
                                    drawLine(col, Offset(x0 + inset, bot), Offset(x1 - inset, bot), 2.5f)
                                    drawLine(col, Offset(x0, mid), Offset(x0 + inset, top), 2f)
                                    drawLine(col, Offset(x0, mid), Offset(x0 + inset, bot), 2f)
                                    drawLine(col, Offset(x1, mid), Offset(x1 - inset, top), 2f)
                                    drawLine(col, Offset(x1, mid), Offset(x1 - inset, bot), 2f)
                                    val label = busLabel(ch.bits)
                                    val m = measurer.measure(label, TextStyle(fontSize = 10.sp, color = Ink, fontFamily = MONO))
                                    if (x1 - x0 > m.size.width + 10f) drawText(m, topLeft = Offset(x0 + (x1 - x0 - m.size.width) / 2, mid - m.size.height / 2))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (wave.truncated) Text("Waveform recording stopped at the change limit.", color = Warn, fontSize = 12.sp)
    }
}
