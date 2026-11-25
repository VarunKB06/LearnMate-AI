package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalParserTest {
    private val catalog = Catalog(TestContent.packs)

    @Test
    fun vlsiCareerGoalSelectsHardwareSubjectsAndTime() {
        val g = GoalParser.parse("I want to become a VLSI design verification engineer in 3 months, 8 hours a week", "UVM, FPGA", null, null, catalog)
        assertTrue("sv" in g.subjectIds && "verilog" in g.subjectIds)
        assertEquals("Career", g.targetType)
        assertEquals(8, g.hoursPerWeek)
        assertEquals(12, g.weeks)
        assertEquals(listOf("UVM", "FPGA"), g.interests)
        assertEquals(GoalProfile.SOURCE_RULES, g.source)
    }

    @Test
    fun formValuesOverrideTextAndAreClamped() {
        val g = GoalParser.parse("prepare for DBMS exam", "", 99, 0, catalog)
        assertEquals(40, g.hoursPerWeek)
        assertEquals(1, g.weeks)
        assertEquals("Exam", g.targetType)
        assertTrue("dbms" in g.subjectIds)
    }

    @Test
    fun unknownGoalStillGetsAStartingSubject() {
        val g = GoalParser.parse("improve myself", "", null, null, catalog)
        assertEquals(1, g.subjectIds.size)
        assertEquals(GoalProfile.DEFAULT_HOURS, g.hoursPerWeek)
    }

    @Test
    fun llmJsonIsValidatedAgainstTheCatalog() {
        val fb = GoalParser.parse("learn verification", "", null, null, catalog)
        val ok = GoalParser.fromLlmJson(
            "Sure! {\"target_type\":\"career\",\"subjects\":[\"verilog\",\"made_up\",\"sv\"],\"interests\":[\"UVM\"],\"hours_per_week\":6,\"weeks\":99,\"level\":\"Advanced\"}",
            fb, catalog
        )!!
        assertEquals(listOf("verilog", "sv"), ok.subjectIds)
        assertEquals("Career", ok.targetType)
        assertEquals(6, ok.hoursPerWeek)
        assertEquals(fb.weeks, ok.weeks) // 99 is out of range, fallback kept
        assertEquals("Advanced", ok.level)
        assertEquals(GoalProfile.SOURCE_LLAMA, ok.source)
    }

    @Test
    fun unusableLlmOutputReturnsNull() {
        val fb = GoalParser.parse("learn c", "", null, null, catalog)
        assertNull(GoalParser.fromLlmJson("I think you should study hard", fb, catalog))
        assertNull(GoalParser.fromLlmJson("{\"subjects\":[\"nonsense\"]}", fb, catalog))
        assertNull(GoalParser.fromLlmJson("{broken", fb, catalog))
    }
}
