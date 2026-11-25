package com.varunkb.learnmateai.data

import android.content.Context
import com.varunkb.learnmateai.domain.GoalProfile
import com.varunkb.learnmateai.domain.PlanJson
import com.varunkb.learnmateai.domain.StudyPlan

/**
 * Persists the learner's goal, study plan and the "use Llama" switch in a small SharedPreferences file.
 * Kept outside Room on purpose: the existing database (and the data already on the phone) is untouched,
 * so there is no schema migration to get wrong.
 */
class PlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("learnmate_plan", Context.MODE_PRIVATE)

    fun loadGoal(): GoalProfile? = prefs.getString(KEY_GOAL, null)?.let { PlanJson.goalFromJson(it) }
    fun saveGoal(goal: GoalProfile) = prefs.edit().putString(KEY_GOAL, PlanJson.goalToJson(goal)).apply()

    fun loadPlan(): StudyPlan? = prefs.getString(KEY_PLAN, null)?.let { PlanJson.planFromJson(it) }
    fun savePlan(plan: StudyPlan) = prefs.edit().putString(KEY_PLAN, PlanJson.planToJson(plan)).apply()

    fun clear() = prefs.edit().remove(KEY_GOAL).remove(KEY_PLAN).apply()

    var useLlm: Boolean
        get() = prefs.getBoolean(KEY_USE_LLM, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_LLM, value).apply()

    private companion object {
        const val KEY_GOAL = "goal_json"
        const val KEY_PLAN = "plan_json"
        const val KEY_USE_LLM = "use_llm"
    }
}
