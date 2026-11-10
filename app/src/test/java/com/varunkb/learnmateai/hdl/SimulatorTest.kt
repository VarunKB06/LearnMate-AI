package com.varunkb.learnmateai.hdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorTest {
    private fun run(vararg src: String, limits: SimLimits = SimLimits()): SimResult =
        VerilogSimulator.simulate(src.mapIndexed { i, s -> SourceFile("f$i.v", s) }, limits)

    private fun ok(r: SimResult) {
        assertTrue(r.compile.diagnostics.joinToString("\n") { it.format() } + "\n" + r.output.joinToString("\n"), r.compile.ok)
    }

    @Test fun andGate() {
        val r = run("""
module and_gate(input a, input b, output y);
  assign y = a & b;
endmodule
module tb;
  reg a, b; wire y;
  and_gate u(.a(a), .b(b), .y(y));
  initial begin
    a=0;b=0; #1 ${'$'}display("%b %b -> %b", a, b, y);
    a=0;b=1; #1 ${'$'}display("%b %b -> %b", a, b, y);
    a=1;b=0; #1 ${'$'}display("%b %b -> %b", a, b, y);
    a=1;b=1; #1 ${'$'}display("%b %b -> %b", a, b, y);
    ${'$'}finish;
  end
endmodule""")
        ok(r)
        assertEquals(listOf("0 0 -> 0", "0 1 -> 0", "1 0 -> 0", "1 1 -> 1"), r.output)
        assertEquals("\$finish", r.finishedBy)
        assertEquals(listOf("tb"), r.compile.topModules)
    }

    @Test fun orAndNotGates() {
        val r = run("""
module g(input a, input b, output o, output n, output x);
  or (o, a, b); not (n, a); xor (x, a, b);
endmodule
module tb; reg a,b; wire o,n,x;
  g u(a,b,o,n,x);
  initial begin
    a=0;b=0;#1 ${'$'}display("%b%b %b%b%b",a,b,o,n,x);
    a=0;b=1;#1 ${'$'}display("%b%b %b%b%b",a,b,o,n,x);
    a=1;b=0;#1 ${'$'}display("%b%b %b%b%b",a,b,o,n,x);
    a=1;b=1;#1 ${'$'}display("%b%b %b%b%b",a,b,o,n,x);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("00 010", "01 111", "10 101", "11 100"), r.output)
        assertEquals("event queue empty", r.finishedBy)
    }

    @Test fun mux4to1() {
        val r = run("""
module mux4(input [3:0] d, input [1:0] s, output reg y);
  always @(*) begin
    case (s)
      2'b00: y = d[0];
      2'b01: y = d[1];
      2'b10: y = d[2];
      default: y = d[3];
    endcase
  end
endmodule
module tb; reg [3:0] d; reg [1:0] s; wire y; integer i;
  mux4 u(d, s, y);
  initial begin
    d = 4'b1010;
    for (i = 0; i < 4; i = i + 1) begin s = i; #1 ${'$'}display("s=%d y=%b", s, y); end
  end
endmodule""")
        ok(r)
        assertEquals(listOf("s=0 y=0", "s=1 y=1", "s=2 y=0", "s=3 y=1"), r.output)
    }

    @Test fun halfAdder() {
        val r = run("""
module ha(input a, b, output s, c); assign s = a ^ b; assign c = a & b; endmodule
module tb; reg a,b; wire s,c; ha u(a,b,s,c);
  initial begin
    {a,b}=2'b00; #1 ${'$'}display("%b%b: s=%b c=%b",a,b,s,c);
    {a,b}=2'b01; #1 ${'$'}display("%b%b: s=%b c=%b",a,b,s,c);
    {a,b}=2'b10; #1 ${'$'}display("%b%b: s=%b c=%b",a,b,s,c);
    {a,b}=2'b11; #1 ${'$'}display("%b%b: s=%b c=%b",a,b,s,c);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("00: s=0 c=0", "01: s=1 c=0", "10: s=1 c=0", "11: s=0 c=1"), r.output)
    }

    @Test fun fullAdderExhaustive() {
        val r = run("""
module fa(input a, b, cin, output sum, cout);
  assign {cout, sum} = a + b + cin;
endmodule
module tb; reg a,b,cin; wire sum,cout; integer i, errors;
  fa u(a,b,cin,sum,cout);
  initial begin
    errors = 0;
    for (i = 0; i < 8; i = i + 1) begin
      {a,b,cin} = i; #1;
      if ({cout,sum} !== a + b + cin) errors = errors + 1;
    end
    ${'$'}display("errors=%0d", errors);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("errors=0"), r.output)
    }

    @Test fun dFlipFlopBehaviour() {
        val r = run("""
module dff(input clk, rst_n, d, output reg q);
  always @(posedge clk or negedge rst_n)
    if (!rst_n) q <= 1'b0; else q <= d;
endmodule
module tb; reg clk, rst_n, d; wire q;
  dff u(clk, rst_n, d, q);
  initial clk = 0;
  always #5 clk = ~clk;
  initial begin
    rst_n = 0; d = 1; #12;
    ${'$'}display("t=%0t q=%b", ${'$'}time, q);
    rst_n = 1; #10;
    ${'$'}display("t=%0t q=%b", ${'$'}time, q);
    d = 0; #10;
    ${'$'}display("t=%0t q=%b", ${'$'}time, q);
    ${'$'}finish;
  end
endmodule""")
        ok(r)
        assertEquals(listOf("t=12 q=0", "t=22 q=1", "t=32 q=0"), r.output)
    }

    @Test fun counterCountsAndWraps() {
        val r = run("""
module counter(input clk, rst, output reg [3:0] count);
  always @(posedge clk) begin
    if (rst) count <= 0; else count <= count + 1;
  end
endmodule
module tb; reg clk, rst; wire [3:0] count; integer i;
  counter u(clk, rst, count);
  initial begin
    clk = 0; rst = 1;
    #1 clk = 1; #1 clk = 0; rst = 0;
    for (i = 0; i < 18; i = i + 1) begin #1 clk = 1; #1 clk = 0; end
    ${'$'}display("count=%d", count);
    ${'$'}display("bin=%b hex=%h", count, count);
  end
endmodule""")
        ok(r)
        assertEquals("count= 2", r.output[0])
        assertEquals("bin=0010 hex=2", r.output[1])
    }

    @Test fun fsmSequenceDetector() {
        val r = run("""
module det(input clk, rst, x, output y);
  parameter S0 = 2'd0, S1 = 2'd1, S2 = 2'd2;
  reg [1:0] state, next;
  always @(posedge clk) if (rst) state <= S0; else state <= next;
  always @(*) begin
    case (state)
      S0: next = x ? S1 : S0;
      S1: next = x ? S1 : S2;
      S2: next = x ? S1 : S0;
      default: next = S0;
    endcase
  end
  assign y = (state == S2) & x;
endmodule
module tb; reg clk, rst, x; wire y; integer i; reg [7:0] pattern;
  det u(clk, rst, x, y);
  initial begin
    clk = 0; rst = 1; x = 0; pattern = 8'b10110101;
    #2 clk = 1; #2 clk = 0; rst = 0;
    for (i = 7; i >= 0; i = i - 1) begin
      x = pattern[i]; #1;
      if (y) ${'$'}display("detected 101 at bit %0d", i);
      #1 clk = 1; #2 clk = 0;
    end
  end
endmodule""")
        ok(r)
        assertEquals(listOf("detected 101 at bit 5", "detected 101 at bit 2", "detected 101 at bit 0"), r.output)
    }

    @Test fun nonBlockingSwapUsesOldValues() {
        val r = run("""
module tb; reg a, b; reg clk;
  initial begin a = 0; b = 1; clk = 0; #1 clk = 1; #1 ${'$'}display("a=%b b=%b", a, b); end
  always @(posedge clk) begin a <= b; b <= a; end
endmodule""")
        ok(r)
        assertEquals(listOf("a=1 b=0"), r.output)
    }

    @Test fun blockingInSameBlockSeesNewValue() {
        val r = run("""
module tb; reg [3:0] a, b;
  initial begin a = 3; b = a + 1; #1 ${'$'}display("%d %d", a, b); end
endmodule""")
        ok(r)
        assertEquals(listOf(" 3  4"), r.output)
    }

    @Test fun unknownsPropagate() {
        val r = run("""
module tb; reg [3:0] a; reg [3:0] b; wire [3:0] s, y;
  assign s = a + b; assign y = a & 4'b0000;
  initial begin
    #1 ${'$'}display("s=%b y=%b", s, y);
    a = 4'd3; b = 4'd4; #1 ${'$'}display("s=%b", s);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("s=xxxx y=0000", "s=0111"), r.output)
    }

    @Test fun memoryAndPartSelects() {
        val r = run("""
module tb; reg [7:0] mem [0:3]; reg [7:0] v; integer i;
  initial begin
    for (i = 0; i < 4; i = i + 1) mem[i] = i * 16 + i;
    v = mem[2];
    ${'$'}display("%h %h %h", v, v[7:4], v[3:0]);
    v[3:0] = 4'hA; ${'$'}display("%h", v);
    v[i-3 +: 2] = 2'b11; ${'$'}display("%b", v);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("22 2 2", "2a", "00101110"), r.output)
    }

    @Test fun concatReplicationAndShifts() {
        val r = run("""
module tb; reg [3:0] a; reg [7:0] y;
  initial begin
    a = 4'b1001;
    y = {2{a}}; ${'$'}display("%b", y);
    y = {a, 4'hF}; ${'$'}display("%h", y);
    y = a << 2; ${'$'}display("%b", y);
    y = a >> 1; ${'$'}display("%b", y);
    ${'$'}display("%b %b %b", &a, |a, ^a);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("10011001", "9f", "00100100", "00000100", "0 1 0"), r.output)
    }

    @Test fun parametersAndInstanceOverride() {
        val r = run("""
module adder #(parameter N = 4) (input [N-1:0] a, b, output [N:0] s);
  assign s = a + b;
endmodule
module tb; reg [7:0] x, y; wire [8:0] s;
  adder #(.N(8)) u(x, y, s);
  initial begin x = 8'd200; y = 8'd100; #1 ${'$'}display("%d", s); end
endmodule""")
        ok(r)
        assertEquals(listOf("300"), r.output)
    }

    @Test fun casezWildcard() {
        val r = run("""
module tb; reg [3:0] r; reg [1:0] g;
  always @(*) casez (r)
    4'b1???: g = 3; 4'b01??: g = 2; 4'b001?: g = 1; default: g = 0;
  endcase
  initial begin
    r = 4'b0000; #1 ${'$'}display("%d", g);
    r = 4'b0010; #1 ${'$'}display("%d", g);
    r = 4'b0111; #1 ${'$'}display("%d", g);
    r = 4'b1000; #1 ${'$'}display("%d", g);
  end
endmodule""")
        ok(r)
        assertEquals(listOf("0", "1", "2", "3"), r.output)
    }

    @Test fun integerLoopCountingDownTerminatesAndSignedPrints() {
        val src = "module tb; integer i; reg [3:0] u;\n" +
            "  initial begin\n" +
            "    for (i = 3; i >= 0; i = i - 1) \$write(\"%0d \", i);\n" +
            "    \$display(\"done i=%0d\", i);\n" +
            "    u = 4'd1; \$display(\"%0d\", u - 4'd2);\n" +
            "  end\nendmodule"
        val r = run(src)
        ok(r)
        assertEquals(listOf("3 2 1 0 done i=-1", "15"), r.output)
        assertEquals("event queue empty", r.finishedBy)
    }

    @Test fun waveformRecordsChanges() {
        val r = run("""
module tb; reg clk; initial begin clk = 0; repeat (4) #5 clk = ~clk; end endmodule""")
        ok(r)
        val clk = r.waveform!!.signals.first { it.name == "tb.clk" }
        assertEquals(listOf(0L to "0", 5L to "1", 10L to "0", 15L to "1", 20L to "0"), clk.changes.map { it.time to it.bits })
    }

    // ---------- compile errors ----------
    @Test fun missingSemicolonReportedWithLine() {
        val r = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a, output y);\n  assign y = a\nendmodule\n")))
        assertFalse(r.ok)
        val d = r.diagnostics.first()
        assertEquals(2, d.line)
        assertTrue(d.message, d.message.contains("';'"))
    }

    @Test fun undeclaredSignal() {
        val r = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a, output y);\n  assign y = a & b;\nendmodule\n")))
        assertFalse(r.ok)
        assertTrue(r.diagnostics.any { it.message.contains("'b' is not declared") && it.line == 2 })
    }

    @Test fun assignToRegAndProceduralWire() {
        val r1 = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a, output reg y);\n assign y = a;\nendmodule")))
        assertTrue(r1.diagnostics.any { it.message.contains("reg") && it.message.contains("assign") })
        val r2 = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a, output y);\n always @(*) y = a;\nendmodule")))
        assertTrue(r2.diagnostics.any { it.message.contains("wire") && it.hint!!.contains("reg") })
    }

    @Test fun unknownModuleAndPort() {
        val r = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a, output y);\n foo u(.a(a));\nendmodule")))
        assertTrue(r.diagnostics.any { it.message.contains("module 'foo' is not defined") })
        val r2 = VerilogSimulator.compile(listOf(SourceFile("a.v", "module k(input a, output y); assign y=a; endmodule\nmodule m(input a, output y);\n k u(.zz(a));\nendmodule")))
        assertTrue(r2.diagnostics.any { it.message.contains("no port named 'zz'") })
    }

    @Test fun unsupportedConstructsGiveClearErrors() {
        for (kw in listOf("function f; input a; f = a; endfunction", "generate endgenerate", "task t; endtask")) {
            val r = VerilogSimulator.compile(listOf(SourceFile("a.v", "module m(input a);\n $kw\nendmodule")))
            assertFalse(kw, r.ok)
            assertTrue(r.diagnostics.first().message, r.diagnostics.first().message.contains("not supported"))
        }
    }

    @Test fun styleWarningsDoNotBlockSimulation() {
        val r = run("""
module m(input clk, input d, output reg q);
  always @(posedge clk) q = d;
endmodule
module tb; reg clk, d; wire q; m u(clk,d,q);
  initial begin clk=0; d=1; #1 clk=1; #1 ${'$'}display("%b", q); end
endmodule""")
        assertTrue(r.compile.ok)
        assertTrue(r.compile.diagnostics.any { it.severity == Severity.Warning })
        assertEquals(listOf("1"), r.output)
    }

    @Test fun noTestbenchIsReported() {
        val r = run("module m(input a, output y); assign y = a; endmodule")
        assertTrue(r.compile.ok)
        assertEquals("not run", r.finishedBy)
        assertEquals(listOf("no testbench (nothing to run)"), r.output)
    }

    // ---------- limits & safety ----------
    @Test fun infiniteZeroDelayLoopHitsStepLimit() {
        val r = run("module tb; reg a; initial begin a = 0; while (1) a = ~a; end endmodule", limits = SimLimits(maxSteps = 100_000))
        assertEquals("step limit", r.finishedBy)
    }

    @Test fun freeRunningClockHitsTimeLimit() {
        val r = run("module tb; reg c; initial c = 0; always #5 c = ~c; endmodule", limits = SimLimits(maxTime = 1000))
        assertEquals("time limit", r.finishedBy)
        assertEquals(1000L, r.endTime)
    }

    @Test fun outputFloodHitsOutputLimit() {
        val r = run("module tb; initial forever #1 ${'$'}display(\"hi\"); endmodule", limits = SimLimits(maxOutputLines = 50))
        assertEquals("output limit", r.finishedBy)
        assertEquals(50, r.output.size)
    }

    @Test fun combinationalOscillationIsStopped() {
        val r = run("module tb; wire a; assign a = ~a; initial #1 ${'$'}display(\"x\"); endmodule", limits = SimLimits(maxSteps = 50_000))
        assertTrue(r.finishedBy, r.finishedBy == "step limit" || r.finishedBy == "event queue empty")
    }

    @Test fun cancellationIsHonoured() {
        var n = 0
        val r = VerilogSimulator.simulate(listOf(SourceFile("a.v", "module tb; reg c; initial c = 0; always #1 c = ~c; endmodule")),
            SimLimits(maxTime = Long.MAX_VALUE / 2, maxSteps = Long.MAX_VALUE / 2, maxWallMillis = 60_000)) { ++n > 3 }
        assertEquals("cancelled", r.finishedBy)
    }

    @Test fun garbageNeverThrows() {
        val rnd = java.util.Random(42)
        val base = "module tb; reg [3:0] a; initial begin a = 4'b1010; #1 \$display(\"%b\", a); end endmodule"
        val alphabet = "();,=<>[]{}#@\$%\"'`01xz abc\n\t&|^~!?:+-*/"
        repeat(400) {
            val sb = StringBuilder(base)
            repeat(1 + rnd.nextInt(6)) {
                val pos = rnd.nextInt(sb.length + 1)
                when (rnd.nextInt(3)) {
                    0 -> sb.insert(pos, alphabet[rnd.nextInt(alphabet.length)])
                    1 -> if (pos < sb.length) sb.deleteCharAt(pos)
                    else -> if (pos < sb.length) sb.setCharAt(pos, alphabet[rnd.nextInt(alphabet.length)])
                }
            }
            val r = VerilogSimulator.simulate(listOf(SourceFile("f.v", sb.toString())), SimLimits(maxSteps = 20_000, maxWallMillis = 2000))
            assertTrue(r.finishedBy.isNotEmpty())
        }
    }

    @Test fun displayFormatting() {
        val r = run("""
module tb; reg [7:0] a; reg [3:0] n;
  initial begin
    a = 8'd5; n = 4'bx01x;
    ${'$'}display("[%d][%0d][%4d][%b][%h][%o]", a, a, a, a, a, a);
    ${'$'}display("[%b][%h][%d]", n, n, n);
    ${'$'}write("ab"); ${'$'}write("cd\n");
    ${'$'}display("100%%");
    ${'$'}display();
  end
endmodule""")
        ok(r)
        assertEquals(listOf("[  5][5][   5][00000101][05][005]", "[x01x][X][ X]", "abcd", "100%", ""), r.output)
    }

    @Test fun monitorPrintsOnChange() {
        val r = run("""
module tb; reg [1:0] a;
  initial begin a = 0; ${'$'}monitor("t=%0t a=%d", ${'$'}time, a); #5 a = 1; #5 a = 1; #5 a = 2; end
endmodule""")
        ok(r)
        assertEquals(3, r.output.size)
        assertTrue(r.output[1].startsWith("t=5"))
    }
}
