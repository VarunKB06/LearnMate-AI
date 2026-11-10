package com.varunkb.learnmateai.hdl

private val UNSUPPORTED = setOf(
    "function", "task", "generate", "genvar", "defparam", "specify", "fork", "join", "force", "release",
    "wait", "disable", "real", "time", "signed", "inout", "interface", "class", "package", "typedef", "struct", "enum",
    "assert", "program", "always_ff_", "automatic", "tri", "wand", "wor", "supply0", "supply1", "event", "deassign", "casez_"
)
private val GATES = setOf("and", "or", "nand", "nor", "xor", "xnor", "not", "buf")

class VParser(private val file: String, private val toks: List<Tok>) {
    private var p = 0
    private val cur get() = toks[p]
    private fun peek(n: Int = 1) = toks[minOf(p + n, toks.size - 1)]

    private fun err(t: Tok, msg: String, hint: String? = null): Nothing = throw VError(t.line, t.col, msg, hint)
    private fun isSym(s: String) = cur.t == T.Sym && cur.s == s
    private fun isKw(s: String) = cur.t == T.Id && cur.s == s
    private fun accept(s: String): Boolean { if (isSym(s)) { p++; return true }; return false }
    private fun acceptKw(s: String): Boolean { if (isKw(s)) { p++; return true }; return false }

    private fun expect(s: String, hint: String? = null): Tok {
        if (!isSym(s)) {
            val prev = toks[maxOf(p - 1, 0)]
            val h = hint ?: if (s == ";") "A statement or declaration probably ends just before ${cur} and is missing a semicolon." else null
            // Missing ';' is reported at the end of the previous token, which is where the user must fix it.
            if (s == ";" && p > 0) throw VError(prev.line, prev.col + prev.s.length.coerceAtLeast(1), "expected ';' but found $cur", h)
            err(cur, "expected '$s' but found $cur", h)
        }
        return toks[p++]
    }

    private fun expectKw(s: String): Tok { if (!isKw(s)) err(cur, "expected '$s' but found $cur"); return toks[p++] }

    private fun ident(what: String = "a name"): Tok {
        if (cur.t != T.Id) err(cur, "expected $what but found $cur")
        if (cur.s in KEYWORDS) err(cur, "'${cur.s}' is a reserved word and cannot be used as $what")
        return toks[p++]
    }

    private fun unsupported(t: Tok): Nothing =
        err(t, "'${t.s}' is not supported in this version of LearnMate's simulator", "Rewrite the design using the supported subset (see HDL Studio help), e.g. always blocks and assign statements.")

    // ---------- modules ----------
    fun parseAll(): List<ModuleDef> {
        val mods = ArrayList<ModuleDef>()
        while (cur.t != T.Eof) {
            if (!isKw("module")) {
                if (cur.t == T.Id && cur.s in UNSUPPORTED) unsupported(cur)
                err(cur, "expected 'module' but found $cur", "Every design starts with: module name (ports);")
            }
            mods.add(parseModule())
        }
        return mods
    }

    private fun parseModule(): ModuleDef {
        val kw = expectKw("module")
        val name = ident("a module name")
        val params = ArrayList<ParamDecl>()
        if (isSym("#")) {
            p++; expect("(")
            while (true) {
                acceptKw("parameter")
                if (isSym("[")) parseRange()
                val n = ident("a parameter name"); expect("=")
                params.add(ParamDecl(n.s, parseExpr(), false, n.line, n.col))
                if (!accept(",")) break
            }
            expect(")")
        }
        val ports = ArrayList<PortDecl>()
        if (accept("(")) {
            if (!isSym(")")) {
                val ansi = isKw("input") || isKw("output") || isKw("inout")
                if (ansi) {
                    var dir: String? = null; var isReg = false; var isLogic = false; var range: Range? = null
                    while (true) {
                        if (isKw("input") || isKw("output") || isKw("inout")) {
                            if (isKw("inout")) unsupported(cur)
                            dir = cur.s; p++; isReg = false; isLogic = false; range = null
                            if (isKw("wire")) p++
                            else if (isKw("reg") || isKw("logic")) { isReg = true; isLogic = cur.s == "logic"; p++ }
                            if (isKw("signed")) unsupported(cur)
                            if (isSym("[")) range = parseRange()
                        }
                        val n = ident("a port name")
                        ports.add(PortDecl(n.s, dir, isReg, range, n.line, n.col).also { it.logic = isLogic })
                        if (!accept(",")) break
                    }
                } else {
                    while (true) {
                        val n = ident("a port name")
                        ports.add(PortDecl(n.s, null, false, null, n.line, n.col))
                        if (!accept(",")) break
                    }
                }
            }
            expect(")")
        }
        expect(";")
        val items = ArrayList<Item>()
        while (!isKw("endmodule")) {
            if (cur.t == T.Eof) err(cur, "missing 'endmodule' for module '${name.s}'", "Close the module with endmodule")
            parseItem(items, ports)
        }
        p++
        return ModuleDef(name.s, file, kw.line, params, ports, items)
    }

