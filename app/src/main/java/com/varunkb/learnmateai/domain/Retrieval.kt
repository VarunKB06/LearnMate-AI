package com.varunkb.learnmateai.domain

import kotlin.math.ln

/** Text normalisation shared by indexing and querying so both sides produce identical terms. */
object TextAnalyzer {
    private val stopwords = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "of", "in", "on", "to", "for", "and", "or",
        "it", "its", "this", "that", "these", "those", "with", "as", "at", "by", "from", "do", "does",
        "did", "can", "could", "would", "should", "will", "i", "me", "my", "you", "your", "we", "us",
        "what", "whats", "which", "who", "how", "why", "when", "where", "tell", "explain", "show",
        "give", "about", "please", "example", "examples", "mean", "means", "meaning", "define",
        "definition", "use", "used", "using", "work", "works", "learn", "understand", "between", "vs"
    )

    /** Query-side expansions for common abbreviations (applied after stemming). */
    private val synonyms = mapOf(
        "ptr" to listOf("pointer"),
        "var" to listOf("variable"),
        "func" to listOf("function"),
        "db" to listOf("database"),
        "rdbms" to listOf("database"),
        "bst" to listOf("binary", "search", "tree"),
        "oop" to listOf("class", "object"),
        "dfs" to listOf("depth", "first", "traversal"),
        "bfs" to listOf("breadth", "first", "traversal"),
        "sql" to listOf("select", "query"),
        "pk" to listOf("primary", "key"),
        "fk" to listOf("foreign", "key"),
        "heap" to listOf("malloc", "dynamic", "memory"),
        "dict" to listOf("dictionary"),
        "bigo" to listOf("complexity"),
        "recurs" to listOf("recursion")
    )

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in stopwords }
            .map { stem(it) }

    fun expandQuery(tokens: List<String>): List<String> =
        tokens.flatMap { t -> listOf(t) + (synonyms[t] ?: emptyList()).map { stem(it) } }.distinct()

    /** Very small suffix stripper. It only has to be consistent between index and query. */
    fun stem(word: String): String {
        var w = word
        if (w.length > 4 && w.endsWith("ies")) w = w.dropLast(3) + "y"
        else if (w.length > 5 && w.endsWith("sses")) w = w.dropLast(2)
        else if (w.length > 5 && w.endsWith("ing")) w = w.dropLast(3)
        else if (w.length > 4 && w.endsWith("ed")) w = w.dropLast(2)
        else if (w.length > 4 && w.endsWith("es")) w = w.dropLast(2)
        else if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) w = w.dropLast(1)
        if (w.length > 4 && w.endsWith("e")) w = w.dropLast(1)
        if (w.length > 3 && w[w.length - 1] == w[w.length - 2] && w.last() !in "lsz") w = w.dropLast(1)
        return w
    }
}

data class Hit(val lesson: Lesson, val score: Double, val coverage: Double)

/**
 * BM25 ranking over lessons with field weighting (title/keywords/topic count more than body).
 * [coverage] is the idf-weighted fraction of query terms found in the lesson, which the tutor
 * uses to decide whether it actually knows the answer instead of guessing.
 */
class Bm25Index(private val lessons: List<Lesson>) {
    private class Doc(val lesson: Lesson, val tf: Map<String, Double>, val length: Double)

    private val docs: List<Doc>
    private val idf = HashMap<String, Double>()
    private val avgLen: Double

    init {
        docs = lessons.map { l ->
            val tf = HashMap<String, Double>()
            fun add(text: String, weight: Double) {
                TextAnalyzer.tokenize(text).forEach { tf[it] = (tf[it] ?: 0.0) + weight }
            }
            add(l.title, 3.0)
            add(l.keywords.joinToString(" "), 3.0)
            add(l.topic, 2.0)
            add(l.body, 1.0)
            Doc(l, tf, tf.values.sum())
        }
        avgLen = if (docs.isEmpty()) 1.0 else docs.sumOf { it.length } / docs.size
        val df = HashMap<String, Int>()
        docs.forEach { d -> d.tf.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = docs.size.toDouble()
        df.forEach { (t, c) -> idf[t] = ln(1.0 + (n - c + 0.5) / (c + 0.5)) }
    }

    val size: Int get() = docs.size

    fun search(query: String, preferSubject: String? = null, limit: Int = 3): List<Hit> {
        val terms = TextAnalyzer.expandQuery(TextAnalyzer.tokenize(query))
        if (terms.isEmpty() || docs.isEmpty()) return emptyList()
        val unknownIdf = ln(1.0 + docs.size)
        val denominator = terms.sumOf { idf[it] ?: unknownIdf }
        val k1 = 1.2
        val b = 0.75
        return docs.mapNotNull { d ->
            var score = 0.0
            var matched = 0.0
            for (t in terms) {
                val f = d.tf[t] ?: continue
                val w = idf[t] ?: continue
                score += w * (f * (k1 + 1)) / (f + k1 * (1 - b + b * d.length / avgLen))
                matched += w
            }
            if (score <= 0.0) null else {
                val boost = if (preferSubject != null && d.lesson.subjectId == preferSubject) 1.25 else 1.0
                Hit(d.lesson, score * boost, matched / denominator)
            }
        }.sortedByDescending { it.score }.take(limit)
    }
}
