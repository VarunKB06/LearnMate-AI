package com.varunkb.learnmateai.domain

import kotlin.math.ceil

/** Small statistics helpers used by the on-device benchmark and its tests. */
object Stats {
    /** Nearest-rank percentile; [p] in 0..100. Returns 0.0 for an empty list. */
    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size

    fun summary(label: String, millis: List<Double>): String =
        "%s: n=%d mean=%.3f ms p50=%.3f ms p95=%.3f ms max=%.3f ms".format(
            label, millis.size, mean(millis), percentile(millis, 50.0), percentile(millis, 95.0),
            millis.maxOrNull() ?: 0.0
        )
}
