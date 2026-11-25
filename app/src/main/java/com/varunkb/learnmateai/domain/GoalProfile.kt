package com.varunkb.learnmateai.domain

/**
 * What the learner wants to achieve, in a structured form the planner can use.
 *
 * It is produced either by the on-device Llama model (which reads the learner's own words and
 * returns JSON, see [GoalParser.fromLlmJson]) or, when no model is installed, by the keyword rules in
 * [GoalParser.parse]. [source] records which one, so the UI never claims more than what happened.
 */
data class GoalProfile(
    val goalText: String,
    /** Career, Exam, Certification, Skill or Lifelong. */
    val targetType: String = "Skill",
    /** Subject ids the goal needs, in priority order. Prerequisites are added by the planner. */
    val subjectIds: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val hoursPerWeek: Int = DEFAULT_HOURS,
    val weeks: Int = DEFAULT_WEEKS,
    /** Self-reported level: Beginner, Intermediate or Advanced. */
    val level: String = "Beginner",
    /** "llama" when the on-device model understood the goal, "rules" for the keyword fallback. */
    val source: String = SOURCE_RULES
) {
    companion object {
        const val DEFAULT_HOURS = 5
        const val DEFAULT_WEEKS = 8
        const val SOURCE_RULES = "rules"
        const val SOURCE_LLAMA = "llama"
        val TARGET_TYPES = listOf("Career", "Exam", "Certification", "Skill", "Lifelong")
        val LEVELS = listOf("Beginner", "Intermediate", "Advanced")
    }
}

object GoalParser {
    /** Subject id -> words that point to it. Matched on whole words in the learner's text. */
    private val subjectWords: Map<String, Regex> = mapOf(
        "c" to Regex("""\bc programming\b|\bc language\b|\bpointers?\b|\bembedded\b|\blearn c\b|\bin c\b"""),
        "python" to Regex("""\bpython\b|\bscripting\b|\bautomation\b"""),
        "dsa" to Regex("""\bdsa\b|\bdata structures?\b|\balgorithms?\b|\bcoding interviews?\b|\bleetcode\b|\bplacements?\b|\bcompetitive\b"""),
        "dbms" to Regex("""\bdbms\b|\bdatabases?\b|\bsql\b|\bnormali[sz]ation\b"""),
        "os" to Regex("""\boperating systems?\b|\bos\b|\bscheduling\b|\bthreads?\b|\bdeadlocks?\b"""),
        "coa" to Regex("""\bcoa\b|\bcomputer organi[sz]ation\b|\bcomputer architecture\b|\bcpu\b|\bpipelin\w*\b|\bcache\b"""),
        "cn" to Regex("""\bnetwork(s|ing)?\b|\btcp\b|\bcn\b|\bprotocols?\b"""),
        "aiml" to Regex("""\bai\b|\bml\b|\bmachine learning\b|\bartificial intelligence\b|\bneural\b|\bdeep learning\b|\bdata science\b"""),
        "digital" to Regex("""\bdigital (logic|design|electronics)\b|\blogic gates?\b|\bflip[- ]?flops?\b|\bk-?map\b"""),
        "semi" to Regex("""\bsemiconductors?\b|\bdiodes?\b|\btransistors?\b|\bmosfets?\b"""),
        "cmos" to Regex("""\bcmos\b|\bvlsi\b|\bchip design\b|\blayout\b|\binverter\b"""),
        "verilog" to Regex("""\bverilog\b|\brtl\b|\bhdl\b|\bhardware description\b"""),
        "sv" to Regex("""\bsystem ?verilog\b|\bverification\b|\buvm\b|\bdv\b"""),
        "fpga" to Regex("""\bfpgas?\b|\bvivado\b|\bxilinx\b|\bsynthesis\b"""),
        "sta" to Regex("""\bsta\b|\bstatic timing\b|\btiming analysis\b|\bsetup and hold\b|\bphysical design\b""")
    )

    /** Career or goal phrases that imply a bundle of subjects, in the order they should be studied. */
    private val bundles: List<Pair<Regex, List<String>>> = listOf(
        Regex("""\bdesign verification\b|\bdv engineer\b|\bverification engineer\b""") to listOf("digital", "verilog", "sv"),
        Regex("""\bfpga (engineer|developer|design)\b""") to listOf("digital", "verilog", "fpga"),
        Regex("""\bvlsi\b|\bchip design\b|\brtl (engineer|design)\b""") to listOf("digital", "verilog", "sv", "cmos"),
        Regex("""\bsoftware (developer|engineer)\b|\bsde\b|\bplacements?\b|\bcoding interviews?\b""") to listOf("c", "dsa", "os", "dbms"),
        Regex("""\bdata scien\w+\b|\bml engineer\b|\bmachine learning engineer\b""") to listOf("python", "aiml"),
        Regex("""\bembedded (engineer|systems?|developer)\b""") to listOf("c", "coa", "digital"),
        Regex("""\bnetwork engineer\b""") to listOf("cn", "os")
    )

