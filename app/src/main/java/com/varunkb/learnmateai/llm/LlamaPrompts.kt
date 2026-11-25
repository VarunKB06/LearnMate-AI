package com.varunkb.learnmateai.llm

import com.varunkb.learnmateai.domain.GoalProfile
import com.varunkb.learnmateai.domain.LearnerContext
import com.varunkb.learnmateai.domain.Lesson
import com.varunkb.learnmateai.domain.StudyPlan
import com.varunkb.learnmateai.domain.Subject
import com.varunkb.learnmateai.domain.TopicMastery

/**
 * Prompt construction for Llama 3.x Instruct models. Pure Kotlin so it is unit-tested on the JVM.
 *
 * Design rule: the 1B/3B model never answers from its own memory. Every prompt carries the
 * lesson text, the learner's data or the subject list it must use, and says to stay inside it.
 */
object LlamaPrompts {
    /**
     * Whether the prompt starts with the begin-of-text token. The ExecuTorch Llama demo includes it
     * in the prompt text; if answers look odd or the log shows two BOS tokens, set this to false.
     */
    const val INCLUDE_BOS = true

    private const val BOS = "<|begin_of_text|>"
    private const val EOT = "<|eot_id|>"
    private const val ASSISTANT_HEADER = "<|start_header_id|>assistant<|end_header_id|>"

    /** Llama 3 chat template: one system turn, one user turn, then the open assistant turn. */
    fun chat(system: String, user: String): String =
        (if (INCLUDE_BOS) BOS else "") +
            "<|start_header_id|>system<|end_header_id|>\n\n$system$EOT" +
            "<|start_header_id|>user<|end_header_id|>\n\n$user$EOT" +
            "$ASSISTANT_HEADER\n\n"

    /** Rough token estimate for budgeting (English text is about 4 characters per token; this is deliberately pessimistic). */
    fun estimateTokens(text: String): Int = text.length / 3 + 1

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text.trim() else text.take(max).trimEnd() + " ..."

    private fun weakSummary(mastery: List<TopicMastery>, n: Int = 4): String =
        mastery.filter { it.total >= 3 && it.pKnown < 0.6 }.sortedBy { it.pKnown }.take(n)
            .joinToString("; ") { "${it.topic} ${it.masteryPercent}%" }

    private fun goalLine(goal: GoalProfile?): String =
        if (goal == null) "No goal set." else
            "Goal: ${goal.goalText.take(160)} (${goal.targetType}, ${goal.hoursPerWeek} h/week for ${goal.weeks} weeks, level ${goal.level})."

    /** Concept question answered only from the retrieved lesson. */
    fun groundedTutor(question: String, lesson: Lesson, ctx: LearnerContext, goal: GoalProfile?): String {
        val system = "You are LearnMate, a patient on-device tutor for engineering students. " +
            "Answer ONLY with information from the LESSON below. If the lesson does not contain the answer, " +
            "say you are not sure and tell the student to re-read the lesson. " +
            "Use plain text, at most 140 words, no headings, no markdown."
        val user = buildString {
            append("Student: ").append(ctx.name.ifBlank { "student" }).append(". ").append(goalLine(goal))
            val weak = weakSummary(ctx.mastery, 3)
            if (weak.isNotEmpty()) append(" Weak topics: ").append(weak).append('.')
            append("\n\nLESSON: ").append(lesson.title).append('\n').append(clip(lesson.body, 1100))
            val example = lesson.example
            if (!example.isNullOrBlank()) append("\n\nEXAMPLE:\n").append(clip(example, 450))
            append("\n\nQUESTION: ").append(question.trim().take(300))
        }
        return chat(system, user)
    }

    private val advisoryWords = Regex(
        """\b(career|roadmap|path|pathway|should i|which (subject|topic|course)|what (next|should)|plan|prepare|interview|placement|gate|certif\w*|study|learn next)\b"""
    )

    /** True for questions that ask for guidance rather than for a concept explanation. */
    fun looksAdvisory(question: String): Boolean = advisoryWords.containsMatchIn(question.lowercase())

