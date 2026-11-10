package com.varunkb.learnmateai.hdl

class VError(val line: Int, val col: Int, message: String, val hint: String? = null) : Exception(message)

enum class T { Id, Num, Str, Sym, Sys, Eof }

class Tok(val t: T, val s: String, val line: Int, val col: Int, val num: V? = null, val sized: Boolean = false, val zmask: Long = 0L) {
    override fun toString() = if (t == T.Eof) "end of file" else "'$s'"
}

/** Handles `define / `timescale and similar directives line by line; line numbers are preserved. */
object VPreprocessor {
    fun run(text: String): String {
        val macros = HashMap<String, String>()
        val out = StringBuilder()
        val lines = text.split("\n")
        for ((i, raw) in lines.withIndex()) {
            val trimmed = raw.trimStart()
            if (trimmed.startsWith("`")) {
                val word = trimmed.drop(1).takeWhile { it.isLetterOrDigit() || it == '_' }
                when (word) {
                    "timescale", "default_nettype", "resetall", "celldefine", "endcelldefine" -> { out.append('\n'); continue }
                    "define" -> {
                        val rest = trimmed.drop(7).trim()
                        val name = rest.takeWhile { it.isLetterOrDigit() || it == '_' }
                        if (name.isEmpty()) throw VError(i + 1, 1, "`define needs a macro name")
                        val body = rest.drop(name.length).substringBefore("//").trim()
                        if (body.contains("(")&& rest.drop(name.length).startsWith("(")) throw VError(i + 1, 1, "macros with arguments are not supported in this version")
                        macros[name] = body
                        out.append('\n'); continue
                    }
                    "include", "ifdef", "ifndef", "else", "elsif", "endif", "undef", "pragma" ->
                        throw VError(i + 1, raw.indexOf('`') + 1, "`$word is not supported in this version of LearnMate", "Put everything in the project's files and remove the directive.")
                }
            }
            var line = raw
            if (line.contains('`')) {
                val sb = StringBuilder()
                var k = 0
                while (k < line.length) {
                    val c = line[k]
                    if (c == '`') {
                        val name = line.substring(k + 1).takeWhile { it.isLetterOrDigit() || it == '_' }
                        val body = macros[name] ?: throw VError(i + 1, k + 1, "macro `$name is not defined", "Define it first with `define $name value")
                        sb.append(body); k += 1 + name.length
                    } else { sb.append(c); k++ }
                }
                line = sb.toString()
            }
            out.append(line)
            if (i < lines.size - 1) out.append('\n')
        }
        return out.toString()
    }
}