    private val hoursPerWeek = Regex("""(\d{1,2})\s*(?:hours?|hrs?|h)\s*(?:/|per|a|each|every)?\s*(?:week|wk)""")
    private val hoursPerDay = Regex("""(\d{1,2})\s*(?:hours?|hrs?|h)\s*(?:/|per|a|each|every)\s*day""")
    private val weeksRe = Regex("""(\d{1,2})\s*(?:weeks?|wks?)\b""")
    private val monthsRe = Regex("""(\d{1,2})\s*months?\b""")

    fun detectSubjects(lowerText: String, catalog: Catalog): List<String> {
        val ids = LinkedHashSet<String>()
        for ((re, list) in bundles) if (re.containsMatchIn(lowerText)) ids.addAll(list)
        subjectWords.entries
            .map { (id, re) -> id to re.findAll(lowerText).count() }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .forEach { ids.add(it.first) }
        val valid = ids.filter { catalog.subject(it) != null }
        if (valid.isNotEmpty()) return valid.take(5)
        // Nothing recognised: start from the first subject of the first learning path.
        return listOfNotNull(catalog.subjects.firstOrNull()?.id)
    }

    fun splitInterests(raw: String): List<String> =
        raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { it.take(40) }.distinct().take(5)

    /** Keyword fallback used when no Llama model is installed (or when it returns unusable output). */
    fun parse(text: String, interestsRaw: String, hours: Int?, weeks: Int?, catalog: Catalog): GoalProfile {
        val lower = text.lowercase()
        val interests = splitInterests(interestsRaw)
        val ids = detectSubjects(lower + " " + interests.joinToString(" ").lowercase(), catalog)
        val h = hours
            ?: hoursPerWeek.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: hoursPerDay.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.times(5)
            ?: GoalProfile.DEFAULT_HOURS
        val w = weeks
            ?: weeksRe.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: monthsRe.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.times(4)
            ?: GoalProfile.DEFAULT_WEEKS
        val type = when {
            Regex("""\bcertif\w*\b""").containsMatchIn(lower) -> "Certification"
            Regex("""\bexams?\b|\bgate\b|\bsemester\b|\bviva\b|\btest\b""").containsMatchIn(lower) -> "Exam"
            Regex("""\bjob\b|\bcareer\b|\binterviews?\b|\bplacements?\b|\bengineer\b|\bintern(ship)?\b|\brole\b""").containsMatchIn(lower) -> "Career"
            Regex("""\blifelong\b|\bhobby\b|\bcurious\b|\bfor fun\b""").containsMatchIn(lower) -> "Lifelong"
            else -> "Skill"
        }
        val level = when {
            Regex("""\bbeginner\b|\bnew to\b|\bfrom scratch\b|\bstart(ing)? out\b""").containsMatchIn(lower) -> "Beginner"
            Regex("""\badvanced\b|\bexpert\b""").containsMatchIn(lower) -> "Advanced"
            Regex("""\bintermediate\b""").containsMatchIn(lower) -> "Intermediate"
            else -> "Beginner"
        }
        return GoalProfile(
            goalText = text.trim().take(300),
            targetType = type,
            subjectIds = ids,
            interests = interests,
            hoursPerWeek = h.coerceIn(1, 40),
            weeks = w.coerceIn(1, 26),
            level = level,
            source = GoalProfile.SOURCE_RULES
        )
    }

    /**
     * Reads the JSON object the Llama model was asked to produce. Anything invalid is replaced by the
     * value from [fallback], and null is returned when the model gave no usable subject list at all,
     * so a hallucinated answer can never replace the rule-based result.
     */
    fun fromLlmJson(raw: String, fallback: GoalProfile, catalog: Catalog): GoalProfile? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed: Any? = try {
            MiniJson.parse(raw.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
        val map = parsed as? Map<*, *> ?: return null

        val subjects = (map["subjects"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim()?.lowercase() }
            .filter { catalog.subject(it) != null }
            .distinct()
            .take(5)
        if (subjects.isEmpty()) return null

        val type = (map["target_type"] as? String)?.trim()
            ?.let { t -> GoalProfile.TARGET_TYPES.firstOrNull { it.equals(t, ignoreCase = true) } }
            ?: fallback.targetType
        val level = (map["level"] as? String)?.trim()
            ?.let { t -> GoalProfile.LEVELS.firstOrNull { it.equals(t, ignoreCase = true) } }
            ?: fallback.level
        val interests = (map["interests"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim()?.take(40) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(5)
            .ifEmpty { fallback.interests }
        val hours = (map["hours_per_week"] as? Number)?.toInt()?.takeIf { it in 1..40 } ?: fallback.hoursPerWeek
        val weeks = (map["weeks"] as? Number)?.toInt()?.takeIf { it in 1..26 } ?: fallback.weeks
        return fallback.copy(
            targetType = type,
            subjectIds = subjects,
            interests = interests,
            hoursPerWeek = hours,
            weeks = weeks,
            level = level,
            source = GoalProfile.SOURCE_LLAMA
        )
    }
}
