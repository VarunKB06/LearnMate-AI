package com.varunkb.learnmateai.llm

/** Settings that decide how much context the on-device Llama model is given. */
object LlmConfig {
    /**
     * Upper bound for prompt + answer tokens passed to ExecuTorch as the sequence length.
     * Export the model with max_seq_length / max_context_length of at least this value (see docs/LLAMA_EXECUTORCH_SETUP.md).
     */
    const val MAX_SEQ_LEN = 1024

    /** Sampling temperature. Low, because the model is used for grounded answers and JSON, not creative writing. */
    const val TEMPERATURE = 0.3f

    /** Maximum answer length in tokens for each kind of request. */
    const val ANSWER_TOKENS = 220
    const val GOAL_TOKENS = 170
    const val COACH_TOKENS = 170
}

/** Result of one generation, with the timings shown on the Model and Diagnostics screens. */
data class GenResult(
    val text: String,
    val tokens: Int,
    val firstTokenMs: Long,
    val totalMs: Long,
    val cancelled: Boolean,
    val rawStats: String? = null
) {
    /** Decode speed after the first token, which excludes the prompt-processing time. */
    val tokensPerSecond: Double
        get() {
            val decodeMs = totalMs - firstTokenMs
            return if (tokens > 1 && decodeMs > 0) (tokens - 1) * 1000.0 / decodeMs else 0.0
        }

    fun summary(): String =
        "%d tokens, first token %d ms, total %.1f s, %.2f tokens/s%s".format(
            tokens, firstTokenMs, totalMs / 1000.0, tokensPerSecond, if (cancelled) " (stopped early)" else ""
        )
}

/** A text-generation engine. The only production implementation is [ExecuTorchBackend]. */
interface LlmBackend {
    /** Loads the model. Throws on failure (missing file, unsupported model, no native library). */
    fun load()

    /** Blocking generation of at most [seqLen] tokens in total (prompt included). Call off the main thread. */
    fun generate(prompt: String, seqLen: Int): GenResult

    /** Asks a running [generate] to stop. Safe to call from another thread. */
    fun stop()

    fun close()
}