object VLexer {
    private val syms3 = listOf("<<<", ">>>", "===", "!==")
    private val syms2 = listOf("<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "~&", "~|", "~^", "^~", "+:", "-:", "**")

    fun lex(text: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0; var line = 1; var col = 1
        fun adv(n: Int = 1) { repeat(n) { if (text[i] == '\n') { line++; col = 1 } else col++; i++ } }
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == ' ' || c == '\t' || c == '\r') { adv(); continue }
            if (c == '/' && i + 1 < text.length && text[i + 1] == '/') { while (i < text.length && text[i] != '\n') adv(); continue }
            if (c == '/' && i + 1 < text.length && text[i + 1] == '*') {
                val sl = line; val sc = col
                adv(2)
                while (i < text.length && !(text[i] == '*' && i + 1 < text.length && text[i + 1] == '/')) adv()
                if (i >= text.length) throw VError(sl, sc, "unterminated /* comment", "Close it with */")
                adv(2); continue
            }
            val sl = line; val sc = col
            if (c.isLetter() || c == '_') {
                val st = i
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) adv()
                out.add(Tok(T.Id, text.substring(st, i), sl, sc)); continue
            }
            if (c == '$') {
                val st = i; adv()
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) adv()
                out.add(Tok(T.Sys, text.substring(st, i), sl, sc)); continue
            }
            if (c == '"') {
                adv()
                val sb = StringBuilder()
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\n') throw VError(sl, sc, "unterminated string")
                    if (text[i] == '\\' && i + 1 < text.length) {
                        adv()
                        sb.append(when (text[i]) { 'n' -> '\n'; 't' -> '\t'; '\\' -> '\\'; '"' -> '"'; '%' -> '%'; else -> text[i] })
                        adv()
                    } else { sb.append(text[i]); adv() }
                }
                if (i >= text.length) throw VError(sl, sc, "unterminated string", "Close the string with a double quote")
                adv()
                out.add(Tok(T.Str, sb.toString(), sl, sc)); continue
            }
            if (c.isDigit() || (c == '\'' && i + 1 < text.length && "bBoOdDhHsS".indexOf(text[i + 1]) >= 0)) {
                val st = i
                while (i < text.length && (text[i].isDigit() || text[i] == '_')) adv()
                var sizeStr = text.substring(st, i).replace("_", "")
                if (i < text.length && text[i] == '\'') {
                    adv()
                    if (i < text.length && (text[i] == 's' || text[i] == 'S')) throw VError(sl, sc, "signed literals are not supported in this version", "Use unsigned values.")
                    if (i >= text.length || "bBoOdDhH".indexOf(text[i]) < 0) throw VError(sl, sc, "bad number: missing base after '")
                    val base = text[i].lowercaseChar(); adv()
                    val ds = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '?')) adv()
                    val digits = text.substring(ds, i).replace("_", "").lowercase()
                    if (digits.isEmpty()) throw VError(sl, sc, "bad number: no digits after base")
                    val size = if (sizeStr.isEmpty()) 32 else (sizeStr.toIntOrNull() ?: throw VError(sl, sc, "bad number size"))
                    if (size < 1 || size > 64) throw VError(sl, sc, "number width $size is outside 1..64 bits")
                    out.add(buildNum(base, digits, size, sizeStr.isNotEmpty(), sl, sc)); continue
                }
                val value = sizeStr.toULongOrNull() ?: throw VError(sl, sc, "number too large (limit is 64 bits)")
                out.add(Tok(T.Num, sizeStr, sl, sc, V.of(if (value > 0xFFFFFFFFUL) 64 else 32, value.toLong()), false)); continue
            }
            val s3 = if (i + 3 <= text.length) text.substring(i, i + 3) else ""
            val s2 = if (i + 2 <= text.length) text.substring(i, i + 2) else ""
            when {
                s3 in syms3 -> { adv(3); out.add(Tok(T.Sym, s3, sl, sc)) }
                s2 in syms2 -> { adv(2); out.add(Tok(T.Sym, s2, sl, sc)) }
                "()[]{};,:.#@=<>+-*/%&|^~!?".indexOf(c) >= 0 -> { adv(); out.add(Tok(T.Sym, c.toString(), sl, sc)) }
                else -> throw VError(sl, sc, "unexpected character '$c'")
            }
        }
        out.add(Tok(T.Eof, "", line, col))
        return out
    }

    private fun buildNum(base: Char, digits: String, size: Int, sized: Boolean, line: Int, col: Int): Tok {
        var v = 0L; var x = 0L; var z = 0L
        if (base == 'd') {
            if (digits == "x" || digits == "z" || digits == "?") {
                return Tok(T.Num, digits, line, col, V.allX(size), sized, if (digits == "x") 0L else V.mask(size))
            }
            val u = digits.toULongOrNull() ?: throw VError(line, col, "bad decimal number '$digits'")
            return Tok(T.Num, digits, line, col, V.of(size, u.toLong()), sized)
        }
        val bitsPer = when (base) { 'b' -> 1; 'o' -> 3; else -> 4 }
        var pos = 0
        for (ch in digits.reversed()) {
            val m = (1L shl bitsPer) - 1
            val sh = pos
            when {
                ch == 'x' || ch == 'z' || ch == '?' -> {
                    if (sh < 64) { x = x or (m shl sh); if (ch != 'x') z = z or (m shl sh) }
                }
                else -> {
                    val d = Character.digit(ch, 1 shl bitsPer)
                    if (d < 0) throw VError(line, col, "digit '$ch' is not valid in base-${1 shl bitsPer} number")
                    if (sh < 64) v = v or (d.toLong() shl sh)
                }
            }
            pos += bitsPer
        }
        val top = digits.first()
        if ((top == 'x' || top == 'z' || top == '?') && pos < size) {
            val ext = V.mask(size) and V.mask(pos).inv()
            x = x or ext; if (top != 'x') z = z or ext
        }
        return Tok(T.Num, digits, line, col, V.mk(size, v, x), sized, z and V.mask(size))
    }
}