    /** Personalised guidance built only from the learner's goal, mastery, plan and the subject list. */
    fun advisory(
        question: String,
        ctx: LearnerContext,
        goal: GoalProfile?,
        plan: StudyPlan?,
        subjects: List<Subject>,
        mastery: List<TopicMastery>
    ): String {
        val system = "You are LearnMate, a study advisor inside an offline learning app. " +
            "Use ONLY the learner data and the subject list below. Never invent subjects, lessons or facts. " +
            "Give short numbered steps with a reason for each, at most 120 words, plain text."
        val user = buildString {
            append("Student: ").append(ctx.name.ifBlank { "student" }).append(". ").append(goalLine(goal)).append('\n')
            val weak = weakSummary(mastery)
            append("Weak topics: ").append(if (weak.isEmpty()) "none measured yet" else weak).append('\n')
            val week = plan?.weeks?.firstOrNull()
            if (week != null) {
                append("This week's plan: ").append(week.items.take(6).joinToString("; ") { it.title }).append('\n')
            }
            append("Available subjects: ").append(subjects.joinToString(", ") { it.title }).append("\n\n")
            append("QUESTION: ").append(question.trim().take(300))
        }
        return chat(system, user)
    }

    /** Turns the learner's own words into a JSON goal. The reply is validated by GoalParser.fromLlmJson. */
    fun goalToJson(goalText: String, interests: String, subjects: List<Subject>): String {
        val system = "You convert a learner's goal into JSON. Reply with ONE JSON object and nothing else."
        val user = buildString {
            append("Subjects (id: title):\n")
            subjects.forEach { append(it.id).append(": ").append(it.title).append('\n') }
            append("\nGoal: ").append(goalText.trim().take(300))
            if (interests.isNotBlank()) append("\nInterests: ").append(interests.trim().take(120))
            append(
                "\n\nSchema: {\"target_type\":\"Career|Exam|Certification|Skill|Lifelong\"," +
                    "\"subjects\":[subject ids in the order to study, at most 5]," +
                    "\"interests\":[up to 5 short phrases],\"hours_per_week\":number or null," +
                    "\"weeks\":number or null,\"level\":\"Beginner|Intermediate|Advanced\"}" +
                    "\nUse only ids from the subject list."
            )
        }
        return chat(system, user)
    }

    /** Short motivational and practical note about the current plan. */
    fun coach(goal: GoalProfile, plan: StudyPlan, mastery: List<TopicMastery>): String {
        val system = "You are LearnMate, an encouraging study coach. Use ONLY the data given. " +
            "Write at most 90 words of plain text: one sentence on progress, then two concrete tips for this week."
        val user = buildString {
            append(goalLine(goal)).append('\n')
            val weak = weakSummary(mastery)
            append("Weak topics: ").append(if (weak.isEmpty()) "none measured yet" else weak).append('\n')
            val week = plan.weeks.firstOrNull()
            if (week != null) {
                append("This week (").append(week.minutes).append(" min): ")
                append(week.items.joinToString("; ") { "${it.title} (${it.minutes} min)" }).append('\n')
            }
            append("Weeks in plan: ").append(plan.weeks.size)
            if (plan.unscheduledLessons > 0) append(", lessons that do not fit: ").append(plan.unscheduledLessons)
            append(".\nWrite the coaching note.")
        }
        return chat(system, user)
    }

    /** Removes the echoed prompt, role headers and end-of-turn tokens from raw model output. */
    fun cleanOutput(raw: String, prompt: String): String {
        var t = raw
        val i = t.lastIndexOf(ASSISTANT_HEADER)
        if (i >= 0) t = t.substring(i + ASSISTANT_HEADER.length)
        else if (prompt.isNotEmpty() && t.startsWith(prompt)) t = t.substring(prompt.length)
        t = t.substringBefore(EOT).substringBefore("<|end_of_text|>")
        t = t.replace(Regex("<\\|[a-z_0-9]+\\|>"), "")
        return t.trim()
    }
}
