package com.varunkb.learnmateai.hdl

/** Re-indents Verilog by begin/end, case/endcase and module/endmodule nesting. Does not reflow expressions. */
object VerilogFormatter {
    private val open = Regex("\\b(begin|case|casez|casex|module)\\b")
    private val close = Regex("\\b(end|endcase|endmodule)\\b")
    private val startsCloser = Regex("^(end|endcase|endmodule)\\b")
    private val needsBody = Regex("^(if\\b.*\\)|else if\\b.*\\)|else|for\\b.*\\)|while\\b.*\\)|always.*\\)|always_comb|always_ff.*\\)|initial|forever|repeat\\b.*\\))$")

    fun format(source: String, indent: String = "  "): String {
        val out = ArrayList<String>()
        var level = 0
        var pending = 0
        var inBlock = false
        var blank = 0
        for (raw in source.replace("\t", indent).split("\n")) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (inBlock) {
                out.add(if (trimmed.isEmpty()) "" else indent.repeat(maxOf(level, 0)) + " " + trimmed)
                if (trimmed.contains("*/")) inBlock = false
                continue
            }
            if (trimmed.isEmpty()) { blank++; if (blank <= 1) out.add(""); continue }
            blank = 0
            val code = stripComments(trimmed)
            if (trimmed.startsWith("/*") && !trimmed.contains("*/")) inBlock = true
            if (code.isEmpty()) { out.add(indent.repeat(maxOf(level + pending, 0)) + trimmed); continue }
            val opens = open.findAll(code).count()
            val closes = close.findAll(code).count()
            val dedent = if (startsCloser.containsMatchIn(code)) 1 else 0
            val startsBegin = code.startsWith("begin")
            val printLevel = maxOf(level - dedent + (if (startsBegin || dedent == 1) 0 else pending), 0)
            out.add(indent.repeat(printLevel) + trimmed)
            level = maxOf(level + opens - closes, 0)
            pending = if (needsBody.matches(code)) 1 else if (startsBegin) 0 else if (pending == 1 && !code.endsWith("begin")) 0 else 0
            if (needsBody.matches(code) && code.endsWith("begin")) pending = 0
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return out.joinToString("\n") + "\n"
    }

    /** Code part of a line: comments and string contents removed. */
    private fun stripComments(s: String): String {
        val sb = StringBuilder()
        var i = 0
        var inStr = false
        while (i < s.length) {
            val c = s[i]
            if (inStr) { if (c == '"') inStr = false; i++; continue }
            if (c == '"') { inStr = true; i++; continue }
            if (c == '/' && i + 1 < s.length && s[i + 1] == '/') break
            if (c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                val e = s.indexOf("*/", i + 2)
                if (e < 0) break
                i = e + 2; continue
            }
            sb.append(c); i++
        }
        return sb.toString().trim()
    }
}

object VerilogHighlighter {
    enum class Kind { Keyword, Number, Comment, String, SystemTask, Directive }
    data class Span(val start: Int, val end: Int, val kind: Kind)

    private val keywords = setOf(
        "module", "endmodule", "input", "output", "inout", "wire", "reg", "logic", "integer", "parameter", "localparam",
        "assign", "initial", "always", "always_comb", "always_ff", "always_latch", "begin", "end", "if", "else",
        "case", "casez", "casex", "endcase", "default", "for", "while", "repeat", "forever", "posedge", "negedge", "or",
        "and", "nand", "nor", "xor", "xnor", "not", "buf", "function", "endfunction", "task", "endtask", "generate", "endgenerate",
        "genvar", "signed", "unsigned", "time", "real"
    )

    fun spans(text: String): List<Span> {
        val out = ArrayList<Span>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    var e = i; while (e < n && text[e] != '\n') e++
                    out.add(Span(i, e, Kind.Comment)); i = e
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val e = text.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                    out.add(Span(i, e, Kind.Comment)); i = e
                }
                c == '"' -> {
                    var e = i + 1
                    while (e < n && text[e] != '"' && text[e] != '\n') { if (text[e] == '\\' && e + 1 < n) e++; e++ }
                    if (e < n && text[e] == '"') e++
                    out.add(Span(i, minOf(e, n), Kind.String)); i = minOf(e, n)
                }
                c == '`' -> {
                    var e = i + 1; while (e < n && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    out.add(Span(i, e, Kind.Directive)); i = e
                }
                c == '$' -> {
                    var e = i + 1; while (e < n && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    out.add(Span(i, e, Kind.SystemTask)); i = e
                }
                c.isDigit() || (c == '\'' && i + 1 < n && "bBoOdDhH".indexOf(text[i + 1]) >= 0) -> {
                    var e = i
                    while (e < n && (text[e].isLetterOrDigit() || text[e] == '_' || text[e] == '\'' || text[e] == '?')) e++
                    out.add(Span(i, e, Kind.Number)); i = e
                }
                c.isLetter() || c == '_' -> {
                    var e = i; while (e < n && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    if (text.substring(i, e) in keywords) out.add(Span(i, e, Kind.Keyword))
                    i = e
                }
                else -> i++
            }
        }
        return out
    }
}

/** Value Change Dump export so waveforms can be opened in GTKWave or similar tools. */
object VcdWriter {
    fun write(wave: Waveform, timescale: String = "1ns"): String {
        val sb = StringBuilder()
        sb.append("\$date LearnMate AI \$end\n\$version LearnMate AI Verilog simulator \$end\n\$timescale $timescale \$end\n")
        fun id(k: Int): String {
            var n = k; val s = StringBuilder()
            do { s.append((33 + n % 94).toChar()); n /= 94 } while (n > 0)
            return s.toString()
        }
        val sigs = wave.signals.sortedBy { it.name }
        var stack = emptyList<String>()
        sigs.forEachIndexed { k, s ->
            val parts = s.name.split(".")
            val scope = parts.dropLast(1)
            var common = 0
            while (common < stack.size && common < scope.size && stack[common] == scope[common]) common++
            repeat(stack.size - common) { sb.append("\$upscope \$end\n") }
            for (j in common until scope.size) sb.append("\$scope module ${scope[j]} \$end\n")
            stack = scope
            val range = if (s.width > 1) " [${s.width - 1}:0]" else ""
            sb.append("\$var wire ${s.width} ${id(k)} ${parts.last()}$range \$end\n")
        }
        repeat(stack.size) { sb.append("\$upscope \$end\n") }
        sb.append("\$enddefinitions \$end\n")
        data class Ev(val t: Long, val id: String, val bits: String, val w: Int)
        val events = ArrayList<Ev>()
        sigs.forEachIndexed { k, s -> s.changes.forEach { events.add(Ev(it.time, id(k), it.bits, s.width)) } }
        events.sortBy { it.t }
        fun line(e: Ev) = if (e.w == 1) "${e.bits}${e.id}\n" else "b${e.bits} ${e.id}\n"
        sb.append("#0\n\$dumpvars\n")
        var i = 0
        while (i < events.size && events[i].t == 0L) { sb.append(line(events[i])); i++ }
        sb.append("\$end\n")
        var last = 0L
        while (i < events.size) {
            if (events[i].t != last) { last = events[i].t; sb.append("#$last\n") }
            sb.append(line(events[i])); i++
        }
        if (wave.endTime > last) sb.append("#${wave.endTime}\n")
        return sb.toString()
    }
}
