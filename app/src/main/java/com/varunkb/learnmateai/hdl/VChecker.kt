package com.varunkb.learnmateai.hdl

/** Static checks on parsed modules: undeclared names, wrong driver kinds, bad instances. */
internal object VChecker {
    val SYS_TASKS = setOf("\$display", "\$write", "\$monitor", "\$strobe", "\$finish", "\$stop", "\$dumpfile", "\$dumpvars", "\$displayb", "\$displayh")

    private class Sym(val kind: String, val isMem: Boolean = false) // kind: wire reg logic input param

    fun check(mods: List<ModuleDef>, diags: MutableList<Diagnostic>) {
        val byName = HashMap<String, ModuleDef>()
        for (m in mods) {
            if (byName.containsKey(m.name)) diags.add(d(m, m.line, 1, "module '${m.name}' is defined more than once"))
            byName[m.name] = m
        }
        for (m in mods) ModuleCheck(m, byName, diags).run()
    }

    private fun d(m: ModuleDef, line: Int, col: Int, msg: String, hint: String? = null, sev: Severity = Severity.Error) =
        Diagnostic(sev, m.file, line, col, msg, hint)

    private class ModuleCheck(val m: ModuleDef, val mods: Map<String, ModuleDef>, val diags: MutableList<Diagnostic>) {
        val syms = HashMap<String, Sym>()
        var clocked = false

        fun err(line: Int, col: Int, msg: String, hint: String? = null) { diags.add(d(m, line, col, msg, hint)) }
        fun warn(line: Int, col: Int, msg: String, hint: String? = null) { diags.add(d(m, line, col, msg, hint, Severity.Warning)) }

        fun run() {
            for (p in m.params) syms[p.name] = Sym("param")
            for (p in m.ports) {
                if (p.dir == null) { err(p.line, p.col, "port '${p.name}' has no direction", "Declare it with input or output."); continue }
                syms[p.name] = Sym(if (p.dir == "input") "input" else if (p.logic) "logic" else if (p.isReg) "reg" else "wire")
            }
            // First pass: declarations, so use-before-declare of reg/wire in procedural code still resolves.
            for (it in m.items) {
                when (it) {
                    is ParamItem -> syms[it.p.name] = Sym("param")
                    is DeclItem -> {
                        val dd = it.d
                        val kind = when (dd.kind) { "integer" -> "reg"; else -> dd.kind }
                        val existing = syms[dd.name]
                        val isPort = m.ports.any { p -> p.name == dd.name }
                        if (existing != null && isPort) {
                            syms[dd.name] = Sym(if (existing.kind == "input") "input" else if (kind == "reg" || kind == "logic") kind else existing.kind, dd.dims != null)
                        } else if (existing != null) {
                            err(dd.line, dd.col, "'${dd.name}' is declared twice in module '${m.name}'")
                        } else syms[dd.name] = Sym(kind, dd.dims != null)
                    }
                    else -> Unit
                }
            }
            for (it in m.items) {
                when (it) {
                    is ContAssignItem -> {
                        checkLv(it.lhs, "assign")
                        checkExpr(it.rhs)
                    }
                    is ProcItem -> {
                        val top = it.body
                        clocked = top is EventCtl && !top.star && top.items.any { e -> e.edge != 0 }
                        stmt(it.body, it.kind == "initial")
                    }
                    is InstItem -> inst(it)
                    else -> Unit
                }
            }
            for (p in m.params) checkExpr(p.value)
        }

        fun inst(i: InstItem) {
            val target = mods[i.module]
            if (target == null) {
                err(i.line, i.col, "module '${i.module}' is not defined", "Check the spelling or add the file that defines it to this project.")
                i.conns.forEach { c -> c.expr?.let { checkExpr(it) } }
                return
            }
            if (target.name == m.name) err(i.line, i.col, "module '${m.name}' cannot instantiate itself")
            val named = i.conns.any { it.port != null }
            if (named && i.conns.any { it.port == null }) err(i.line, i.col, "instance '${i.name}' mixes named and positional connections")
            if (named) {
                for (c in i.conns) {
                    if (target.ports.none { p -> p.name == c.port }) err(i.line, i.col, "module '${target.name}' has no port named '${c.port}'",
                        "Its ports are: " + target.ports.joinToString(", ") { it.name })
                }
            } else if (i.conns.size > target.ports.size) err(i.line, i.col, "instance '${i.name}' has ${i.conns.size} connections but module '${target.name}' has ${target.ports.size} ports")
            val targetParams = target.params.map { it.name } + target.items.filterIsInstance<ParamItem>().filter { !it.p.local }.map { it.p.name }
            for (c in i.params) {
                if (c.port != null && c.port !in targetParams) err(i.line, i.col, "module '${target.name}' has no parameter named '${c.port}'")
                c.expr?.let { checkExpr(it) }
            }
            for (c in i.conns) c.expr?.let { checkExpr(it) }
        }

