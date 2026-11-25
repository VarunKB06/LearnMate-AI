package com.varunkb.learnmateai.llm

import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.GoalParser
import com.varunkb.learnmateai.domain.GoalProfile
import com.varunkb.learnmateai.domain.LearnerContext
import com.varunkb.learnmateai.domain.ReplyKind
import com.varunkb.learnmateai.domain.StudyPlan
import com.varunkb.learnmateai.domain.TopicMastery
import com.varunkb.learnmateai.domain.TutorReply

/**
 * The reasoning layer on top of the offline tutor. It calls the on-device Llama model for three jobs:
 *  1. answer a concept question from the lesson the BM25 retriever found (grounded tutoring),
 *  2. give goal-based advice from the learner's data (advisory),
 *  3. understand a goal written in free text and write a coaching note for the study plan.
 * Every function returns null when the model is unavailable or gives unusable output, so callers
 * can fall back to the deterministic rule-based result.
 */
class LlmAdvisor(private val llm: LlmManager, private val catalog: Catalog) {

    /** Which rule-based replies the model is allowed to improve. Code, error, progress and quiz replies stay deterministic. */
    fun handles(rule: TutorReply, question: String): Boolean = when (rule.kind) {
        ReplyKind.Lesson -> rule.lessonId != null
        ReplyKind.StudyPlan -> true
        ReplyKind.NoMatch -> LlamaPrompts.looksAdvisory(question)
        else -> false
    }

    suspend fun answer(
        question: String,
        ctx: LearnerContext,
        rule: TutorReply,
        goal: GoalProfile?,
        plan: StudyPlan?,
        mastery: List<TopicMastery>
    ): TutorReply? {
        if (!handles(rule, question)) return null
        val lesson = rule.lessonId?.let { catalog.lesson(it) }
        val grounded = rule.kind == ReplyKind.Lesson && lesson != null
        val prompt = if (grounded) {
            LlamaPrompts.groundedTutor(question, lesson!!, ctx, goal)
        } else {
            LlamaPrompts.advisory(question, ctx, goal, plan, catalog.subjects, mastery)
        }
        val res = llm.generate(prompt, LlmConfig.ANSWER_TOKENS) ?: return null
        if (res.cancelled || res.text.length < 15) return null
        val label = if (grounded) "Llama on-device · grounded in: ${lesson!!.title}" else "Llama on-device · advice from your goal and progress"
        return TutorReply(
            text = res.text,
            kind = if (rule.kind == ReplyKind.NoMatch) ReplyKind.StudyPlan else rule.kind,
            sourceTitle = label,
            confidence = if (grounded) rule.confidence else null,
            lessonId = if (grounded) lesson!!.id else null
        )
    }

    /** Understands a goal in the learner's own words. Returns null if the model output is not valid. */
    suspend fun parseGoal(goalText: String, interests: String, fallback: GoalProfile): GoalProfile? {
        val res = llm.generate(LlamaPrompts.goalToJson(goalText, interests, catalog.subjects), LlmConfig.GOAL_TOKENS) ?: return null
        if (res.cancelled) return null
        return GoalParser.fromLlmJson(res.text, fallback, catalog)
    }

    suspend fun coach(goal: GoalProfile, plan: StudyPlan, mastery: List<TopicMastery>): String? {
        val res = llm.generate(LlamaPrompts.coach(goal, plan, mastery), LlmConfig.COACH_TOKENS) ?: return null
        return res.text.takeIf { !res.cancelled && it.length >= 20 }
    }

    /** Honest self-description for "Are you an LLM?" that depends on whether a model is installed. */
    fun aboutText(): String {
        val s = llm.state.value
        val base = "LearnMate has two layers. The first is a rule-based tutor: BM25 lesson search, Bayesian Knowledge Tracing for your mastery, " +
            "and a deterministic study-plan engine. The second is a Llama language model that runs on this phone through ExecuTorch."
        return if (s.installed) {
            "$base A model is installed (${s.modelPath?.substringAfterLast('/')}, ${s.modelSizeMb} MB), so Llama now explains lessons, " +
                "understands your goal and writes coaching notes. It is only given your lesson text and your own data, and nothing leaves the phone."
        } else {
            "$base No Llama model is installed yet, so only the first layer is active. Open Model setup from the home screen to install one."
        }
    }
}