    private fun parseRange(): Range {
        expect("[")
        val a = parseExpr(); expect(":"); val b = parseExpr(); expect("]")
        return Range(a, b)
    }

    private fun <T : Item> at(t: Tok, item: T): T { item.line = t.line; item.col = t.col; return item }

    private fun parseItem(items: MutableList<Item>, ports: MutableList<PortDecl>) {
        val t = cur
        if (t.t != T.Id) err(t, "unexpected $t in module body")
        when (t.s) {
            "parameter", "localparam" -> {
                p++
                if (isSym("[")) parseRange()
                while (true) {
                    val n = ident("a parameter name"); expect("=")
                    items.add(at(t, ParamItem(ParamDecl(n.s, parseExpr(), t.s == "localparam", n.line, n.col))))
                    if (!accept(",")) break
                }
                expect(";")
            }
            "input", "output" -> {
                p++
                var isReg = false
                var isLogic = false
                if (isKw("wire")) p++ else if (isKw("reg") || isKw("logic")) { isReg = true; isLogic = cur.s == "logic"; p++ }
                if (isKw("signed")) unsupported(cur)
                val range = if (isSym("[")) parseRange() else null
                while (true) {
                    val n = ident("a port name")
                    val port = ports.firstOrNull { it.name == n.s }
                        ?: err(n, "'${n.s}' is declared as ${t.s} but is not in the module's port list", "Add '${n.s}' to the port list in the module header.")
                    port.dir = t.s; port.isReg = isReg; port.logic = isLogic; port.range = range
                    if (!accept(",")) break
                }
                expect(";")
            }
            "wire", "reg", "logic", "integer" -> {
                p++
                if (isKw("signed")) unsupported(cur)
                val range = if (t.s != "integer" && isSym("[")) parseRange() else null
                while (true) {
                    val n = ident("a signal name")
                    val dims = if (isSym("[")) parseRange() else null
                    items.add(at(n, DeclItem(NetDecl(n.s, t.s, range, dims, n.line, n.col))))
                    if (accept("=")) {
                        if (t.s == "reg" || t.s == "integer") err(cur, "initial values in declarations are only supported for wires", "Set the value in an initial block instead.")
                        val rhs = parseExpr()
                        val lhs = Id(n.s).also { it.line = n.line; it.col = n.col }
                        items.add(at(n, ContAssignItem(lhs, rhs)))
                    }
                    if (!accept(",")) break
                }
                expect(";")
            }
            "assign" -> {
                p++
                if (isSym("#")) err(cur, "delays on 'assign' are not supported in this version")
                while (true) {
                    val lhs = parseLvalue()
                    expect("=")
                    items.add(at(t, ContAssignItem(lhs, parseExpr())))
                    if (!accept(",")) break
                }
                expect(";")
            }
            "initial" -> { p++; items.add(at(t, ProcItem("initial", parseStmt()))) }
            "always", "always_comb", "always_ff", "always_latch" -> { p++; items.add(at(t, ProcItem(t.s, parseStmt()))) }
            "endmodule" -> Unit
            else -> {
                if (t.s in GATES) { parseGate(items); return }
                if (t.s in UNSUPPORTED) unsupported(t)
                if (t.s in KEYWORDS) err(t, "unexpected keyword '${t.s}' in module body")
                // module instantiation: type [#(...)] name (...)
                parseInstance(items)
            }
        }
    }

