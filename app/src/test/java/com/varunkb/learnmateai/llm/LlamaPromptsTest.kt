package com.varunkb.learnmateai.llm

import com.varunkb.learnmateai.domain.Catalog
import com.varunkb.learnmateai.domain.GoalParser
import com.varunkb.learnmateai.domain.LearnerContext
import com.varunkb.learnmateai.domain.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaPromptsTest {
    private val catalog = Catalog(TestContent.packs)
    private val ctx = LearnerContext("Asha", "c", "C Programming", 0, 0, emptyList())

    @Test
    fun chatTemplateFollowsLlama3Format() {
        val p = LlamaPrompts.chat("SYS", "USR")
        assertTrue(p.contains("<|start_header_id|>system<|end_header_id|>\n\nSYS<|eot_id|>"))
        assertTrue(p.contains("<|start_header_id|>user<|end_header_id|>\n\nUSR<|eot_id|>"))
        assertTrue(p.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun groundedPromptCarriesLessonAndStaysWithinBudget() {
        val lesson = TestContent.lessons.maxByOrNull { it.body.length }!!
        val p = LlamaPrompts.groundedTutor("What are pointers?", lesson, ctx, null)
        assertTrue(p.contains(lesson.title))
        assertTrue(p.contains("QUESTION: What are pointers?"))
        // leave room for the answer inside the 1024-token window
        assertTrue(LlamaPrompts.estimateTokens(p) + LlmConfig.ANSWER_TOKENS < LlmConfig.MAX_SEQ_LEN)
    }

    @Test
    fun goalPromptListsEverySubjectId() {
        val p = LlamaPrompts.goalToJson("become a VLSI engineer", "UVM", catalog.subjects)
        catalog.subjects.forEach { assertTrue(p.contains(it.id + ": " + it.title)) }
        val g = GoalParser.parse("x", "", null, null, catalog)
        assertEquals(1, g.subjectIds.size)
    }

    @Test
    fun cleanOutputRemovesEchoAndSpecialTokens() {
        val prompt = LlamaPrompts.chat("S", "U")
        assertEquals("Hello there", LlamaPrompts.cleanOutput(prompt + "Hello there<|eot_id|>", prompt))
        assertEquals("Hello", LlamaPrompts.cleanOutput("Hello<|eot_id|>garbage", prompt))
        assertEquals("Answer", LlamaPrompts.cleanOutput("<|start_header_id|>assistant<|end_header_id|>\n\nAnswer<|end_of_text|>", prompt))
    }

    @Test
    fun advisoryDetection() {
        assertTrue(LlamaPrompts.looksAdvisory("Which subject should I learn for placements?"))
        assertFalse(LlamaPrompts.looksAdvisory("hello"))
    }
}
