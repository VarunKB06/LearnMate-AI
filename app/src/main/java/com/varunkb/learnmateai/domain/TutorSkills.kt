package com.varunkb.learnmateai.domain

/**
 * Rule-based tutor skills that need no model: they explain pasted Verilog line by line, translate
 * compiler diagnostics and simulator output into plain words, and render a lesson at a chosen
 * depth. Everything here is deterministic and unit-tested on the JVM.
 */
object TutorSkills {

    /** Line-by-line explanation of Verilog source using keyword rules. Honest about its limits. */
    fun explainVerilog(code: String): String {
        val lines = code.lines()
        val out = StringBuilder("Line-by-line reading (rule-based, not a compiler):\n")
        var shown = 0
        for ((i, raw) in lines.withIndex()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("//")) continue
            val note = explainLine(t) ?: continue
            out.append("\nLine ${i + 1}: ").append(t.take(70)).append("\n  → ").append(note)
            if (++shown >= 40) { out.append("\n\n(Stopped after 40 lines.)"); break }
        }
        if (shown == 0) out.append("\nI could not recognise any Verilog statements. Paste a full module.")
        return out.toString()
    }

    private fun explainLine(t: String): String? {
        val l = t.removeSuffix(";").trim()
        Regex("^module\\s+(\\w+)").find(l)?.let { return "Starts a module named ${it.groupValues[1]}; a module is one hardware block with ports." }
        if (l.startsWith("endmodule")) return "Ends the module."
        Regex("^(input|output|inout)\\s+(wire|reg|logic)?\\s*(\\[[^\\]]+\\])?\\s*([\\w, ]+)").find(l)?.let {
            val w = it.groupValues[3]
            val width = if (w.isNotEmpty()) " ${w} bits wide" else " 1 bit"
            return "Port declaration: ${it.groupValues[1]} signal(s) ${it.groupValues[4].trim()},$width."
        }
        Regex("^(wire|reg|logic|integer)\\s*(\\[[^\\]]+\\])?\\s*([\\w, ]+)").find(l)?.let {
            val kind = if (it.groupValues[1] == "wire") "a wire (a connection driven by assign or a module output)"
            else "a variable that holds a value, assigned inside always/initial blocks"
            return "Declares ${it.groupValues[3].trim()} as $kind."
        }
        Regex("^parameter\\s+(\\w+)").find(l)?.let { return "Parameter ${it.groupValues[1]}: a compile-time constant." }
        Regex("^assign\\s+(.+)").find(l)?.let { return "Continuous assignment: the left side always follows the right side, like a wire connected to logic: ${it.groupValues[1].take(50)}." }
        Regex("^always\\s*@\\s*\\((.*)\\)").find(l)?.let {
            val s = it.groupValues[1]
            return when {
                s.contains("posedge") || s.contains("negedge") -> "Clocked (sequential) block: runs on the clock edge ($s). Use <= for assignments here."
                s.contains("*") -> "Combinational block: re-runs when any input changes. Use = for assignments here."
                else -> "Block that waits for a change in $s."
            }
        }
        if (l.startsWith("always_ff")) return "SystemVerilog clocked block (flip-flops)."
        if (l.startsWith("always_comb")) return "SystemVerilog combinational block."
        if (l.startsWith("initial")) return "Runs once at time 0; used in testbenches to apply stimulus."
        if (l.startsWith("begin")) return "Opens a group of statements."
        if (l == "end") return "Closes the group of statements."
        Regex("^if\\s*\\((.*)\\)").find(l)?.let { return "Condition: do the following only when (${it.groupValues[1]}) is true." }
        if (l.startsWith("else")) return "Otherwise branch of the if above."
        Regex("^(case|casez|casex)\\s*\\((.*)\\)").find(l)?.let { return "Multi-way choice on ${it.groupValues[2]}; the matching branch runs." }
        if (l.startsWith("endcase")) return "Ends the case statement."
        if (l.startsWith("default")) return "Branch used when no other case matches."
        Regex("^for\\s*\\(").find(l)?.let { return "Loop; repeats its body while the condition holds." }
        Regex("^#\\s*(\\d+)").find(l)?.let { return "Wait ${it.groupValues[1]} time units before the next statement." }
        Regex("^forever").find(l)?.let { return "Repeats forever (typically a clock generator)." }
        if (l.startsWith("\$display")) return "Prints a line to the console."
        if (l.startsWith("\$monitor")) return "Prints whenever a listed signal changes."
        if (l.startsWith("\$finish")) return "Ends the simulation."
        if (l.startsWith("\$dumpvars") || l.startsWith("\$dumpfile")) return "Records waveform data."
        Regex("^(\\w[\\w\\[\\]:. ]*?)\\s*<=\\s*(.+)").find(l)?.let { return "Non-blocking assignment: ${it.groupValues[1]} updates at the end of the time step (flip-flop style)." }
        Regex("^(\\w[\\w\\[\\]:. ]*?)\\s*=\\s*(.+)").find(l)?.let { return "Blocking assignment: ${it.groupValues[1]} updates immediately, in order." }
        Regex("^(\\w+)\\s+(#\\(.*\\)\\s*)?(\\w+)\\s*\\(").find(l)?.let {
            if (it.groupValues[1] !in setOf("if", "for", "while", "case")) return "Creates an instance named ${it.groupValues[3]} of module ${it.groupValues[1]}."
        }
        return null
    }

    private data class ErrRule(val pattern: Regex, val text: String)

    private val errorRules = listOf(
        ErrRule(Regex("(?i)expected ';'|missing ';'"), "A statement is missing its closing semicolon. Check the end of that line or the one before it."),
        ErrRule(Regex("(?i)undeclared|not declared|unknown (signal|identifier)"), "A name is used but never declared. Declare it as wire/reg, or fix a spelling difference (Verilog is case-sensitive)."),
        ErrRule(Regex("(?i)assign.*reg|reg.*assign|cannot assign.*continuous"), "assign can only drive a wire. Declare the target as wire, or drive a reg inside an always block."),
        ErrRule(Regex("(?i)wire.*(always|procedural)|procedural.*wire"), "Inside always/initial you can only assign reg (or logic) variables. Change the declaration to reg."),
        ErrRule(Regex("(?i)unsupported"), "This simulator supports a documented subset of Verilog. Rewrite using supported constructs (see Verilog Studio help)."),
        ErrRule(Regex("(?i)expected 'endmodule'|missing endmodule"), "The module is not closed. Add endmodule at the end."),
        ErrRule(Regex("(?i)expected 'end'|missing end"), "A begin block is not closed. Every begin needs a matching end."),
        ErrRule(Regex("(?i)already declared|duplicate"), "The same name is declared twice. Remove or rename one."),
        ErrRule(Regex("(?i)width|mismatch"), "Signal widths differ. Check the [msb:lsb] of both sides; extra bits are cut and missing bits are zero-filled."),
        ErrRule(Regex("(?i)module.*not found|unknown module|undefined module"), "An instance names a module that is not in any project file. Add the file or fix the name."),
        ErrRule(Regex("(?i)port"), "A port connection problem: check the number, names and order of ports in the instance against the module header."),
        ErrRule(Regex("(?i)blocking.*clock"), "Style warning: use <= (non-blocking) in clocked always blocks to avoid race conditions."),
        ErrRule(Regex("(?i)nonblocking.*comb|non-blocking.*comb"), "Style warning: use = in combinational always blocks."),
        ErrRule(Regex("(?i)case.*default"), "Style warning: a case without default may infer a latch. Add default."),
        ErrRule(Regex("(?i)time limit|step limit|exceed"), "The run hit a safety limit, often a missing \$finish or a loop without a delay. Add \$finish or #delay.")
    )

    fun looksLikeDiagnostic(text: String): Boolean =
        Regex("(?i)\\b(error|warning)\\b").containsMatchIn(text) &&
            (Regex("(?i)line\\s*\\d+|:\\d+").containsMatchIn(text) || text.lines().size <= 4 && errorRules.any { it.pattern.containsMatchIn(text) })

    fun explainDiagnostic(text: String): String {
        val matched = errorRules.filter { it.pattern.containsMatchIn(text) }
        val line = Regex("(?i)line\\s*(\\d+)|:(\\d+)").find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        val sb = StringBuilder()
        if (line != null) sb.append("The message points at line $line. The real mistake is often on that line or just above it.\n\n")
        if (matched.isEmpty()) {
            sb.append("I do not recognise this message. Read it left to right: file, line, then the description. Fix the first error first, because later ones are often caused by it.")
        } else {
            matched.take(2).forEach { sb.append("• ").append(it.text).append('\n') }
            sb.append("\nFix the first error and recompile; later errors are often side effects.")
        }
        return sb.toString().trim()
    }

    /** Plain-language reading of console output from a run. */
    fun explainSimulation(output: String): String {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return "There is no output yet. Compile and Run first; add \$display or \$monitor to see values."
        val sb = StringBuilder("What the output says:\n")
        val fails = lines.filter { Regex("(?i)\\b(fail|mismatch|error)\\b").containsMatchIn(it) }
        val passes = lines.filter { Regex("(?i)\\bpass").containsMatchIn(it) }
        val xs = lines.count { Regex("\\bx\\b|=x|'bx|:x").containsMatchIn(it) }
        sb.append("• ${lines.size} line(s) of output.\n")
        if (passes.isNotEmpty()) sb.append("• The testbench reports PASS: ${passes.first().take(60)}.\n")
        if (fails.isNotEmpty()) sb.append("• Problem line: ${fails.first().take(80)}. Compare the printed value with the expected one and check the logic that drives it.\n")
        if (xs > 0) sb.append("• $xs line(s) contain x (unknown). Usually a reg that was never given a value, e.g. no reset before the clock starts.\n")
        if (lines.any { it.contains("time", true) || Regex("^\\d+").containsMatchIn(it) }) sb.append("• Numbers at the start of a line are simulation times; read down to see how signals change.\n")
        if (fails.isEmpty() && passes.isEmpty()) sb.append("• No pass/fail line. Have the testbench print one so results are clear.\n")
        return sb.toString().trim()
    }

    enum class Depth { Simple, Normal, Advanced, Example }

    fun renderLesson(l: Lesson, depth: Depth): String = when (depth) {
        Depth.Simple -> {
            val first = l.body.split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" ")
            val pts = l.summary.take(3).joinToString("\n") { "• $it" }
            "In simple words: $first" + if (pts.isNotEmpty()) "\n\nRemember:\n$pts" else ""
        }
        Depth.Example -> {
            if (l.example.isNullOrBlank()) "This lesson has no worked example. Try its practice questions instead."
            else "Example:\n${l.example}" + (l.exampleNote?.let { "\n\nWhat to notice: $it" } ?: "")
        }
        Depth.Advanced -> buildString {
            append(l.body)
            if (l.definitions.isNotEmpty()) append("\n\nKey terms:\n").append(l.definitions.take(6).joinToString("\n") { "• ${it.term}: ${it.meaning}" })
            if (l.mistakes.isNotEmpty()) append("\n\nCommon mistakes:\n").append(l.mistakes.take(4).joinToString("\n") { "• $it" })
            if (l.exercises.isNotEmpty()) append("\n\nTry this:\n").append(l.exercises.take(2).joinToString("\n") { "• $it" })
        }
        Depth.Normal -> buildString {
            append(l.body)
            if (!l.example.isNullOrBlank()) append("\n\nExample:\n").append(l.example)
        }
    }

    fun practice(l: Lesson, count: Int = 3): String {
        if (l.practice.isEmpty()) return "No practice questions are stored for ${l.title}. Take a quiz on ${l.topic} instead."
        val qs = l.practice.take(count)
        return "Practice questions on ${l.title} (answers are below, try first!):\n\n" +
            qs.mapIndexed { i, p -> "${i + 1}. ${p.question}" }.joinToString("\n") +
            "\n\nAnswers:\n" + qs.mapIndexed { i, p -> "${i + 1}. ${p.answer}" }.joinToString("\n")
    }

    fun compare(a: Lesson, b: Lesson): String = buildString {
        append("${a.title} vs ${b.title}\n\n")
        append("${a.title}: ").append(firstSentences(a.body, 2)).append("\n\n")
        append("${b.title}: ").append(firstSentences(b.body, 2))
        val da = a.definitions.firstOrNull(); val db = b.definitions.firstOrNull()
        if (da != null && db != null) append("\n\nKey terms: ${da.term} - ${da.meaning} | ${db.term} - ${db.meaning}")
    }

    private fun firstSentences(s: String, n: Int) = s.split(Regex("(?<=[.!?])\\s+")).take(n).joinToString(" ")
}
