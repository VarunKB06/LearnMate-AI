package com.varunkb.learnmateai.domain

/** Parses and validates one subject content pack (see the JSON files under assets/content). */
object ContentParser {

    fun parse(json: String): ContentPack {
        val root = MiniJson.parse(json) as? Map<*, *> ?: bad("Root must be an object")
        val subjectId = root.str("id")
        val subject = Subject(
            id = subjectId,
            title = root.str("title"),
            description = root.str("description"),
            level = root["level"] as? String ?: "Beginner",
            path = root["path"] as? String ?: "Programming Foundations",
            pathOrder = (root["pathOrder"] as? Number)?.toInt() ?: 0,
            prerequisites = (root["prerequisites"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            icon = root["icon"] as? String ?: "📘"
        )

        val questions = root.list("questions").mapIndexed { i, any ->
            val q = any as? Map<*, *> ?: bad("questions[$i] must be an object")
            val choices = q.list("choices").map { it as? String ?: bad("choice must be string in ${q["id"]}") }
            val answer = (q["answer"] as? Number)?.toInt() ?: bad("answer missing in ${q["id"]}")
            if (choices.size != 4) bad("Question ${q["id"]} must have exactly 4 choices")
            if (answer !in choices.indices) bad("Question ${q["id"]} answer out of range")
            val difficulty = (q["difficulty"] as? Number)?.toInt() ?: 2
            if (difficulty !in 1..3) bad("Question ${q["id"]} difficulty must be 1..3")
            Question(
                id = q.str("id"),
                subjectId = subjectId,
                topic = q.str("topic"),
                difficulty = difficulty,
                prompt = q.str("prompt"),
                choices = choices,
                answerIndex = answer,
                explanation = q.str("explanation")
            )
        }

        val lessons = root.list("lessons").mapIndexed { i, any ->
            val l = any as? Map<*, *> ?: bad("lessons[$i] must be an object")
            Lesson(
                id = l.str("id"),
                subjectId = subjectId,
                topic = l.str("topic"),
                title = l.str("title"),
                keywords = l.list("keywords").map { it.toString() },
                body = l.str("body"),
                example = l["example"] as? String,
                level = l["level"] as? String ?: "Beginner",
                minutes = (l["minutes"] as? Number)?.toInt() ?: estimateMinutes(l.str("body")),
                objectives = l.strings("objectives"),
                definitions = (l["definitions"] as? List<*>).orEmpty().mapNotNull { d ->
                    val m = d as? Map<*, *> ?: return@mapNotNull null
                    val t = m["term"] as? String
                    val x = m["meaning"] as? String
                    if (t != null && x != null) Definition(t, x) else null
                },
                exampleNote = l["exampleNote"] as? String,
                diagram = l["diagram"] as? String,
                mistakes = l.strings("mistakes"),
                practice = (l["practice"] as? List<*>).orEmpty().mapNotNull { d ->
                    val m = d as? Map<*, *> ?: return@mapNotNull null
                    val q = m["q"] as? String
                    val a = m["a"] as? String
                    if (q != null && a != null) PracticeItem(q, a) else null
                },
                exercises = l.strings("exercises"),
                summary = l.strings("summary"),
                hdl = parseHdl(l["hdl"])
            )
        }

        if (questions.map { it.id }.toSet().size != questions.size) bad("Duplicate question ids in $subjectId")
        if (lessons.map { it.id }.toSet().size != lessons.size) bad("Duplicate lesson ids in $subjectId")
        return ContentPack(subject, questions, lessons)
    }

    private fun estimateMinutes(body: String): Int = (body.split(" ").size / 120 + 3).coerceIn(3, 15)

    private fun Map<*, *>.strings(key: String): List<String> =
        (this[key] as? List<*>).orEmpty().mapNotNull { it as? String }

    private fun parseHdl(any: Any?): LessonHdl? {
        val m = any as? Map<*, *> ?: return null
        val files = (m["files"] as? List<*>).orEmpty().mapNotNull { f ->
            val fm = f as? Map<*, *> ?: return@mapNotNull null
            val name = fm["name"] as? String ?: return@mapNotNull null
            val text = fm["text"] as? String ?: return@mapNotNull null
            HdlFile(name, fm["tb"] as? Boolean ?: false, text)
        }
        val title = m["title"] as? String ?: return null
        return if (files.isEmpty()) null else LessonHdl(title, files)
    }

    private fun bad(msg: String): Nothing = throw IllegalArgumentException(msg)

    private fun Map<*, *>.str(key: String): String =
        (this[key] as? String)?.takeIf { it.isNotBlank() } ?: bad("Missing string field '$key'")

    private fun Map<*, *>.list(key: String): List<*> =
        this[key] as? List<*> ?: bad("Missing list field '$key'")
}