        fun stmt(s: Stmt, initial: Boolean) {
            when (s) {
                is Block -> s.stmts.forEach { stmt(it, initial) }
                is Assign -> {
                    checkLv(s.lhs, "proc")
                    checkExpr(s.rhs); s.delay?.let { checkExpr(it) }
                    if (!initial) {
                        if (clocked && s.blocking) warn(s.line, s.col, "blocking assignment (=) in a clocked always block", "Use non-blocking (<=) for sequential logic so all flip-flops update together.")
                        if (!clocked && !s.blocking) warn(s.line, s.col, "non-blocking assignment (<=) in a combinational always block", "Use blocking (=) for combinational logic.")
                    }
                }
                is If -> { checkExpr(s.c); stmt(s.t, initial); s.e?.let { stmt(it, initial) } }
                is Case -> {
                    checkExpr(s.sel)
                    s.items.forEach { it.labels.forEach { l -> checkExpr(l) }; stmt(it.body, initial) }
                    if (!initial && !clocked && s.items.none { it.labels.isEmpty() })
                        warn(s.line, s.col, "case statement has no default", "In combinational logic a missing default can create an unwanted latch.")
                }
                is For -> { stmt(s.init, true); checkExpr(s.cond); stmt(s.step, true); stmt(s.body, initial) }
                is While -> { checkExpr(s.cond); stmt(s.body, initial) }
                is Repeat -> { checkExpr(s.n); stmt(s.body, initial) }
                is Forever -> stmt(s.body, initial)
                is Delay -> { checkExpr(s.t); s.body?.let { stmt(it, initial) } }
                is EventCtl -> {
                    for (e in s.items) if (!syms.containsKey(e.name)) undeclared(e.name, e.line, e.col)
                    s.body?.let { stmt(it, initial) }
                }
                is SysCall -> {
                    if (s.name !in SYS_TASKS) err(s.line, s.col, "system task ${s.name} is not supported in this version", "Supported: " + SYS_TASKS.sorted().joinToString(" "))
                    s.args.forEach { checkExpr(it) }
                }
                Empty -> Unit
            }
        }

        fun undeclared(name: String, line: Int, col: Int) =
            err(line, col, "'$name' is not declared in module '${m.name}'", "Check the spelling, or declare it with wire/reg/input/output.")

        fun root(e: Expr): String? = when (e) { is Id -> e.name; is Index -> e.name; is Part -> e.name; is IPart -> e.name; else -> null }

        fun checkLv(e: Expr, mode: String) {
            if (e is Concat) { e.parts.forEach { checkLv(it, mode) }; return }
            val name = root(e) ?: run { err(e.line, e.col, "invalid assignment target"); return }
            val sym = syms[name]
            if (sym == null) { undeclared(name, e.line, e.col); checkExpr(e); return }
            if (e is Id && sym.isMem) err(e.line, e.col, "memory '$name' needs an index when assigned", "Write $name[address] = value;")
            when {
                sym.kind == "input" -> err(e.line, e.col, "cannot assign to input port '$name'", "Inputs are driven from outside the module.")
                sym.kind == "param" -> err(e.line, e.col, "cannot assign to parameter '$name'")
                mode == "assign" && sym.kind == "reg" -> err(e.line, e.col, "'$name' is a reg and cannot be driven by 'assign'", "Declare '$name' as wire, or assign it inside an always block.")
                mode == "proc" && sym.kind == "wire" -> err(e.line, e.col, "'$name' is a wire and cannot be assigned inside an always/initial block", "Declare '$name' as reg (or 'output reg'), or use an 'assign' statement.")
            }
            checkExpr(e, lvalue = true)
        }

        fun checkExpr(e: Expr, lvalue: Boolean = false) {
            when (e) {
                is Num, is Str -> Unit
                is Id -> {
                    val s = syms[e.name]
                    if (s == null) undeclared(e.name, e.line, e.col)
                    else if (s.isMem && !lvalue) err(e.line, e.col, "memory '${e.name}' needs an index", "Write ${e.name}[address].")
                }
                is Index -> { if (!syms.containsKey(e.name)) undeclared(e.name, e.line, e.col); checkExpr(e.idx) }
                is Part -> { if (!syms.containsKey(e.name)) undeclared(e.name, e.line, e.col); checkExpr(e.msb); checkExpr(e.lsb) }
                is IPart -> { if (!syms.containsKey(e.name)) undeclared(e.name, e.line, e.col); checkExpr(e.base); checkExpr(e.width) }
                is Unary -> checkExpr(e.e)
                is Binary -> { checkExpr(e.l); checkExpr(e.r) }
                is Tern -> { checkExpr(e.c); checkExpr(e.a); checkExpr(e.b) }
                is Concat -> e.parts.forEach { checkExpr(it) }
                is Repl -> { checkExpr(e.n); e.parts.forEach { checkExpr(it) } }
                is SysFn -> e.args.forEach { checkExpr(it) }
            }
        }
    }
}
