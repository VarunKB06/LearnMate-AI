package com.varunkb.learnmateai.domain

enum class ReplyKind { Lesson, Progress, StudyPlan, Greeting, About, QuizHelp, NoMatch, Empty, Compare, Practice, Code, Diagnostic, Simulation, FollowUp }

data class TutorReply(
    val text: String,
    val kind: ReplyKind,
    val sourceTitle: String? = null,
    /** idf-weighted query coverage 0..100 for lesson answers, null for other kinds. */
    val confidence: Int? = null,
    /** Lesson this answer came from, so the chat can open it and follow-ups know the topic. */
    val lessonId: String? = null
)

data class LearnerContext(
    val name: String,
    val subjectId: String,
    val subjectTitle: String,
    val attempts: Int,
    val accuracyPercent: Int,
    val mastery: List<TopicMastery>,
    /** Lesson of the previous tutor answer (for follow-ups like "give an example"). */
    val lastLessonId: String? = null
)

interface TutorEngine {
    fun answer(question: String, context: LearnerContext): TutorReply
}

/**
 * Offline retrieval tutor. It does NOT generate free text: it finds the best matching lesson in
 * the bundled knowledge pack with BM25, answers from that lesson verbatim, and says so when it
 * has no good match. Progress and study-plan questions are answered from the learner's
 * Bayesian-Knowledge-Tracing mastery estimates.
 */
