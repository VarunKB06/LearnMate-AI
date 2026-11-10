package com.varunkb.learnmateai.hdl

/** Starter projects for HDL Studio. Every template compiles and its testbench prints PASS (checked by unit tests). */
object HdlTemplates {
    data class Template(
        val id: String,
        val title: String,
        val description: String,
        val files: List<SourceFile>,
        val tbFileNames: Set<String>
    )

    private fun s(t: String) = t.trimIndent().trim().replace('¤', '$') + "\n"

    private fun t(id: String, title: String, desc: String, design: String, tb: String) = Template(
        id, title, desc,
        listOf(SourceFile("$id.v", s(design)), SourceFile("${id}_tb.v", s(tb))),
        setOf("${id}_tb.v")
    )

    val all: List<Template> = listOf(
        Template("blank", "Blank project", "An empty design and testbench to start from.",
            listOf(SourceFile("design.v", s("""
                module top(input a, output y);
                  assign y = a;
                endmodule
            """)), SourceFile("tb.v", s("""
                module tb;
                  reg a; wire y;
                  top dut(.a(a), .y(y));
                  initial begin
                    a = 0; #1 ¤display("a=%b y=%b", a, y);
                    a = 1; #1 ¤display("a=%b y=%b", a, y);
                  end
                endmodule
            """))), setOf("tb.v")),

        t("and_gate", "AND gate", "Two-input AND with an exhaustive truth-table testbench.", """
            module and_gate(input a, input b, output y);
              assign y = a & b;
            endmodule
        """, """
            module and_gate_tb;
              reg a, b; wire y; integer i, errors;
              and_gate dut(.a(a), .b(b), .y(y));
              initial begin
                errors = 0;
                for (i = 0; i < 4; i = i + 1) begin
                  {a, b} = i; #1;
                  ¤display("a=%b b=%b y=%b", a, b, y);
                  if (y !== (a & b)) errors = errors + 1;
                end
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL: %0d errors", errors);
              end
            endmodule
        """),

        t("basic_gates", "OR, NOT, XOR gates", "Gate primitives (or, not, xor) with a truth-table check.", """
            module basic_gates(input a, input b, output o, output n, output x);
              or  g1(o, a, b);
              not g2(n, a);
              xor g3(x, a, b);
            endmodule
        """, """
            module basic_gates_tb;
              reg a, b; wire o, n, x; integer i, errors;
              basic_gates dut(a, b, o, n, x);
              initial begin
                errors = 0;
                for (i = 0; i < 4; i = i + 1) begin
                  {a, b} = i; #1;
                  ¤display("a=%b b=%b | or=%b not_a=%b xor=%b", a, b, o, n, x);
                  if (o !== (a | b) || n !== ~a || x !== (a ^ b)) errors = errors + 1;
                end
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
              end
            endmodule
        """),

        t("mux4", "4-to-1 multiplexer", "A case-based combinational multiplexer.", """
            module mux4(input [3:0] d, input [1:0] sel, output reg y);
              always @(*) begin
                case (sel)
                  2'b00: y = d[0];
                  2'b01: y = d[1];
                  2'b10: y = d[2];
                  default: y = d[3];
                endcase
              end
            endmodule
        """, """
            module mux4_tb;
              reg [3:0] d; reg [1:0] sel; wire y; integer i, errors;
              mux4 dut(.d(d), .sel(sel), .y(y));
              initial begin
                errors = 0; d = 4'b1010;
                for (i = 0; i < 4; i = i + 1) begin
                  sel = i; #1;
                  ¤display("sel=%d y=%b", sel, y);
                  if (y !== d[sel]) errors = errors + 1;
                end
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
              end
            endmodule
        """),

        t("half_adder", "Half adder", "Sum and carry from two bits.", """
            module half_adder(input a, input b, output sum, output carry);
              assign sum = a ^ b;
              assign carry = a & b;
            endmodule
        """, """
            module half_adder_tb;
              reg a, b; wire sum, carry; integer i, errors;
              half_adder dut(.a(a), .b(b), .sum(sum), .carry(carry));
              initial begin
                errors = 0;
                for (i = 0; i < 4; i = i + 1) begin
                  {a, b} = i; #1;
                  ¤display("%b + %b = carry %b sum %b", a, b, carry, sum);
                  if ({carry, sum} !== a + b) errors = errors + 1;
                end
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
              end
            endmodule
        """),

        t("full_adder", "Full adder", "Adds three bits using one expression with a carry-out.", """
            module full_adder(input a, input b, input cin, output sum, output cout);
              assign {cout, sum} = a + b + cin;
            endmodule
        """, """
            module full_adder_tb;
              reg a, b, cin; wire sum, cout; integer i, errors;
              full_adder dut(.a(a), .b(b), .cin(cin), .sum(sum), .cout(cout));
              initial begin
                errors = 0;
                for (i = 0; i < 8; i = i + 1) begin
                  {a, b, cin} = i; #1;
                  ¤display("%b+%b+%b = cout %b sum %b", a, b, cin, cout, sum);
                  if ({cout, sum} !== a + b + cin) errors = errors + 1;
                end
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
              end
            endmodule
        """),

        t("dff", "D flip-flop", "Edge-triggered flip-flop with asynchronous active-low reset.", """
            module dff(input clk, input rst_n, input d, output reg q);
              always @(posedge clk or negedge rst_n) begin
                if (!rst_n) q <= 1'b0;
                else q <= d;
              end
            endmodule
        """, """
            module dff_tb;
              reg clk, rst_n, d; wire q; integer errors;
              dff dut(.clk(clk), .rst_n(rst_n), .d(d), .q(q));
              initial clk = 0;
              always #5 clk = ~clk;
              initial begin
                errors = 0; rst_n = 0; d = 1; #12;
                if (q !== 1'b0) errors = errors + 1;
                rst_n = 1; #10;
                ¤display("t=%0t q=%b (d was 1)", ¤time, q);
                if (q !== 1'b1) errors = errors + 1;
                d = 0; #10;
                ¤display("t=%0t q=%b (d was 0)", ¤time, q);
                if (q !== 1'b0) errors = errors + 1;
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
                ¤finish;
              end
            endmodule
        """),

        t("counter", "4-bit counter", "Synchronous counter with reset that wraps from 15 to 0.", """
            module counter(input clk, input rst, output reg [3:0] count);
              always @(posedge clk) begin
                if (rst) count <= 4'd0;
                else count <= count + 4'd1;
              end
            endmodule
        """, """
            module counter_tb;
              reg clk, rst; wire [3:0] count; integer errors, i;
              counter dut(.clk(clk), .rst(rst), .count(count));
              initial clk = 0;
              always #5 clk = ~clk;
              initial begin
                errors = 0; rst = 1; #12; rst = 0;
                for (i = 1; i <= 18; i = i + 1) begin
                  @(posedge clk); #1;
                  if (count !== i % 16) errors = errors + 1;
                end
                ¤display("count after 18 clocks = %0d", count);
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
                ¤finish;
              end
            endmodule
        """),

        t("shift_reg", "Shift register", "4-bit serial-in shift register.", """
            module shift_reg(input clk, input rst, input din, output reg [3:0] q);
              always @(posedge clk) begin
                if (rst) q <= 4'b0000;
                else q <= {q[2:0], din};
              end
            endmodule
        """, """
            module shift_reg_tb;
              reg clk, rst, din; wire [3:0] q; integer errors;
              shift_reg dut(.clk(clk), .rst(rst), .din(din), .q(q));
              initial clk = 0;
              always #5 clk = ~clk;
              initial begin
                errors = 0; rst = 1; din = 0; #12; rst = 0;
                din = 1; @(posedge clk); #1;
                din = 0; @(posedge clk); #1;
                din = 1; @(posedge clk); #1;
                din = 1; @(posedge clk); #1;
                ¤display("q = %b", q);
                if (q !== 4'b1011) errors = errors + 1;
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
                ¤finish;
              end
            endmodule
        """),

        t("fsm_101", "FSM: detect 101", "Three-state Mealy detector for the bit pattern 1-0-1 (overlap allowed).", """
            module fsm_101(input clk, input rst, input x, output y);
              parameter S0 = 2'd0, S1 = 2'd1, S2 = 2'd2;
              reg [1:0] state, next;
              always @(posedge clk) begin
                if (rst) state <= S0;
                else state <= next;
              end
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
        """, """
            module fsm_101_tb;
              reg clk, rst, x; wire y; integer i, hits; reg [7:0] pattern;
              fsm_101 dut(.clk(clk), .rst(rst), .x(x), .y(y));
              initial begin
                clk = 0; rst = 1; x = 0; hits = 0; pattern = 8'b10110101;
                #2 clk = 1; #2 clk = 0; rst = 0;
                for (i = 7; i >= 0; i = i - 1) begin
                  x = pattern[i]; #1;
                  if (y) begin hits = hits + 1; ¤display("pattern found at bit %0d", i); end
                  #1 clk = 1; #2 clk = 0;
                end
                if (hits == 3) ¤display("PASS"); else ¤display("FAIL: hits=%0d", hits);
              end
            endmodule
        """),

        t("alu4", "4-bit ALU", "Add, subtract, AND and OR selected by a 2-bit opcode.", """
            module alu4(input [3:0] a, input [3:0] b, input [1:0] op, output reg [3:0] y);
              always @(*) begin
                case (op)
                  2'b00: y = a + b;
                  2'b01: y = a - b;
                  2'b10: y = a & b;
                  default: y = a | b;
                endcase
              end
            endmodule
        """, """
            module alu4_tb;
              reg [3:0] a, b; reg [1:0] op; wire [3:0] y; integer errors; reg [3:0] expect_y;
              alu4 dut(.a(a), .b(b), .op(op), .y(y));
              initial begin
                errors = 0; a = 4'd9; b = 4'd5;
                op = 0; #1; ¤display("add: %d", y); if (y !== 4'd14) errors = errors + 1;
                op = 1; #1; ¤display("sub: %d", y); if (y !== 4'd4) errors = errors + 1;
                op = 2; #1; ¤display("and: %b", y); if (y !== 4'b0001) errors = errors + 1;
                op = 3; #1; ¤display("or : %b", y); if (y !== 4'b1101) errors = errors + 1;
                if (errors == 0) ¤display("PASS"); else ¤display("FAIL");
              end
            endmodule
        """)
    )
}
