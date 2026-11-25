package com.varunkb.learnmateai.domain

import kotlin.math.max

enum class PlanItemKind { Learn, Revise, Quiz }

data class PlanItem(
    val kind: PlanItemKind,
    val subjectId: String,
    /** Lesson to open, or null for a checkpoint quiz. */
    val lessonId: String?,
    val title: String,
    val minutes: Int,
    /** One line explaining why this item is in the plan. Shown to the learner. */
    val reason: String
)

data class PlanWeek(val number: Int, val theme: String, val items: List<PlanItem>) {
    val minutes: Int get() = items.sumOf { it.minutes }
}

data class StudyPlan(
    val createdAt: Long,
    /** 1 for the first plan, +1 every time the plan is rebuilt from new progress. */
    val revision: Int,
    val weeks: List<PlanWeek>,
    /** Lessons that belong to the goal but do not fit in the available weeks and hours. */
    val unscheduledLessons: Int,
    /** Lessons left out because the learner finished them or already shows high mastery. */
    val skippedLessons: Int,
    /** Short factual notes about what changed since the previous revision. */
    val notes: List<String>,
    /** Free-text coaching written by the on-device Llama model, or null if it was never asked. */
    val coachNote: String? = null,
    /** True when the plan was rebuilt after [coachNote] was written. */
    val coachStale: Boolean = false
) {
    val totalItems: Int get() = weeks.sumOf { it.items.size }
}

/**
 * Deterministic, explainable study planner. It is the reliable core of "generate study plans" and
 * "adapt over time": the Llama model understands the goal and explains the plan, but the schedule
 * itself comes from these rules so it is always valid, fits the learner's hours and can be tested.
 *
 * Inputs: the goal, Bayesian-Knowledge-Tracing mastery per topic, and lesson progress.
 * Rules:
 *  1. Subjects: the goal's subjects in priority order; any prerequisite subject that is less than
 *     half complete is placed before the subject that needs it.
 *  2. Weak topics (mastery below 60% after at least 3 answers) become "Revise" items first.
 *  3. Lessons already completed, or on topics with mastery of at least 85%, are skipped.
 *  4. Lessons are packed into weeks up to hoursPerWeek, reserving one checkpoint quiz per week.
 *  5. Spare capacity goes to subjects that match the learner's interests.
 */
object StudyPlanner {
    const val MASTERED = 0.85
    const val WEAK = 0.60
    const val MIN_ANSWERS = 3
    const val QUIZ_MINUTES = 15

    private class Queued(val item: PlanItem, val core: Boolean)

    private class Ordered(val ids: List<String>, val prereqOf: Map<String, String>)

    private fun orderSubjects(catalog: Catalog, goal: GoalProfile, progress: Map<String, LessonProgress>): Ordered {
        val out = LinkedHashSet<String>()
        val visiting = HashSet<String>()
        val prereqOf = HashMap<String, String>()
        fun add(id: String, requiredBy: Subject?) {
            val s = catalog.subject(id) ?: return
            if (id in out || !visiting.add(id)) return
            for (p in s.prerequisites) {
                if (catalog.subject(p) == null) continue
                if (Curriculum.progressFor(catalog.lessons(p), progress).percent < 50) add(p, s)
            }
            if (requiredBy != null && id !in goal.subjectIds) prereqOf[id] = requiredBy.title
            out.add(id)
        }
        goal.subjectIds.forEach { add(it, null) }
        return Ordered(out.toList(), prereqOf)
    }

    private fun interestFor(subject: Subject, interests: List<String>): String? {
        val hay = (subject.title + " " + subject.description).lowercase()
        return interests.firstOrNull { interest ->
            val words = interest.lowercase().split(Regex("[^a-z0-9+#]+")).filter { it.length >= 3 }
            words.isNotEmpty() && words.any { it in hay }
        }
    }

