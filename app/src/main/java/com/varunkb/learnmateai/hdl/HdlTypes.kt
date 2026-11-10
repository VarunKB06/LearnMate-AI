package com.varunkb.learnmateai.hdl

/** Public result types of the Verilog simulator. Pure Kotlin, no Android imports. */

data class SourceFile(val name: String, val text: String)

enum class Severity { Error, Warning }

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val hint: String? = null
) {
    fun format(): String {
        val kind = if (severity == Severity.Error) "error" else "warning"
        val loc = if (line > 0) "$file:$line:$column" else file
        return "$loc: $kind: $message" + (hint?.let { "\n    hint: $it" } ?: "")
    }
}

data class CompileResult(val ok: Boolean, val diagnostics: List<Diagnostic>, val topModules: List<String>)

data class WaveChange(val time: Long, val bits: String)

/** One signal's history. [bits] is MSB first using '0', '1' and 'x'. */
data class WaveSignal(val name: String, val width: Int, val changes: List<WaveChange>)

data class Waveform(val endTime: Long, val signals: List<WaveSignal>, val truncated: Boolean)

data class SimLimits(
    val maxTime: Long = 1_000_000,
    val maxSteps: Long = 5_000_000,
    val maxOutputLines: Int = 2000,
    val maxWallMillis: Long = 8000,
    val maxWaveChanges: Int = 100_000
)

data class SimResult(
    val compile: CompileResult,
    val output: List<String>,
    /** "$finish", "event queue empty", "time limit", "step limit", "output limit", "wall-clock limit", "cancelled", "not run" */
    val finishedBy: String,
    val endTime: Long,
    val steps: Long,
    val wallMillis: Long,
    val waveform: Waveform?
)
