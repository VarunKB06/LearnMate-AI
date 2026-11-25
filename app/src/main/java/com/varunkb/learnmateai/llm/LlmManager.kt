package com.varunkb.learnmateai.llm

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class LlmStatus { NoModel, Idle, Loading, Ready, Generating, Error }

data class LlmState(
    val status: LlmStatus = LlmStatus.NoModel,
    val modelPath: String? = null,
    val tokenizerPath: String? = null,
    val modelSizeMb: Long = 0,
    val message: String = "No Llama model installed yet.",
    val loadMs: Long = 0,
    val last: GenResult? = null
) {
    val installed: Boolean get() = modelPath != null
}

/**
 * Owns the single on-device Llama model: finds the files, loads the model on demand, runs one
 * generation at a time on a dedicated thread, and lets the UI cancel or unload it.
 * Failures never throw into the UI; they become [LlmState.message] and a null result, and the
 * caller falls back to the rule-based tutor.
 */
class LlmManager(
    private val context: Context,
    private val backendFactory: (String, String) -> LlmBackend = { m, t -> ExecuTorchBackend(m, t) }
) {
    private val _state = MutableStateFlow(LlmState())
    val state: StateFlow<LlmState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(null, r, "llama-ondevice", 16L shl 20) }
        .asCoroutineDispatcher()

    @Volatile private var backend: LlmBackend? = null
    @Volatile private var loadedFor: String? = null

    val isInstalled: Boolean get() = _state.value.installed

    /** Re-scans the model folders. Does not load anything. */
    fun refresh() {
        val found = ModelStore.find(context)
        val cur = _state.value
        if (found == null) {
            backend?.let { runCatching { it.close() } }
            backend = null
            loadedFor = null
            _state.value = LlmState(message = "No Llama model installed yet. Open the Model screen for setup steps.")
            return
        }
        val keepRunning = cur.status == LlmStatus.Ready || cur.status == LlmStatus.Generating || cur.status == LlmStatus.Loading
        _state.value = cur.copy(
            status = if (keepRunning && loadedFor == found.model.absolutePath) cur.status else LlmStatus.Idle,
            modelPath = found.model.absolutePath,
            tokenizerPath = found.tokenizer.absolutePath,
            modelSizeMb = found.model.length() / (1024 * 1024),
            message = if (keepRunning) cur.message else "Model files found (${found.model.name}). It loads when first used."
        )
    }

    private suspend fun loadLocked(): Boolean {
        val cur = _state.value
        val model = cur.modelPath ?: return false
        val tokenizer = cur.tokenizerPath ?: return false
        if (backend != null && loadedFor == model) return true
        backend?.let { runCatching { it.close() } }
        backend = null
        _state.value = cur.copy(status = LlmStatus.Loading, message = "Loading ${model.substringAfterLast('/')}…")
        return try {
            val t0 = SystemClock.elapsedRealtime()
            val b = withContext(dispatcher) { backendFactory(model, tokenizer).also { it.load() } }
            backend = b
            loadedFor = model
            val ms = SystemClock.elapsedRealtime() - t0
            _state.value = _state.value.copy(status = LlmStatus.Ready, loadMs = ms, message = "Model ready (loaded in ${ms} ms).")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.value = _state.value.copy(status = LlmStatus.Error, message = "Could not load the model: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    suspend fun load(): Boolean = mutex.withLock { loadLocked() }

    /** Generates an answer for [prompt], or returns null (with the reason in [state]) if that is not possible. */
    suspend fun generate(prompt: String, maxNewTokens: Int): GenResult? = mutex.withLock {
        if (!loadLocked()) return@withLock null
        val b = backend ?: return@withLock null
        val seqLen = minOf(LlmConfig.MAX_SEQ_LEN, LlamaPrompts.estimateTokens(prompt) + maxNewTokens)
        _state.value = _state.value.copy(status = LlmStatus.Generating, message = "Llama is generating…")
        try {
            val res = withContext(dispatcher) { b.generate(prompt, seqLen) }
            _state.value = _state.value.copy(
                status = LlmStatus.Ready, last = res,
                message = if (res.cancelled) "Stopped." else "Last answer: ${res.summary()}"
            )
            res
        } catch (e: CancellationException) {
            runCatching { b.stop() }
            throw e
        } catch (t: Throwable) {
            _state.value = _state.value.copy(status = LlmStatus.Error, message = "Generation failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /** Stops a running generation. The partial text is discarded by the caller. */
    fun cancel() {
        backend?.stop()
    }

    /** Frees the model memory. Files stay on disk. */
    suspend fun unload() = mutex.withLock {
        backend?.let { runCatching { it.close() } }
        backend = null
        loadedFor = null
        refresh()
    }

    /** Short fixed prompt used by the Model screen to check the model and record speed. */
    suspend fun selfTest(): String {
        val prompt = LlamaPrompts.chat(
            "You are a concise electronics tutor. Answer in at most two sentences.",
            "What is a flip-flop in digital logic?"
        )
        val res = generate(prompt, maxNewTokens = 80)
        val s = _state.value
        return buildString {
            appendLine("Llama on-device self-test")
            appendLine("Model: ${s.modelPath?.substringAfterLast('/')} (${s.modelSizeMb} MB)")
            appendLine("Runtime: ExecuTorch (executorch-android), XNNPACK CPU")
            appendLine("Load time: ${s.loadMs} ms")
            if (res == null) {
                appendLine("Result: FAILED - ${s.message}")
            } else {
                appendLine("Result: ${res.summary()}")
                appendLine("Prompt tokens (estimate): ${LlamaPrompts.estimateTokens(prompt)}")
                appendLine("Answer: ${res.text}")
                res.rawStats?.let { appendLine("ExecuTorch stats: $it") }
            }
        }
    }
}