    fun plan(
        catalog: Catalog,
        goal: GoalProfile,
        mastery: List<TopicMastery>,
        progress: Map<String, LessonProgress>,
        now: Long,
        previous: StudyPlan? = null
    ): StudyPlan {
        val byTopic = mastery.associateBy { it.subjectId to it.topic }
        fun stat(l: Lesson): TopicMastery? = byTopic[l.subjectId to l.topic]?.takeIf { it.total >= MIN_ANSWERS }
        fun isMastered(l: Lesson) = (stat(l)?.pKnown ?: 0.0) >= MASTERED
        fun isWeak(l: Lesson) = stat(l)?.let { it.pKnown < WEAK } == true
        fun isDone(l: Lesson) = progress[l.id]?.status == LessonStatus.Completed
        fun minutes(l: Lesson) = max(l.minutes, 5)

        val ordered = orderSubjects(catalog, goal, progress)
        val revise = ArrayList<Queued>()
        val learn = ArrayList<Queued>()
        val scheduledIds = HashSet<String>()
        var skipped = 0

        for (sid in ordered.ids) {
            val subject = catalog.subject(sid) ?: continue
            val lessons = catalog.lessons(sid)
            val whyLearn = ordered.prereqOf[sid]?.let { "Prerequisite for $it" }
                ?: "Part of your goal (${goal.targetType.lowercase()})"

            val reviseTopics = HashSet<String>()
            for (l in lessons) {
                if (isWeak(l) && reviseTopics.add(l.topic)) {
                    val m = stat(l)!!
                    revise.add(
                        Queued(
                            PlanItem(
                                PlanItemKind.Revise, sid, l.id, "Revise: ${l.title}", minutes(l),
                                "Your mastery of ${l.topic} is ${m.masteryPercent}% (${m.correct}/${m.total} correct)"
                            ), true
                        )
                    )
                    scheduledIds.add(l.id)
                }
            }
            for (l in lessons) {
                if (l.id in scheduledIds) continue
                if (isDone(l) || isMastered(l)) { skipped++; continue }
                if (goal.level == "Advanced" && l.level == "Beginner" && !isWeak(l)) { skipped++; continue }
                learn.add(Queued(PlanItem(PlanItemKind.Learn, sid, l.id, l.title, minutes(l), whyLearn), true))
            }
        }

        // Spare capacity: subjects outside the goal that match an interest.
        val bonus = ArrayList<Queued>()
        for (subject in catalog.subjects) {
            if (subject.id in ordered.ids) continue
            val interest = interestFor(subject, goal.interests) ?: continue
            for (l in catalog.lessons(subject.id)) {
                if (isDone(l) || isMastered(l)) continue
                bonus.add(Queued(PlanItem(PlanItemKind.Learn, subject.id, l.id, l.title, minutes(l), "Matches your interest: $interest"), false))
            }
        }

        val queue = ArrayDeque<Queued>()
        queue.addAll(revise)
        queue.addAll(learn)
        queue.addAll(bonus)

        val capacity = goal.hoursPerWeek * 60
        val weeks = ArrayList<PlanWeek>()
        var number = 1
        while (number <= goal.weeks && queue.isNotEmpty()) {
            val items = ArrayList<PlanItem>()
            var used = 0
            val room = capacity - QUIZ_MINUTES
            while (queue.isNotEmpty()) {
                val next = queue.first()
                if (items.isNotEmpty() && used + next.item.minutes > room) break
                items.add(queue.removeFirst().item)
                used += next.item.minutes
            }
            val main = items.groupBy { it.subjectId }.maxByOrNull { (_, v) -> v.sumOf { it.minutes } }?.key
            if (main != null) {
                val title = catalog.subject(main)?.title ?: main
                items.add(
                    PlanItem(
                        PlanItemKind.Quiz, main, null, "Checkpoint quiz: $title", QUIZ_MINUTES,
                        "Updates your mastery so next week's plan adapts"
                    )
                )
            }
            val titles = items.filter { it.kind != PlanItemKind.Quiz }
                .map { catalog.subject(it.subjectId)?.title ?: it.subjectId }.distinct()
            val theme = if (titles.size <= 2) titles.joinToString(" + ") else titles.take(2).joinToString(" + ") + " + more"
            weeks.add(PlanWeek(number, theme, items))
            number++
        }
        val unscheduled = queue.count { it.core }

        val notes = ArrayList<String>()
        if (weeks.isEmpty()) {
            notes.add("Everything for this goal is completed or already mastered. Set a new goal or take a quiz to find gaps.")
        }
        if (skipped > 0) notes.add("Left out $skipped lesson(s) you completed or already know well.")
        if (unscheduled > 0) {
            notes.add("$unscheduled lesson(s) do not fit in ${goal.weeks} week(s) at ${goal.hoursPerWeek} h/week. Add hours or weeks to include them.")
        }
        if (previous != null) {
            val before = previous.weeks.flatMap { it.items }.mapNotNull { it.lessonId }.toSet()
            val after = weeks.flatMap { it.items }.mapNotNull { it.lessonId }.toSet()
            val finished = before.count { it !in after && catalog.lesson(it)?.let { l -> isDone(l) } == true }
            if (finished > 0) notes.add("Since the last plan you completed $finished planned lesson(s).")
            weeks.flatMap { it.items }
                .filter { it.kind == PlanItemKind.Revise && it.lessonId !in before }
                .take(3)
                .forEach { notes.add("Added revision because of a quiz result: ${it.title.removePrefix("Revise: ")}.") }
        }

        return StudyPlan(
            createdAt = now,
            revision = (previous?.revision ?: 0) + 1,
            weeks = weeks,
            unscheduledLessons = unscheduled,
            skippedLessons = skipped,
            notes = notes,
            coachNote = previous?.coachNote,
            coachStale = previous?.coachNote != null
        )
    }
}