    private fun parseGate(items: MutableList<Item>) {
        val t = toks[p++]
        if (isSym("#")) err(cur, "gate delays are not supported in this version")
        while (true) {
            if (cur.t == T.Id) p++ // optional instance name
            expect("(")
            val args = ArrayList<Expr>()
            while (true) { args.add(parseExpr()); if (!accept(",")) break }
            expect(")")
            if (args.size < 2) err(t, "gate '${t.s}' needs an output and at least one input")
            val out = args[0]
            val ins = args.drop(1)
            if (out !is Id && out !is Index && out !is Part) err(t, "gate output must be a signal name")
            fun chain(op: String): Expr = ins.reduce { a, b -> Binary(op, a, b).also { it.line = t.line; it.col = t.col } }
            val rhs: Expr = when (t.s) {
                "and" -> chain("&"); "or" -> chain("|"); "xor" -> chain("^")
                "nand" -> Unary("~", chain("&")); "nor" -> Unary("~", chain("|")); "xnor" -> Unary("~", chain("^"))
                "not" -> { if (ins.size != 1) err(t, "'not' gate takes exactly one input in this version"); Unary("~", ins[0]) }
                else -> { if (ins.size != 1) err(t, "'buf' gate takes exactly one input in this version"); ins[0] }
            }
            rhs.line = t.line; rhs.col = t.col
            items.add(at(t, ContAssignItem(out, rhs)))
            if (!accept(",")) break
        }
        expect(";")
    }

    private fun parseInstance(items: MutableList<Item>) {
        val mt = ident("a module name")
        val params = ArrayList<Conn>()
        if (accept("#")) {
            expect("(")
            if (!isSym(")")) {
                while (true) {
                    if (accept(".")) { val n = ident(); expect("("); val e = parseExpr(); expect(")"); params.add(Conn(n.s, e)) }
                    else params.add(Conn(null, parseExpr()))
                    if (!accept(",")) break
                }
            }
            expect(")")
        }
        while (true) {
            val nameTok = if (cur.t == T.Id && peek().t == T.Sym && peek().s == "(") ident("an instance name")
            else err(cur, "expected an instance name after module type '${mt.s}' but found $cur",
                if (cur.t == T.Sym) "If '${mt.s}' is meant to be a statement, it must be inside an initial/always block." else null)
            expect("(")
            val conns = ArrayList<Conn>()
            if (!isSym(")")) {
                while (true) {
                    if (accept(".")) {
                        val n = ident("a port name"); expect("(")
                        val e = if (isSym(")")) null else parseExpr()
                        expect(")"); conns.add(Conn(n.s, e))
                    } else conns.add(Conn(null, parseExpr()))
                    if (!accept(",")) break
                }
            }
            expect(")")
            items.add(at(mt, InstItem(mt.s, nameTok.s, params, conns)))
            if (!accept(",")) break
        }
        expect(";")
    }

    // ---------- statements ----------
    private fun <T : Stmt> at(t: Tok, s: T): T { s.line = t.line; s.col = t.col; return s }

    fun parseStmt(): Stmt {
        val t = cur
        if (accept(";")) return Empty
        if (t.t == T.Sym) {
            when (t.s) {
                "#" -> { p++; val d = parseDelayExpr(); val body = if (isSym(";")) { p++; null } else parseStmt(); return at(t, Delay(d, body)) }
                "@" -> {
                    p++
                    var star = false
                    val items = ArrayList<EvItem>()
                    if (accept("*")) star = true
                    else if (accept("(")) {
                        if (accept("*")) { star = true; expect(")") }
                        else {
                            while (true) {
                                var edge = 0
                                if (acceptKw("posedge")) edge = 1 else if (acceptKw("negedge")) edge = 2
                                val n = ident("a signal name")
                                if (isSym("[")) err(cur, "events on bit/part selects are not supported", "Use the whole signal in the event list.")
                                items.add(EvItem(n.s, edge, n.line, n.col))
                                if (acceptKw("or") || accept(",")) continue
                                break
                            }
                            expect(")")
                        }
                    } else {
                        val n = ident("a signal name"); items.add(EvItem(n.s, 0, n.line, n.col))
                    }
                    val body = if (isSym(";")) { p++; null } else parseStmt()
                    return at(t, EventCtl(items, star, body))
                }
                "{" -> return parseAssignStmt()
            }
            err(t, "unexpected $t at the start of a statement")
        }
        if (t.t == T.Sys) return parseSysCall()
        if (t.t != T.Id) err(t, "unexpected $t at the start of a statement")
        when (t.s) {
            "begin" -> {
                p++
                if (accept(":")) ident("a block name")
                val list = ArrayList<Stmt>()
                while (!isKw("end")) {
                    if (cur.t == T.Eof) err(t, "'begin' has no matching 'end'", "Add 'end' after the last statement of the block.")
                    list.add(parseStmt())
                }
                p++
                return at(t, Block(list))
            }
            "if" -> {
                p++; expect("("); val c = parseExpr(); expect(")")
                val th = parseStmt()
                val el = if (acceptKw("else")) parseStmt() else null
                return at(t, If(c, th, el))
            }
            "case", "casez", "casex" -> {
                p++; expect("("); val sel = parseExpr(); expect(")")
                val items = ArrayList<CaseItem>()
                while (!isKw("endcase")) {
                    if (cur.t == T.Eof) err(t, "'${t.s}' has no matching 'endcase'")
                    if (acceptKw("default")) { accept(":"); items.add(CaseItem(emptyList(), parseStmt())) }
                    else {
                        val labels = ArrayList<Expr>()
                        while (true) { labels.add(parseExpr()); if (!accept(",")) break }
                        expect(":")
                        items.add(CaseItem(labels, parseStmt()))
                    }
                }
                p++
                return at(t, Case(t.s, sel, items))
            }
            "for" -> {
                p++; expect("(")
                val init = parseSimpleAssign(); expect(";")
                val c = parseExpr(); expect(";")
                val step = parseSimpleAssign(); expect(")")
                return at(t, For(init, c, step, parseStmt()))
            }
            "while" -> { p++; expect("("); val c = parseExpr(); expect(")"); return at(t, While(c, parseStmt())) }
            "repeat" -> { p++; expect("("); val c = parseExpr(); expect(")"); return at(t, Repeat(c, parseStmt())) }
            "forever" -> { p++; return at(t, Forever(parseStmt())) }
            "end", "endcase", "endmodule" -> err(t, "unexpected '${t.s}'", "There is an extra '${t.s}' or a missing 'begin'/'case' above.")
            "else" -> err(t, "'else' without a matching 'if'", "Check the semicolons and begin/end around the previous if.")
        }
        if (t.s in UNSUPPORTED) unsupported(t)
        if (t.s in KEYWORDS) err(t, "unexpected keyword '${t.s}'")
        return parseAssignStmt()
    }

