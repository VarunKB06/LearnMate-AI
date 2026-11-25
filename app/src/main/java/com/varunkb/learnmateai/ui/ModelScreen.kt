package com.varunkb.learnmateai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunkb.learnmateai.llm.LlmState
import com.varunkb.learnmateai.llm.LlmStatus
import com.varunkb.learnmateai.llm.ModelStore

/** Install, load, test and benchmark the on-device Llama model (ExecuTorch). */
@Composable
fun ModelScreen(
    state: LlmState,
    busy: String?,
    report: String,
    useLlm: Boolean,
    onUseLlm: (Boolean) -> Unit,
    onImport: (Uri, ModelStore.Kind) -> Unit,
    onRescan: () -> Unit,
    onLoad: () -> Unit,
    onTest: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onImport(uri, ModelStore.Kind.Model)
    }
    val pickTokenizer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onImport(uri, ModelStore.Kind.Tokenizer)
    }
    val folder = ModelStore.searchDirs(context).firstOrNull()?.absolutePath ?: "(app external files)/llm"

    AppPage {
        TextButton(onClick = onBack) { Text("← Dashboard", color = Forest, fontWeight = FontWeight.Bold) }
        Heading("On-device Llama", "A Llama model running on this phone through ExecuTorch. No cloud, no internet permission.")

        InfoCard(
            "Status",
            state.status.name,
            state.message +
                (state.modelPath?.let { "\nModel: ${it.substringAfterLast('/')} · ${state.modelSizeMb} MB" } ?: "") +
                (state.tokenizerPath?.let { "\nTokenizer: ${it.substringAfterLast('/')}" } ?: "")
        )

        if (busy != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Forest)
                Text(busy, color = Ink)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Use Llama for tutor answers, goals and coaching", color = Ink, modifier = Modifier.weight(1f))
            Switch(checked = useLlm, onCheckedChange = onUseLlm)
        }

        PrimaryButton("Load model now", enabled = busy == null && state.installed && state.status != LlmStatus.Ready, onClick = onLoad)
        SecondaryButton("Run self-test and measure speed", enabled = busy == null && state.installed, onClick = onTest)
        SecondaryButton("Rescan folders", enabled = busy == null, onClick = onRescan)
        SecondaryButton("Unload from memory", enabled = busy == null && state.status == LlmStatus.Ready, onClick = onUnload)

        if (report.isNotBlank()) {
            SectionLabel("Latest result")
            SelectionContainer {
                Text(report, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        SectionLabel("Install a model")
        InfoCard(
            "Option 1: adb (fastest)",
            "Push the two files",
            "Open this screen once so the folder exists, then on your computer:\n" +
                "adb push llama3_2_1b.pte $folder/\n" +
                "adb push tokenizer.model $folder/\n" +
                "Then tap Rescan folders and Load model now."
        )
        InfoCard(
            "Option 2: import from the phone",
            "Pick the files",
            "Copy the .pte and tokenizer files to the phone (USB, Drive), then pick them here. They are copied into the app's private storage, so keep about 1.5 GB free."
        )
        PrimaryButton("Import model file (.pte)", enabled = busy == null) { pickModel.launch(arrayOf("*/*")) }
        SecondaryButton("Import tokenizer file", enabled = busy == null) { pickTokenizer.launch(arrayOf("*/*")) }
        SecondaryButton("Delete imported files", enabled = busy == null, onClick = onDelete)
        Text(
            "Create the .pte on a computer with ExecuTorch (see docs/LLAMA_EXECUTORCH_SETUP.md). Meta's Llama 3.2 1B Instruct, 8da4w quantised, is the recommended starting point for a 6 GB phone.",
            color = Muted, fontSize = 14.sp
        )
    }
}
