package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSkillsTest {
    private val lessons = TestContent.lessons
    private val tutor = RetrievalTutorEngine(Bm25Index(lessons), lessons)

    private fun ctx(last: String? = null) =
        LearnerContext("Asha", "c", "C Programming", 0, 0, emptyList(), last)

    @Test fun explainsPastedVerilogLineByLine() {
        val code = "module top(input a, input b, output y);\n  assign y = a & b;\nendmodule"
        val r = tutor.answer(code, ctx())
        assertEquals(ReplyKind.Code, r.kind)
        assertTrue(r.text.contains("Starts a module named top"))
        assertTrue(r.text.contains("Continuous assignment"))
        assertTrue(r.text.contains("Ends the module"))
    }

    @Test fun explainsClockedBlockAndNonBlocking() {
        val t = TutorSkills.explainVerilog("always @(posedge clk) begin\n q <= d;\nend")
        assertTrue(t.contains("Clocked"))
        assertTrue(t.contains("Non-blocking"))
    }

    @Test fun explainsCompilerDiagnostic() {
        val r = tutor.answer("error: top.v line 5: signal 'foo' is undeclared", ctx())
        assertEquals(ReplyKind.Diagnostic, r.kind)
        assertTrue(r.text.contains("line 5"))
        assertTrue(r.text.contains("never declared"))
    }

    @Test fun unknownDiagnosticIsAdmitted() {
        val t = TutorSkills.explainDiagnostic("error: line 2: zebra flux capacitor")
        assertTrue(t.contains("do not recognise"))
    }

    @Test fun explainsSimulationOutput() {
        val t = TutorSkills.explainSimulation("0 a=0 y=x\n10 a=1 y=1\nPASS: all checks")
        assertTrue(t.contains("PASS"))
        assertTrue(t.contains("x (unknown)"))
        assertTrue(TutorSkills.explainSimulation("").contains("no output"))
    }

    @Test fun followUpExampleUsesLastLesson() {
        val first = tutor.answer("Explain pointers in C", ctx())
        assertEquals("c-pointers", first.lessonId)
        val r = tutor.answer("give an example", ctx(first.lessonId))
        assertEquals(ReplyKind.FollowUp, r.kind)
        assertEquals("c-pointers", r.lessonId)
    }

    @Test fun simplerAndAdvancedDiffer() {
        val simple = tutor.answer("explain pointers simply", ctx())
        val adv = tutor.answer("explain pointers in depth", ctx())
        assertTrue(simple.text.startsWith("In simple words"))
        assertTrue(adv.text.length >= simple.text.length)
    }

    @Test fun practiceQuestionsComeFromLesson() {
        val r = tutor.answer("give me practice questions on pointers", ctx())
        assertEquals(ReplyKind.Practice, r.kind)
        assertEquals("c-pointers", r.lessonId)
    }

    @Test fun compareTwoConcepts() {
        val r = tutor.answer("difference between pointers and recursion", ctx())
        assertEquals(ReplyKind.Compare, r.kind)
        assertTrue(r.text.contains(" vs "))
    }

    @Test fun compareWithUnknownConceptDoesNotGuess() {
        val r = tutor.answer("difference between quantum entanglement and pointers", ctx())
        assertEquals(ReplyKind.NoMatch, r.kind)
    }

    @Test fun followUpWithoutContextAsksForTopic() {
        val r = tutor.answer("give an example", ctx())
        assertEquals(ReplyKind.NoMatch, r.kind)
    }
}
