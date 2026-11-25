package com.varunkb.learnmateai.llm

import android.os.SystemClock
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Runs a Llama model exported to ExecuTorch (.pte) through the executorch-android AAR.
 *
 * This is the ONLY file that touches the ExecuTorch API. The result callback is created with a
 * java.lang.reflect.Proxy so the code compiles against any 1.x version of [LlmCallback], whether or
 * not that version declares onStats/onError as abstract or default methods.
 */
class ExecuTorchBackend(
    private val modelPath: String,
    private val tokenizerPath: String,
    private val temperature: Float = LlmConfig.TEMPERATURE
) : LlmBackend {

    @Volatile
    private var module: LlmModule? = null

    override fun load() {
        val m = LlmModule(modelPath, tokenizerPath, temperature)
        m.load() // return value differs between releases; failures also surface on the first generate()
        module = m
    }

    override fun generate(prompt: String, seqLen: Int): GenResult {
        val m = module ?: throw IllegalStateException("Model is not loaded")
        val raw = StringBuilder()
        var tokens = 0
        var firstAt = 0L
        var stats: String? = null
        var errorText: String? = null
        var stopped = false
        cancelRequested = false
        val t0 = SystemClock.elapsedRealtime()

        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "onResult" -> {
                    val piece = args?.getOrNull(0) as? String
                    if (piece != null) {
                        if (tokens == 0) firstAt = SystemClock.elapsedRealtime()
                        tokens++
                        raw.append(piece)
                        if (piece.contains("<|eot_id|>") || piece.contains("<|end_of_text|>")) {
                            stopped = true
                            m.stop()
                        }
                    }
                    null
                }
                "onStats" -> { stats = args?.getOrNull(0) as? String; null }
                "onError" -> { errorText = args?.getOrNull(1)?.toString() ?: args?.getOrNull(0)?.toString(); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "LearnMateLlmCallback"
                else -> null
            }
        }
        val callback = Proxy.newProxyInstance(
            LlmCallback::class.java.classLoader, arrayOf<Class<*>>(LlmCallback::class.java), handler
        ) as LlmCallback

        val echoDisabled = invokeGenerate(m, prompt, seqLen, callback)
        val end = SystemClock.elapsedRealtime()

        val text = LlamaPrompts.cleanOutput(raw.toString(), prompt)
        if (text.isEmpty() && errorText != null) throw IllegalStateException("ExecuTorch error: $errorText")
        // If the runtime echoed the prompt, do not count its tokens as generated tokens.
        val generated = if (echoDisabled) tokens else maxOf(1, tokens - LlamaPrompts.estimateTokens(prompt) / 2)
        return GenResult(
            text = text,
            tokens = generated,
            firstTokenMs = if (firstAt == 0L) end - t0 else firstAt - t0,
            totalMs = end - t0,
            cancelled = cancelRequested && !stopped,
            rawStats = stats
        ).also { cancelRequested = false }
    }

    @Volatile
    private var cancelRequested = false

    /** Returns true when the 4-argument overload with echo=false was used. */
    private fun invokeGenerate(m: LlmModule, prompt: String, seqLen: Int, cb: LlmCallback): Boolean {
        val withEcho = try {
            LlmModule::class.java.getMethod(
                "generate", String::class.java, Int::class.javaPrimitiveType, LlmCallback::class.java, Boolean::class.javaPrimitiveType
            )
        } catch (e: NoSuchMethodException) {
            null
        }
        if (withEcho == null) {
            m.generate(prompt, seqLen, cb)
            return false
        }
        try {
            withEcho.invoke(m, prompt, seqLen, cb, false)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
        return true
    }

    override fun stop() {
        cancelRequested = true
        module?.stop()
    }

    override fun close() {
        val m = module ?: return
        module = null
        try { m.stop() } catch (_: Throwable) { }
        // Best effort: release native memory now if this release exposes it.
        for (name in listOf("resetNative", "close", "destroy")) {
            try {
                LlmModule::class.java.getMethod(name).invoke(m)
                break
            } catch (_: Throwable) { }
        }
    }
}
