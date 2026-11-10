package com.varunkb.learnmateai.hdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {
    @Test fun everyTemplateCompilesRunsAndPasses() {
        assertTrue(HdlTemplates.all.size >= 10)
        for (t in HdlTemplates.all) {
            val r = VerilogSimulator.simulate(t.files)
            val msg = "${t.id}: " + r.compile.diagnostics.joinToString { it.format() } + r.output
            assertTrue(msg, r.compile.ok)
            assertTrue(msg, r.output.isNotEmpty())
            assertFalse(msg, r.output.any { it.startsWith("FAIL") || it.startsWith("ERROR") })
            if (t.id != "blank") assertTrue(msg, r.output.contains("PASS"))
            assertTrue("${t.id} ${r.finishedBy}", r.finishedBy == "\$finish" || r.finishedBy == "event queue empty")
            assertTrue(t.id, t.tbFileNames.all { n -> t.files.any { it.name == n } })
        }
    }

    @Test fun templatesHaveNoWarnings() {
        for (t in HdlTemplates.all) {
            val d = VerilogSimulator.compile(t.files).diagnostics
            assertTrue("${t.id}: ${d.joinToString { it.format() }}", d.isEmpty())
        }
    }

    @Test fun formatterReindentsAndIsIdempotent() {
        val messy = "module m(input a, output reg y);\nalways @(*) begin\nif (a)\ny = 1;\nelse begin\ny = 0;\nend\nend\nendmodule\n"
        val f = VerilogFormatter.format(messy)
        assertEquals(
            "module m(input a, output reg y);\n  always @(*) begin\n    if (a)\n      y = 1;\n    else begin\n      y = 0;\n    end\n  end\nendmodule\n", f
        )
        assertEquals(f, VerilogFormatter.format(f))
    }

    @Test fun formatterKeepsMeaning() {
        for (t in HdlTemplates.all) {
            val formatted = t.files.map { SourceFile(it.name, VerilogFormatter.format(it.text)) }
            val a = VerilogSimulator.simulate(t.files).output
            val b = VerilogSimulator.simulate(formatted).output
            assertEquals(t.id, a, b)
            formatted.forEach { assertEquals(it.text, VerilogFormatter.format(it.text)) }
        }
    }

    @Test fun highlighterFindsTokens() {
        val text = "module m; // note\n  reg [3:0] a = 4'b1010; \$display(\"hi\"); /* c */\nendmodule"
        val sp = VerilogHighlighter.spans(text)
        fun kinds(k: VerilogHighlighter.Kind) = sp.filter { it.kind == k }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("module", "reg", "endmodule"), kinds(VerilogHighlighter.Kind.Keyword))
        assertEquals(listOf("// note", "/* c */"), kinds(VerilogHighlighter.Kind.Comment))
        assertEquals(listOf("\"hi\""), kinds(VerilogHighlighter.Kind.String))
        assertEquals(listOf("\$display"), kinds(VerilogHighlighter.Kind.SystemTask))
        assertTrue(kinds(VerilogHighlighter.Kind.Number).containsAll(listOf("3", "0", "4'b1010")))
    }

    @Test fun highlighterSurvivesUnterminatedInput() {
        for (s in listOf("\"abc", "/* open", "4'", "`", "\$", "")) VerilogHighlighter.spans(s)
    }

    @Test fun vcdContainsHeaderAndChanges() {
        val r = VerilogSimulator.simulate(listOf(SourceFile("a.v", "module tb; reg clk; reg [1:0] c; initial begin clk=0; c=0; #5 clk=1; c=2; end endmodule")))
        val vcd = VcdWriter.write(r.waveform!!)
        assertTrue(vcd.contains("\$enddefinitions"))
        assertTrue(vcd.contains("\$scope module tb \$end"))
        assertTrue(vcd.contains("\$var wire 2 "))
        assertTrue(vcd.contains("#5"))
        assertTrue(vcd.contains("b10 "))
    }
}
