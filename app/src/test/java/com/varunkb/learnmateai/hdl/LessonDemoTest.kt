package com.varunkb.learnmateai.hdl

import com.varunkb.learnmateai.domain.TestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every runnable example embedded in a lesson must compile, finish by itself and print PASS. */
class LessonDemoTest {
    @Test fun everyLessonDemoRunsAndPasses() {
        var count = 0
        for (l in TestContent.lessons) {
            val h = l.hdl ?: continue
            count++
            assertTrue("${l.id}: needs a testbench file", h.files.any { it.isTestbench })
            val r = VerilogSimulator.simulate(h.files.map { SourceFile(it.name, it.text) })
            val msg = "${l.id}: " + r.compile.diagnostics.joinToString { it.format() } + " | " + r.output.joinToString(" / ") + " | " + r.finishedBy
            assertTrue(msg, r.compile.ok)
            assertTrue(msg, r.output.any { it.startsWith("PASS") })
            assertFalse(msg, r.output.any { it.startsWith("FAIL") || it.startsWith("ERROR") })
            assertTrue(msg, r.finishedBy == "\$finish" || r.finishedBy == "event queue empty")
        }
        println("LESSON DEMOS RUN: $count")
        assertTrue(count > 0)
    }
}
