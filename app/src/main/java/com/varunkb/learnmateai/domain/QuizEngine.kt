package com.varunkb.learnmateai.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Adaptive quiz selection. Questions are sampled without replacement with weights that
 * favour (a) topics the learner has not mastered, (b) questions not seen recently and
 * (c) a difficulty that matches the estimated mastery. A per-topic cap keeps quizzes varied.
 */
object QuizSelector {
    const val DEFAULT_SIZE = 10
    private val DEFAULT_P = BktParams().pInit

    fun select(
        questions: List<Question>,
        mastery: Map<String, TopicMastery>,
        recentIds: Set<String>,
        count: Int = DEFAULT_SIZE,
        random: Random = Random.Default
    ): List<QuizItem> {
        if (questions.isEmpty()) return emptyList()
        val n = count.coerceIn(1, questions.size)
        val topics = questions.map { it.topic }.distinct().size
        val cap = maxOf(2, ceil(n.toDouble() / topics).toInt() + 1)

        val remaining = questions.toMutableList()
        val chosen = ArrayList<Question>(n)
        val perTopic = HashMap<String, Int>()

        while (chosen.size < n && remaining.isNotEmpty()) {
            var candidates = remaining.filter { (perTopic[it.topic] ?: 0) < cap }
            if (candidates.isEmpty()) candidates = remaining
            val weights = candidates.map { weight(it, mastery[it.topic]?.pKnown ?: DEFAULT_P, it.id in recentIds) }
            val pick = candidates[weightedIndex(weights, random)]
            chosen.add(pick)
            remaining.remove(pick)
            perTopic[pick.topic] = (perTopic[pick.topic] ?: 0) + 1
        }
        chosen.shuffle(random)
        return chosen.map { toItem(it, random) }
    }

    internal fun weight(q: Question, pKnown: Double, recentlySeen: Boolean): Double {
        val topicWeight = 0.5 + 2.5 * (1.0 - pKnown)
        val target = when {
            pKnown < 0.45 -> 1
            pKnown < 0.75 -> 2
            else -> 3
        }
        val difficultyFactor = maxOf(0.4, 1.0 - 0.3 * abs(q.difficulty - target))
        val recency = if (recentlySeen) 0.15 else 1.0
        return topicWeight * difficultyFactor * recency
    }

    private fun weightedIndex(weights: List<Double>, random: Random): Int {
        val total = weights.sum()
        var r = random.nextDouble() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0) return i
        }
        return weights.lastIndex
    }

    fun toItem(q: Question, random: Random): QuizItem {
        val order = q.choices.indices.shuffled(random)
        val choices = order.map { q.choices[it] }
        return QuizItem(q, choices, order.indexOf(q.answerIndex))
    }
}

object QuizScoring {
    /**
     * Scores a finished quiz and updates per-topic mastery with BKT.
     * [chosen] holds the selected index per item (-1 = unanswered, counted as wrong).
     */
    fun score(
        subjectId: String,
        items: List<QuizItem>,
        chosen: List<Int>,
        prior: Map<String, TopicMastery>
    ): QuizOutcome {
        require(items.size == chosen.size) { "items and answers must have the same length" }
        val updated = HashMap<String, TopicMastery>()
        val topicScores = LinkedHashMap<String, Pair<Int, Int>>()
        var correct = 0
        items.forEachIndexed { i, item ->
            val ok = chosen[i] == item.correctIndex
            if (ok) correct++
            val topic = item.question.topic
            val old = updated[topic] ?: prior[topic] ?: TopicMastery(subjectId, topic, BktParams().pInit, 0, 0)
            updated[topic] = old.copy(
                pKnown = Bkt.update(old.pKnown, ok),
                correct = old.correct + if (ok) 1 else 0,
                total = old.total + 1
            )
            val ts = topicScores[topic] ?: (0 to 0)
            topicScores[topic] = (ts.first + if (ok) 1 else 0) to (ts.second + 1)
        }
        return QuizOutcome(correct, items.size, topicScores, updated.values.toList())
    }
}

data class Recommendation(val title: String, val detail: String, val focusTopic: String?)

object Recommender {
    fun recommend(subjectTitle: String, mastery: List<TopicMastery>, attemptCount: Int): Recommendation {
        val seen = mastery.filter { it.total > 0 }
        if (attemptCount == 0 || seen.isEmpty()) {
            return Recommendation(
                "Take your first $subjectTitle quiz",
                "A short diagnostic quiz lets LearnMate estimate which topics you already know.",
                null
            )
        }
        val weakest = seen.minByOrNull { it.pKnown }!!
        return if (weakest.pKnown >= 0.85) {
            Recommendation(
                "Try a harder challenge",
                "All attempted topics are above 85% estimated mastery. Take another quiz to reach new topics.",
                null
            )
        } else {
            Recommendation(
                "Revise ${weakest.topic}",
                "Estimated mastery is ${weakest.masteryPercent}% (${weakest.correct}/${weakest.total} correct). " +
                    "Your next quiz will include more ${weakest.topic} questions.",
                weakest.topic
            )
        }
    }

    fun accuracy(mastery: List<TopicMastery>): Int {
        val total = mastery.sumOf { it.total }
        return if (total == 0) 0 else mastery.sumOf { it.correct } * 100 / total
    }
}
