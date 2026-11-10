package com.varunkb.learnmateai.domain

/**
 * Bayesian Knowledge Tracing (Corbett & Anderson, 1994): a two-state hidden Markov model that
 * estimates the probability that a learner has mastered a topic from their sequence of
 * right/wrong answers. This is a small, classical probabilistic student model. It runs on-device
 * in microseconds and is the core of LearnMate's adaptive behaviour.
 *
 * Parameters are fixed defaults chosen for 4-option multiple-choice questions (guess = 0.25).
 * They are not fitted to real learner data.
 */
data class BktParams(
    val pInit: Double = 0.30,
    val pLearn: Double = 0.12,
    val pSlip: Double = 0.10,
    val pGuess: Double = 0.25
)

object Bkt {
    private const val MIN_P = 0.001
    private const val MAX_P = 0.999

    /** Posterior mastery after observing one answer, including the learning transition. */
    fun update(pKnown: Double, correct: Boolean, p: BktParams = BktParams()): Double {
        val posterior = if (correct) {
            val known = pKnown * (1 - p.pSlip)
            known / (known + (1 - pKnown) * p.pGuess)
        } else {
            val known = pKnown * p.pSlip
            known / (known + (1 - pKnown) * (1 - p.pGuess))
        }
        val next = posterior + (1 - posterior) * p.pLearn
        return next.coerceIn(MIN_P, MAX_P)
    }

    fun updateAll(start: Double, results: List<Boolean>, p: BktParams = BktParams()): Double =
        results.fold(start) { acc, r -> update(acc, r, p) }
}