class RetrievalTutorEngine(
    private val index: Bm25Index,
    private val lessons: List<Lesson>
) : TutorEngine {

    companion object {
        const val MIN_COVERAGE = 0.50
        private val GREETING_WORDS = setOf("hello", "hi", "hey", "thanks", "thank", "good", "morning", "evening")
    }

    override fun answer(question: String, context: LearnerContext): TutorReply {
        val q = question.trim()
        if (q.isEmpty()) {
            return TutorReply("Please type a question first, for example \"Explain pointers\" or \"Show my progress\".", ReplyKind.Empty)
        }
        val lower = q.lowercase()
        skillAnswer(q, lower, context)?.let { return it }
        val words = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

        if (words.isNotEmpty() && words.all { it in GREETING_WORDS }) {
            return TutorReply(
                "Hello, ${context.name.ifBlank { "learner" }}! I can explain ${context.subjectTitle} concepts from my offline lessons " +
                    "or review your quiz progress. What would you like to do?",
                ReplyKind.Greeting
            )
        }
        if (Regex("\\b(llm|chatgpt|gpt|generative|neural|language model|are you (an )?ai|how do you work|what are you|internet|offline|network)\\b").containsMatchIn(lower)) {
            return TutorReply(aboutText(), ReplyKind.About)
        }
        if (Regex("\\b(progress|score|scores|accuracy|performance|how am i doing|history|my results)\\b").containsMatchIn(lower)) {
            return progress(context)
        }
        if (Regex("\\b(weak|weakest|revise|revision|improve|study plan|what should i (study|learn|revise)|which topic|focus on|recommend)\\b").containsMatchIn(lower)) {
            return studyPlan(context)
        }
        if (Regex("\\b(how (do|can) i (start|take)|start a quiz|take a quiz|practice quiz)\\b").containsMatchIn(lower)) {
            return TutorReply(
                "Open the dashboard and tap \"Start a practice quiz\". Each quiz has 10 questions picked adaptively from your " +
                    "active subject, favouring topics you have not mastered yet. Results update your mastery estimates.",
                ReplyKind.QuizHelp
            )
        }
        return lessonAnswer(q, context)
    }

    private fun lessonAnswer(q: String, context: LearnerContext): TutorReply {
        val hits = index.search(q, preferSubject = context.subjectId, limit = 3)
        val best = hits.firstOrNull()
        if (best == null || best.coverage < MIN_COVERAGE) {
            val topics = lessons.filter { it.subjectId == context.subjectId }.map { it.title }.take(8)
            return TutorReply(
                "I don't have a reliable answer for that in my offline ${context.subjectTitle} lessons. " +
                    "I only answer from a fixed lesson pack and I never guess. Try one of these: ${topics.joinToString(", ")}.",
                ReplyKind.NoMatch
            )
        }
        val l = best.lesson
        val sb = StringBuilder(l.body)
        if (!l.example.isNullOrBlank()) sb.append("\n\nExample:\n").append(l.example)
        val weak = context.mastery.filter { it.total > 0 && it.topic == l.topic && it.pKnown < 0.6 }
        if (weak.isNotEmpty()) {
            sb.append("\n\nThis is one of your weaker topics (${weak.first().masteryPercent}% estimated mastery). A practice quiz will include more of it.")
        }
        val related = hits.drop(1).filter { it.coverage >= MIN_COVERAGE }.map { it.lesson.title }
        if (related.isNotEmpty()) sb.append("\n\nRelated lessons: ${related.joinToString(", ")}.")
        return TutorReply(sb.toString(), ReplyKind.Lesson, l.title, (best.coverage * 100).toInt().coerceIn(0, 100), l.id)
    }

    private val byId: Map<String, Lesson> = lessons.associateBy { it.id }

    private fun find(topic: String, c: LearnerContext): Lesson? {
        val hit = index.search(topic, preferSubject = c.subjectId, limit = 1).firstOrNull()
        return hit?.takeIf { it.coverage >= MIN_COVERAGE }?.lesson
    }

    private fun skillAnswer(q: String, lower: String, c: LearnerContext): TutorReply? {
        // Pasted Verilog source.
        if (Regex("\\bmodule\\b").containsMatchIn(lower) && lower.contains("endmodule") ||
            (lower.contains("explain") && Regex("\\b(assign|always)\\b").containsMatchIn(lower) && q.contains(";"))
        ) return TutorReply(TutorSkills.explainVerilog(q), ReplyKind.Code)
        // Pasted compiler diagnostic.
        if (TutorSkills.looksLikeDiagnostic(q)) return TutorReply(TutorSkills.explainDiagnostic(q), ReplyKind.Diagnostic)
        // Simulation output.
        if (Regex("\\b(explain|understand|read|mean)\\b.*\\b(simulation|output|console|waveform|result)\\b").containsMatchIn(lower) ||
            lower.startsWith("simulation output")) {
            val body = q.substringAfter(":", "").trim()
            return TutorReply(TutorSkills.explainSimulation(body), ReplyKind.Simulation)
        }
        // Compare two concepts.
        val cmp = Regex("(?:difference between|compare)\\s+(.+?)\\s+(?:and|with|vs\\.?|versus)\\s+(.+)").find(lower)
            ?: Regex("^(.+?)\\s+(?:vs\\.?|versus)\\s+(.+)$").find(lower)
        if (cmp != null) {
            val a = find(cmp.groupValues[1].trim().trimEnd('?'), c)
            val b = find(cmp.groupValues[2].trim().trimEnd('?'), c)
            if (a != null && b != null && a.id != b.id) {
                return TutorReply(TutorSkills.compare(a, b), ReplyKind.Compare, "${a.title} / ${b.title}", null, a.id)
            }
            if (a != null && b != null) return null
            return TutorReply("I could not find both concepts in my offline lessons, so I will not guess a comparison. Try asking about each one separately.", ReplyKind.NoMatch)
        }
        // Practice questions.
        if (Regex("\\b(practice questions?|quiz me|test me|give me questions|exercises?)\\b").containsMatchIn(lower)) {
            val topic = lower.replace(Regex("\\b(give me|some|practice questions?|quiz me|test me|on|about|questions|exercises?|for|me|please|more)\\b"), " ").trim()
            val l = (if (topic.isNotBlank()) find(topic, c) else null) ?: c.lastLessonId?.let { byId[it] }
            return if (l != null) TutorReply(TutorSkills.practice(l), ReplyKind.Practice, l.title, null, l.id)
            else TutorReply("Tell me the topic, for example \"practice questions on pointers\", or ask about a lesson first.", ReplyKind.NoMatch)
        }
        // Depth requests and follow-ups.
        val depth = when {
            Regex("\\b(simply|simple|simpler|eli5|beginner|easy|easier|in short)\\b").containsMatchIn(lower) -> TutorSkills.Depth.Simple
            Regex("\\b(advanced|in depth|deeper|detail|detailed)\\b").containsMatchIn(lower) -> TutorSkills.Depth.Advanced
            Regex("\\bexample\\b").containsMatchIn(lower) -> TutorSkills.Depth.Example
            else -> null
        }
        if (depth != null) {
            val topic = lower.replace(Regex("\\b(explain|simply|simple|simpler|eli5|beginner|easy|easier|in short|advanced|in depth|deeper|detail|detailed|more|give|me|an|a|the|example|show|of|about|this|it|please|again|in|terms|words|to)\\b"), " ").trim()
            val l = (if (topic.isNotBlank()) find(topic, c) else null) ?: c.lastLessonId?.let { byId[it] }
            if (l != null) return TutorReply(TutorSkills.renderLesson(l, depth), ReplyKind.FollowUp, l.title, null, l.id)
            if (topic.isBlank()) return TutorReply("Ask me about a topic first, then I can simplify it or give an example.", ReplyKind.NoMatch)
        }
        return null
    }

    private fun progress(c: LearnerContext): TutorReply {
        val seen = c.mastery.filter { it.total > 0 }
        if (c.attempts == 0 || seen.isEmpty()) {
            return TutorReply("You have not completed a ${c.subjectTitle} quiz yet. Take one and I will track your accuracy and topic mastery.", ReplyKind.Progress)
        }
        val strongest = seen.maxByOrNull { it.pKnown }!!
        val weakest = seen.minByOrNull { it.pKnown }!!
        val text = "In ${c.subjectTitle} you have completed ${c.attempts} quiz attempt(s) with ${c.accuracyPercent}% overall accuracy. " +
            "Strongest topic: ${strongest.topic} (${strongest.masteryPercent}% estimated mastery). " +
            "Weakest topic: ${weakest.topic} (${weakest.masteryPercent}%)."
        return TutorReply(text, ReplyKind.Progress)
    }

    private fun studyPlan(c: LearnerContext): TutorReply {
        val rec = Recommender.recommend(c.subjectTitle, c.mastery, c.attempts)
        val plan = if (rec.focusTopic != null) {
            "\n\nA 20-minute plan: 5 min re-read the ${rec.focusTopic} lesson (ask me to explain it), 5 min study the example, " +
                "5 min answer practice questions without notes, 5 min explain the idea aloud. Then take another quiz."
        } else ""
        return TutorReply("${rec.title}. ${rec.detail}$plan", ReplyKind.StudyPlan)
    }

    private fun aboutText(): String =
        "I am an offline retrieval tutor, not a generative AI or LLM. Everything runs on this phone and the app has no internet permission. " +
            "Lessons are searched with BM25 ranking, and your mastery is estimated with Bayesian Knowledge Tracing. " +
            "I can only answer topics in my built-in lesson pack and I will say so when I do not know."
}
