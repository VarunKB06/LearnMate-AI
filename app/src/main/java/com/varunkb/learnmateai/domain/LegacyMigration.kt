package com.varunkb.learnmateai.domain

data class LegacyAttempt(
    val date: Long,
    val correct: Int,
    val total: Int,
    val topics: Map<String, Pair<Int, Int>>
)

/**
 * Pure logic for migrating the SharedPreferences data written by earlier builds
 * (preference file "learnmate_local_v1") into the Room database.
 */
object LegacyMigration {

    /** Parses the old "attempts" JSON array. Malformed input yields an empty list, never a crash. */
    fun parseAttempts(json: String?): List<LegacyAttempt> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = MiniJson.parse(json) as? List<*> ?: return emptyList()
            arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val total = (m["total"] as? Number)?.toInt() ?: return@mapNotNull null
                val correct = (m["correct"] as? Number)?.toInt() ?: return@mapNotNull null
                if (total <= 0 || correct !in 0..total) return@mapNotNull null
                val topics = LinkedHashMap<String, Pair<Int, Int>>()
                (m["topics"] as? Map<*, *>)?.forEach { (k, v) ->
                    val t = v as? Map<*, *> ?: return@forEach
                    val c = (t["correct"] as? Number)?.toInt() ?: 0
                    val n = (t["total"] as? Number)?.toInt() ?: 0
                    if (k is String && n > 0 && c in 0..n) topics[k] = c to n
                }
                LegacyAttempt((m["date"] as? Number)?.toLong() ?: 0L, correct, total, topics)
            }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** Folds legacy per-attempt topic counts into totals per topic. */
    fun foldTopics(attempts: List<LegacyAttempt>): Map<String, Pair<Int, Int>> {
        val out = LinkedHashMap<String, Pair<Int, Int>>()
        attempts.forEach { a ->
            a.topics.forEach { (t, s) ->
                val old = out[t] ?: (0 to 0)
                out[t] = (old.first + s.first) to (old.second + s.second)
            }
        }
        return out
    }

    /** Laplace-smoothed estimate, used because old data has only aggregated counts, not answer order. */
    fun masteryFromCounts(subjectId: String, topic: String, correct: Int, total: Int): TopicMastery =
        TopicMastery(subjectId, topic, ((correct + 1.0) / (total + 2.0)).coerceIn(0.001, 0.999), correct, total)

    /** The old profile stored free text ("C", "Python"); map it to a bundled subject id. */
    fun subjectIdFor(text: String, subjects: List<Subject>): String {
        val t = text.trim().lowercase()
        subjects.firstOrNull { it.id == t || it.title.lowercase() == t }?.let { return it.id }
        if (t.length >= 3) subjects.firstOrNull { it.title.lowercase().contains(t) }?.let { return it.id }
        return subjects.firstOrNull { it.id == "c" }?.id ?: subjects.first().id
    }
}
