package com.varunkb.learnmateai.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Finds and imports the Llama model files. A 1B model is about 1 GB, far too big for the APK,
 * so it is installed separately, in either of two ways:
 *  - adb push into the app's own external folder (see docs/LLAMA_EXECUTORCH_SETUP.md), or
 *  - the "Import" buttons on the Model screen, which copy a picked file into the app's private folder.
 * Nothing is downloaded and the app still has no INTERNET permission.
 */
object ModelStore {
    enum class Kind { Model, Tokenizer }

    data class Found(val model: File, val tokenizer: File)

    fun searchDirs(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir("llm"),
        File(context.filesDir, "llm"),
        File("/data/local/tmp/llama")
    )

    fun importDir(context: Context): File = File(context.filesDir, "llm").also { it.mkdirs() }

    /** First folder that contains both a .pte model and a tokenizer file. */
    fun find(context: Context): Found? {
        for (dir in searchDirs(context)) {
            val files = dir.listFiles() ?: continue
            val model = files.filter { it.isFile && it.canRead() && it.name.endsWith(".pte") }.maxByOrNull { it.length() }
            val tokenizer = files.firstOrNull { it.isFile && it.canRead() && it.name.startsWith("tokenizer") }
            if (model != null && tokenizer != null) return Found(model, tokenizer)
        }
        return null
    }

    fun describeSearch(context: Context): String =
        searchDirs(context).joinToString("\n") { d ->
            val names = d.listFiles()?.filter { it.isFile }?.joinToString { it.name + " (" + it.length() / (1024 * 1024) + " MB)" }
            "${d.absolutePath}: " + (if (names.isNullOrEmpty()) "empty or not readable" else names)
        }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Copies a picked file into the private model folder. Blocking: call from an IO dispatcher. */
    fun copyFromUri(context: Context, uri: Uri, kind: Kind, onProgressMb: (Long) -> Unit): File {
        val picked = displayName(context, uri).orEmpty()
        val name = when (kind) {
            Kind.Model -> if (picked.endsWith(".pte")) picked else "model.pte"
            Kind.Tokenizer -> if (picked.startsWith("tokenizer")) picked else "tokenizer.model"
        }
        val dir = importDir(context)
        val target = File(dir, name)
        val part = File(dir, "$name.part")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected file" }
            part.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgressMb(total / (1024 * 1024))
                }
            }
        }
        if (target.exists()) target.delete()
        check(part.renameTo(target)) { "Could not finish writing $name" }
        return target
    }

    fun deleteImported(context: Context): Int {
        var n = 0
        importDir(context).listFiles()?.forEach { if (it.delete()) n++ }
        return n
    }
}