/** JSON round trip for the goal and plan, so they survive app restarts without a Room migration. */
object PlanJson {
    fun goalToJson(g: GoalProfile): String = MiniJson.write(
        mapOf(
            "goalText" to g.goalText, "targetType" to g.targetType, "subjectIds" to g.subjectIds,
            "interests" to g.interests, "hours" to g.hoursPerWeek, "weeks" to g.weeks,
            "level" to g.level, "source" to g.source
        )
    )

    fun goalFromJson(json: String): GoalProfile? {
        val m = parseMap(json) ?: return null
        val text = m["goalText"] as? String ?: return null
        return GoalProfile(
            goalText = text,
            targetType = m["targetType"] as? String ?: "Skill",
            subjectIds = strings(m["subjectIds"]),
            interests = strings(m["interests"]),
            hoursPerWeek = (m["hours"] as? Number)?.toInt() ?: GoalProfile.DEFAULT_HOURS,
            weeks = (m["weeks"] as? Number)?.toInt() ?: GoalProfile.DEFAULT_WEEKS,
            level = m["level"] as? String ?: "Beginner",
            source = m["source"] as? String ?: GoalProfile.SOURCE_RULES
        )
    }

    fun planToJson(p: StudyPlan): String = MiniJson.write(
        mapOf(
            "createdAt" to p.createdAt, "revision" to p.revision, "unscheduled" to p.unscheduledLessons,
            "skipped" to p.skippedLessons, "notes" to p.notes, "coach" to p.coachNote, "coachStale" to p.coachStale,
            "weeks" to p.weeks.map { w ->
                mapOf(
                    "n" to w.number, "theme" to w.theme,
                    "items" to w.items.map { i ->
                        mapOf("k" to i.kind.name, "s" to i.subjectId, "l" to i.lessonId, "t" to i.title, "m" to i.minutes, "r" to i.reason)
                    }
                )
            }
        )
    )

    fun planFromJson(json: String): StudyPlan? {
        val m = parseMap(json) ?: return null
        val weeks = (m["weeks"] as? List<*>).orEmpty().mapNotNull { wAny ->
            val w = wAny as? Map<*, *> ?: return@mapNotNull null
            val items = (w["items"] as? List<*>).orEmpty().mapNotNull { iAny ->
                val i = iAny as? Map<*, *> ?: return@mapNotNull null
                val kind = PlanItemKind.values().firstOrNull { it.name == i["k"] } ?: return@mapNotNull null
                PlanItem(
                    kind = kind,
                    subjectId = i["s"] as? String ?: return@mapNotNull null,
                    lessonId = i["l"] as? String,
                    title = i["t"] as? String ?: "",
                    minutes = (i["m"] as? Number)?.toInt() ?: 5,
                    reason = i["r"] as? String ?: ""
                )
            }
            PlanWeek((w["n"] as? Number)?.toInt() ?: 0, w["theme"] as? String ?: "", items)
        }
        return StudyPlan(
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            revision = (m["revision"] as? Number)?.toInt() ?: 1,
            weeks = weeks,
            unscheduledLessons = (m["unscheduled"] as? Number)?.toInt() ?: 0,
            skippedLessons = (m["skipped"] as? Number)?.toInt() ?: 0,
            notes = strings(m["notes"]),
            coachNote = m["coach"] as? String,
            coachStale = m["coachStale"] as? Boolean ?: false
        )
    }

    private fun parseMap(json: String): Map<*, *>? = try {
        MiniJson.parse(json) as? Map<*, *>
    } catch (e: Exception) {
        null
    }

    private fun strings(v: Any?): List<String> = (v as? List<*>).orEmpty().mapNotNull { it as? String }
}