    private fun parseDelayExpr(): Expr {
        if (accept("(")) { val e = parseExpr(); expect(")"); return e }
        val t = cur
        if (t.t == T.Num) { p++; return mkNum(t) }
        if (t.t == T.Id) { p++; return Id(t.s).also { it.line = t.line; it.col = t.col } }
        err(t, "expected a delay value after '#' but found $t")
    }

    private fun parseSimpleAssign(): Stmt {
        val t = cur
        val lhs = parseLvalue()
        expect("=")
        return at(t, Assign(lhs, parseExpr(), true, null))
    }

    private fun parseAssignStmt(): Stmt {
        val t = cur
        val lhs = parseLvalue()
        val blocking = when {
            accept("=") -> true
            accept("<=") -> false
            else -> err(cur, "expected '=' or '<=' after '${describe(lhs)}' but found $cur",
                if (lhs is Id && isSym("(")) "'${lhs.name}(...)' looks like a call; tasks and functions are not supported." else null)
        }
        var delay: Expr? = null
        if (accept("#")) delay = parseDelayExpr()
        val rhs = parseExpr()
        expect(";")
        return at(t, Assign(lhs, rhs, blocking, delay))
    }

    private fun describe(e: Expr) = when (e) { is Id -> e.name; is Index -> e.name + "[..]"; is Part -> e.name + "[..:..]"; else -> "target" }

    private fun parseSysCall(): Stmt {
        val t = toks[p++]
        val args = ArrayList<Expr>()
        if (accept("(")) {
            if (!isSym(")")) while (true) { args.add(parseExpr()); if (!accept(",")) break }
            expect(")")
        }
        expect(";")
        return at(t, SysCall(t.s, args))
    }

    private fun parseLvalue(): Expr {
        val t = cur
        if (accept("{")) {
            val parts = ArrayList<Expr>()
            while (true) { parts.add(parseLvalue()); if (!accept(",")) break }
            expect("}")
            return Concat(parts).also { it.line = t.line; it.col = t.col }
        }
        if (t.t != T.Id || t.s in KEYWORDS) err(t, "expected a signal name but found $t")
        p++
        return selects(t)
    }

    private fun selects(t: Tok): Expr {
        if (isSym("[")) {
            p++
            val a = parseExpr()
            val e: Expr = when {
                accept(":") -> { val b = parseExpr(); expect("]"); Part(t.s, a, b) }
                isSym("+:") || isSym("-:") -> { val plus = cur.s == "+:"; p++; val w = parseExpr(); expect("]"); IPart(t.s, a, w, plus) }
                else -> { expect("]"); Index(t.s, a) }
            }
            if (isSym("[")) err(cur, "chained selects like a[i][j] are not supported", "Copy the word to a temporary signal first, or use a part-select.")
            e.line = t.line; e.col = t.col
            return e
        }
        return Id(t.s).also { it.line = t.line; it.col = t.col }
    }

