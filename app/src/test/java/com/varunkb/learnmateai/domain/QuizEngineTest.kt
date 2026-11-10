package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BktTest {
    @Test fun correctAnswerRaisesMastery() {
        assertTrue(Bkt.update(0.3, true) > 0.3)
    }

    @Test fun wrongAnswerLowersMasteryWhenAlreadyHigh() {
        assertTrue(Bkt.update(0.8, false) < 0.8)
    }

    @Test fun wrongAnswerLowersPosteriorBeforeLearning() {
        // With pLearn = 0 the update is pure Bayes, so a wrong answer must lower mastery.
        val p = BktParams(pLearn = 0.0)
        assertTrue(Bkt.update(0.5, false, p) < 0.5)
        assertTrue(Bkt.update(0.5, true, p) > 0.5)
    }

    @Test fun matchesHandComputedValue() {
        // pKnown 0.3, correct: 0.3*0.9 / (0.3*0.9 + 0.7*0.25) = 0.27/0.445 = 0.606742
        // then + (1 - 0.606742) * 0.12 = 0.653933
        assertEquals(0.653933, Bkt.update(0.3, true), 1e-5)
    }

    @Test fun staysWithinBounds() {
        var p = 0.5
        repeat(200) { p = Bkt.update(p, true) }
        assertTrue(p <= 0.999)
        repeat(200) { p = Bkt.update(p, false) }
        assertTrue(p >= 0.001)
    }

    @Test fun streakOfCorrectAnswersConverges() {
        assertTrue(Bkt.updateAll(0.3, List(8) { true }) > 0.95)
    }
}

class QuizSelectorTest {
    private val c = TestContent.pack("c")

    @Test fun selectsRequestedCountWithoutDuplicates() {
        val quiz = QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(1))
        assertEquals(10, quiz.size)
        assertEquals(10, quiz.map { it.question.id }.toSet().size)
    }

    @Test fun neverReturnsMoreThanAvailable() {
        val few = c.questions.take(4)
        assertEquals(4, QuizSelector.select(few, emptyMap(), emptySet(), 10, Random(2)).size)
        assertTrue(QuizSelector.select(emptyList(), emptyMap(), emptySet(), 10).isEmpty())
    }

    @Test fun shuffledChoicesKeepTheCorrectAnswer() {
        repeat(50) { seed ->
            QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(seed)).forEach { item ->
                assertEquals(item.question.choices[item.question.answerIndex], item.choices[item.correctIndex])
                assertEquals(item.question.choices.toSet(), item.choices.toSet())
            }
        }
    }

    @Test fun differentSeedsGiveDifferentQuizzes() {
        val a = QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(1)).map { it.question.id }
        val b = QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(99)).map { it.question.id }
        assertFalse(a == b)
    }

    @Test fun weakTopicIsSampledMoreOften() {
        val mastery = c.questions.map { it.topic }.distinct().associateWith {
            TopicMastery("c", it, if (it == "Pointers") 0.05 else 0.95, 5, 5)
        }
        var pointerWeak = 0
        var pointerNeutral = 0
        repeat(300) { seed ->
            pointerWeak += QuizSelector.select(c.questions, mastery, emptySet(), 10, Random(seed)).count { it.question.topic == "Pointers" }
            pointerNeutral += QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(seed)).count { it.question.topic == "Pointers" }
        }
        assertTrue("weak=$pointerWeak neutral=$pointerNeutral", pointerWeak > pointerNeutral)
    }

    @Test fun recentlySeenQuestionsAreDeprioritised() {
        val recent = c.questions.take(20).map { it.id }.toSet()
        var fromRecent = 0
        repeat(200) { seed ->
            fromRecent += QuizSelector.select(c.questions, emptyMap(), recent, 10, Random(seed)).count { it.question.id in recent }
        }
        // 20 of 30 questions are recent; unweighted expectation would be ~6.7 of 10 per quiz.
        assertTrue("avg recent per quiz = ${fromRecent / 200.0}", fromRecent / 200.0 < 5.0)
    }

    @Test fun perTopicCapKeepsQuizzesVaried() {
        repeat(100) { seed ->
            val quiz = QuizSelector.select(c.questions, emptyMap(), emptySet(), 10, Random(seed))
            val maxPerTopic = quiz.groupingBy { it.question.topic }.eachCount().values.max()
            assertTrue("max per topic $maxPerTopic", maxPerTopic <= 3)
        }
    }
}

class QuizScoringTest {
    private fun items(): List<QuizItem> =
        TestContent.pack("c").questions.take(4).map { QuizItem(it, it.choices, it.answerIndex) }

    @Test fun countsCorrectAndUnanswered() {
        val its = items()
        val out = QuizScoring.score("c", its, listOf(its[0].correctIndex, (its[1].correctIndex + 1) % 4, -1, its[3].correctIndex), emptyMap())
        assertEquals(2, out.correct)
        assertEquals(4, out.total)
        assertEquals(50, out.percent)
    }

    @Test fun updatesMasteryAndAccumulatesOnPriorCounts() {
        val its = items()
        val topic = its[0].question.topic
        val prior = mapOf(topic to TopicMastery("c", topic, 0.5, 3, 4))
        val out = QuizScoring.score("c", listOf(its[0]), listOf(its[0].correctIndex), prior)
        val m = out.updatedMastery.first { it.topic == topic }
        assertEquals(4, m.correct)
        assertEquals(5, m.total)
        assertTrue(m.pKnown > 0.5)
    }

    @Test fun topicScoresSumToTotals() {
        val its = items()
        val out = QuizScoring.score("c", its, List(its.size) { its[it].correctIndex }, emptyMap())
        assertEquals(its.size, out.topicScores.values.sumOf { it.second })
        assertEquals(its.size, out.topicScores.values.sumOf { it.first })
    }
}

class RecommenderTest {
    @Test fun firstQuizWhenNoAttempts() {
        val r = Recommender.recommend("C Programming", emptyList(), 0)
        assertTrue(r.title.contains("first"))
        assertEquals(null, r.focusTopic)
    }

    @Test fun recommendsWeakestTopic() {
        val m = listOf(TopicMastery("c", "Arrays", 0.9, 5, 5), TopicMastery("c", "Pointers", 0.3, 1, 5))
        val r = Recommender.recommend("C Programming", m, 2)
        assertEquals("Pointers", r.focusTopic)
        assertTrue(r.title.contains("Pointers"))
    }

    @Test fun suggestsChallengeWhenEverythingIsStrong() {
        val m = listOf(TopicMastery("c", "Arrays", 0.95, 5, 5), TopicMastery("c", "Pointers", 0.9, 5, 5))
        assertTrue(Recommender.recommend("C", m, 3).title.contains("harder"))
    }

    @Test fun accuracyHandlesEmpty() {
        assertEquals(0, Recommender.accuracy(emptyList()))
        assertEquals(60, Recommender.accuracy(listOf(TopicMastery("c", "A", 0.5, 3, 5))))
    }
}
