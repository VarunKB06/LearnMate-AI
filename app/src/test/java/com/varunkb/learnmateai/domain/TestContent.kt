package com.varunkb.learnmateai.domain

import java.io.File

/** Loads the real bundled content packs from the module's assets directory (JVM unit tests). */
object TestContent {
    private val dir: File = listOf(File("src/main/assets/content"), File("app/src/main/assets/content"))
        .first { it.isDirectory }

    val packs: List<ContentPack> by lazy {
        dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { ContentParser.parse(it.readText()) }
    }
    val lessons: List<Lesson> get() = packs.flatMap { it.lessons }
    val questions: List<Question> get() = packs.flatMap { it.questions }
    fun pack(id: String) = packs.first { it.subject.id == id }
}