    // ---------- expressions ----------
    private fun mkNum(t: Tok) = Num(t.num!!, t.sized, t.zmask).also { it.line = t.line; it.col = t.col }

    fun parseExpr(): Expr = parseTernary()

    private fun parseTernary(): Expr {
        val c = parseBin(0)
        if (isSym("?")) {
            val t = toks[p++]
            val a = parseTernary(); expect(":"); val b = parseTernary()
            return Tern(c, a, b).also { it.line = t.line; it.col = t.col }
        }
        return c
    }

    private val levels = listOf(
        listOf("||"), listOf("&&"), listOf("|"), listOf("^", "~^", "^~"), listOf("&"),
        listOf("==", "!=", "===", "!=="), listOf("<", "<=", ">", ">="), listOf("<<", ">>", "<<<", ">>>"),
        listOf("+", "-"), listOf("*", "/", "%")
    )

    private fun parseBin(level: Int): Expr {
        if (level >= levels.size) return parseUnary()
        var l = parseBin(level + 1)
        while (cur.t == T.Sym && cur.s in levels[level]) {
            val t = toks[p++]
            val r = parseBin(level + 1)
            val op = when (t.s) { "<<<" -> "<<"; ">>>" -> ">>"; "^~" -> "~^"; else -> t.s }
            l = Binary(op, l, r).also { it.line = t.line; it.col = t.col }
        }
        return l
    }

    private fun parseUnary(): Expr {
        val t = cur
        if (t.t == T.Sym && t.s in listOf("+", "-", "~", "!", "&", "|", "^", "~&", "~|", "~^", "^~")) {
            p++
            val e = parseUnary()
            val op = if (t.s == "^~") "~^" else t.s
            return Unary(op, e).also { it.line = t.line; it.col = t.col }
        }
        if (t.t == T.Sym && t.s == "**") err(t, "the power operator ** is not supported in this version")
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val t = cur
        when (t.t) {
            T.Num -> { p++; return mkNum(t) }
            T.Str -> { p++; return Str(t.s).also { it.line = t.line; it.col = t.col } }
            T.Sys -> {
                p++
                val args = ArrayList<Expr>()
                if (accept("(")) { if (!isSym(")")) while (true) { args.add(parseExpr()); if (!accept(",")) break }; expect(")") }
                if (t.s !in setOf("\$time", "\$random", "\$clog2", "\$stime")) err(t, "system function ${t.s} is not supported in this version")
                return SysFn(t.s, args).also { it.line = t.line; it.col = t.col }
            }
            T.Id -> {
                if (t.s in KEYWORDS) err(t, "unexpected keyword '${t.s}' in an expression")
                p++
                if (isSym("(")) err(t, "'${t.s}(...)' looks like a function call; functions are not supported in this version")
                return selects(t)
            }
            T.Sym -> {
                if (t.s == "(") { p++; val e = parseExpr(); expect(")"); return e }
                if (t.s == "{") {
                    p++
                    val first = parseExpr()
                    if (isSym("{")) {
                        p++
                        val parts = ArrayList<Expr>()
                        while (true) { parts.add(parseExpr()); if (!accept(",")) break }
                        expect("}"); expect("}")
                        return Repl(first, parts).also { it.line = t.line; it.col = t.col }
                    }
                    val parts = arrayListOf(first)
                    while (accept(",")) parts.add(parseExpr())
                    expect("}")
                    return Concat(parts).also { it.line = t.line; it.col = t.col }
                }
                err(t, "unexpected $t in expression", "An operand (signal, number or parenthesised expression) is missing here.")
            }
            T.Eof -> err(t, "unexpected end of file inside an expression")
        }
    }

    companion object {
        val KEYWORDS = setOf(
            "module", "endmodule", "input", "output", "inout", "wire", "reg", "logic", "integer", "parameter", "localparam",
            "assign", "initial", "always", "always_comb", "always_ff", "always_latch", "begin", "end", "if", "else",
            "case", "casez", "casex", "endcase", "default", "for", "while", "repeat", "forever", "posedge", "negedge", "or"
        ) + UNSUPPORTED
    }
}
