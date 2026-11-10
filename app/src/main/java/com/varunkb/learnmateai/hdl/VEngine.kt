package com.varunkb.learnmateai.hdl

import java.util.TreeMap

internal class RtError(message: String, val line: Int = 0, val col: Int = 0) : RuntimeException(message, null, false, false)
internal class StopSim(val reason: String) : RuntimeException(reason, null, false, false)

internal class Net(val name: String, val width: Int, val lo: Int, val depth: Int = 0, val isMem: Boolean = false, val signed: Boolean = false) {
    var cur: V = V.allX(width)
    val mem: Array<V>? = if (isMem) Array(depth) { V.allX(width) } else null
    val deps = ArrayList<ContAssign>()
    val waiters = ArrayList<Waiter>()
    val wave: ArrayList<WaveChange>? = if (isMem) null else arrayListOf(WaveChange(0, cur.bits()))
}

internal class Scope(val path: String, val mod: ModuleDef) {
    val params = HashMap<String, V>()
    val nets = HashMap<String, Net>()
}

internal class ContAssign(val lhs: Expr, val lscope: Scope, val rhs: Expr, val rscope: Scope) { var queued = false }

internal sealed class Wait
internal class WDelay(val t: Long) : Wait()
internal class WEvent(val items: List<Pair<Net, Int>>) : Wait()

internal class Proc(val kind: String, val scope: Scope, val body: Stmt) {
    lateinit var gen: Iterator<Wait>
    var dead = false
}

internal class Waiter(val proc: Proc, val items: List<Pair<Net, Int>>) { var done = false }

internal class Seg(val net: Net?, val lo: Int, val width: Int, val mem: Int)
internal class Task(val proc: Proc?, val ca: ContAssign?)
internal class NbaItem(val segs: List<Seg>, val value: V)
internal class Slot { val active = ArrayDeque<Task>(); val nba = ArrayList<NbaItem>() }
private class Monitor(val args: List<Expr>, val sc: Scope) { var last: List<V>? = null }
private class Strobe(val args: List<Expr>, val sc: Scope)

internal class Engine(
    private val mods: Map<String, ModuleDef>,
    private val limits: SimLimits,
    private val cancelled: () -> Boolean
) {
    var now = 0L
    var steps = 0L
    val output = ArrayList<String>()
    private val partial = StringBuilder()
    private val slots = TreeMap<Long, Slot>()
    private var curSlot = Slot()
    private val allNets = ArrayList<Net>()
    private val scopes = ArrayList<Scope>()
    private val procs = ArrayList<Proc>()
    private val cas = ArrayList<ContAssign>()
    private val start = System.nanoTime()
    private var waveCount = 0
    var waveTruncated = false
    private var monitor: Monitor? = null
    private val strobes = ArrayList<Strobe>()
    private var rnd = 1L

    // ---------------- limits ----------------
    private fun tick() {
        steps++
        if (steps > limits.maxSteps) throw StopSim("step limit")
        if ((steps and 1023L) == 0L) {
            if (cancelled()) throw StopSim("cancelled")
            if ((System.nanoTime() - start) / 1_000_000 > limits.maxWallMillis) throw StopSim("wall-clock limit")
        }
    }

    private fun emit(line: String) {
        if (output.size >= limits.maxOutputLines) throw StopSim("output limit")
        output.add(line)
    }

    // ---------------- elaboration ----------------
    fun tops(): List<ModuleDef> {
        val used = HashSet<String>()
        for (m in mods.values) for (i in m.items) if (i is InstItem) used.add(i.module)
        return mods.values.filter { it.name !in used }
    }

    fun elaborate(tops: List<ModuleDef>) {
        for (t in tops) build(t, t.name, emptyList(), emptyList(), 0, null)
    }

    private fun rangeOf(r: Range?, sc: Scope, what: String, line: Int): Pair<Int, Int> {
        if (r == null) return 1 to 0
        val a = constInt(r.msb, sc); val b = constInt(r.lsb, sc)
        if (a < b) throw RtError("ascending range [$a:$b] for $what is not supported; write [msb:lsb] with msb >= lsb", line)
        if (a - b + 1 > 64) throw RtError("$what is ${a - b + 1} bits wide; the simulator supports at most 64", line)
        return a - b + 1 to b
    }

    private fun addNet(sc: Scope, n: Net) { sc.nets[n.name] = n; allNets.add(n); }

    private fun build(m: ModuleDef, path: String, posOv: List<V?>, namedOv: List<Pair<String, V>>, depth: Int, parent: Scope?): Scope {
        if (depth > 32) throw RtError("module instantiation is nested more than 32 levels deep (recursive instantiation?)", m.line)
        if (scopes.size > 4000) throw RtError("design has more than 4000 module instances; too large for the simulator", m.line)
        val sc = Scope(path, m)
        scopes.add(sc)
        // parameters
        val overridable = m.params.map { it } + m.items.filterIsInstance<ParamItem>().filter { !it.p.local }.map { it.p }
        val allParams = ArrayList<ParamDecl>()
        allParams.addAll(m.params)
        m.items.filterIsInstance<ParamItem>().forEach { allParams.add(it.p) }
        for (p in allParams) {
            val idx = overridable.indexOf(p)
            val named = namedOv.firstOrNull { it.first == p.name }?.second
            val pos = if (idx >= 0) posOv.getOrNull(idx) else null
            sc.params[p.name] = named ?: pos ?: evalSelf(p.value, sc)
        }
        // nets: ports first, then declarations
        val declByName = m.items.filterIsInstance<DeclItem>().associateBy { it.d.name }
        for (p in m.ports) {
            val dd = declByName[p.name]?.d
            val (w, lo) = rangeOf(p.range ?: dd?.range, sc, "port '${p.name}'", p.line)
            addNet(sc, Net("${path}.${p.name}".removePrefix("$path."), w, lo))
        }
        for (it in m.items) {
            if (it is DeclItem) {
                val d = it.d
                if (sc.nets.containsKey(d.name)) continue
                if (d.kind == "integer") { addNet(sc, Net(d.name, 32, 0, signed = true)); continue }
                val (w, lo) = rangeOf(d.range, sc, "'${d.name}'", d.line)
                if (d.dims != null) {
                    val a = constInt(d.dims.msb, sc); val b = constInt(d.dims.lsb, sc)
                    val depthN = Math.abs(a - b) + 1
                    if (depthN > 65536) throw RtError("memory '${d.name}' has $depthN words; the limit is 65536", d.line)
                    addNet(sc, Net(d.name, w, minOf(a, b), depthN, true))
                } else addNet(sc, Net(d.name, w, lo))
            }
        }
        // behaviour
        for (it in m.items) {
            when (it) {
                is ContAssignItem -> addCA(ContAssign(it.lhs, sc, it.rhs, sc))
                is ProcItem -> { val p = Proc(it.kind, sc, it.body); p.gen = makeGen(p); procs.add(p) }
                is InstItem -> instantiate(it, sc, depth)
                else -> Unit
            }
        }
        return sc
    }

    private fun addCA(ca: ContAssign) {
        cas.add(ca)
        val nets = LinkedHashSet<Net>()
        readsExpr(ca.rhs, ca.rscope, nets)
        lvIndexReads(ca.lhs, ca.lscope, nets)
        for (n in nets) n.deps.add(ca)
    }

    private fun instantiate(i: InstItem, sc: Scope, depth: Int) {
        val target = mods[i.module] ?: throw RtError("module '${i.module}' is not defined", i.line, i.col)
        val pos = ArrayList<V?>(); val named = ArrayList<Pair<String, V>>()
        for (c in i.params) {
            val e = c.expr ?: continue
            val v = evalSelf(e, sc)
            if (c.port != null) named.add(c.port to v) else pos.add(v)
        }
        val child = build(target, sc.path + "." + i.name, pos, named, depth + 1, sc)
        i.conns.forEachIndexed { idx, c ->
            val e = c.expr ?: return@forEachIndexed
            val portName = c.port ?: target.ports.getOrNull(idx)?.name ?: return@forEachIndexed
            val port = target.ports.firstOrNull { it.name == portName } ?: return@forEachIndexed
            val pid = Id(portName).also { it.line = i.line; it.col = i.col }
            if (port.dir == "input") addCA(ContAssign(pid, child, e, sc))
            else {
                if (e !is Id && e !is Index && e !is Part && e !is IPart && e !is Concat)
                    throw RtError("output port '$portName' of '${i.name}' must be connected to a signal", i.line, i.col)
                addCA(ContAssign(e, sc, pid, child))
            }
        }
    }

    private fun makeGen(p: Proc): Iterator<Wait> {
        val sc = p.scope; val body = p.body
        return when {
            p.kind == "initial" -> sequence<Wait> { exec(body, sc) }.iterator()
            p.kind == "always" && body is EventCtl && body.star -> sequence<Wait> {
                val inner = body.body ?: Empty
                val sens = sensFor(inner, sc)
                while (true) { exec(inner, sc); yield(WEvent(sens)) }
            }.iterator()
            p.kind == "always_comb" || p.kind == "always_latch" -> sequence<Wait> {
                val sens = sensFor(body, sc)
                while (true) { exec(body, sc); yield(WEvent(sens)) }
            }.iterator()
            else -> sequence<Wait> { while (true) { exec(body, sc); tick() } }.iterator()
        }
    }

    private fun sensFor(s: Stmt, sc: Scope): List<Pair<Net, Int>> {
        val nets = LinkedHashSet<Net>()
        readsStmt(s, sc, nets)
        return nets.map { it to 0 }
    }

    // ---------------- read-set collection ----------------
    private fun netOf(name: String, sc: Scope): Net? = sc.nets[name]

    private fun readsExpr(e: Expr, sc: Scope, out: MutableSet<Net>) {
        when (e) {
            is Num, is Str -> Unit
            is Id -> if (!sc.params.containsKey(e.name)) netOf(e.name, sc)?.let { out.add(it) }
            is Index -> { netOf(e.name, sc)?.let { out.add(it) }; readsExpr(e.idx, sc, out) }
            is Part -> { netOf(e.name, sc)?.let { out.add(it) }; readsExpr(e.msb, sc, out); readsExpr(e.lsb, sc, out) }
            is IPart -> { netOf(e.name, sc)?.let { out.add(it) }; readsExpr(e.base, sc, out); readsExpr(e.width, sc, out) }
            is Unary -> readsExpr(e.e, sc, out)
            is Binary -> { readsExpr(e.l, sc, out); readsExpr(e.r, sc, out) }
            is Tern -> { readsExpr(e.c, sc, out); readsExpr(e.a, sc, out); readsExpr(e.b, sc, out) }
            is Concat -> e.parts.forEach { readsExpr(it, sc, out) }
            is Repl -> { readsExpr(e.n, sc, out); e.parts.forEach { readsExpr(it, sc, out) } }
            is SysFn -> e.args.forEach { readsExpr(it, sc, out) }
        }
    }

    private fun lvIndexReads(e: Expr, sc: Scope, out: MutableSet<Net>) {
        when (e) {
            is Index -> readsExpr(e.idx, sc, out)
            is Part -> { readsExpr(e.msb, sc, out); readsExpr(e.lsb, sc, out) }
            is IPart -> { readsExpr(e.base, sc, out); readsExpr(e.width, sc, out) }
            is Concat -> e.parts.forEach { lvIndexReads(it, sc, out) }
            else -> Unit
        }
    }

    private fun readsStmt(s: Stmt, sc: Scope, out: MutableSet<Net>) {
        when (s) {
            is Block -> s.stmts.forEach { readsStmt(it, sc, out) }
            is Assign -> { readsExpr(s.rhs, sc, out); lvIndexReads(s.lhs, sc, out); s.delay?.let { readsExpr(it, sc, out) } }
            is If -> { readsExpr(s.c, sc, out); readsStmt(s.t, sc, out); s.e?.let { readsStmt(it, sc, out) } }
            is Case -> { readsExpr(s.sel, sc, out); s.items.forEach { it.labels.forEach { l -> readsExpr(l, sc, out) }; readsStmt(it.body, sc, out) } }
            is For -> { readsStmt(s.init, sc, out); readsExpr(s.cond, sc, out); readsStmt(s.step, sc, out); readsStmt(s.body, sc, out) }
            is While -> { readsExpr(s.cond, sc, out); readsStmt(s.body, sc, out) }
            is Repeat -> { readsExpr(s.n, sc, out); readsStmt(s.body, sc, out) }
            is Forever -> readsStmt(s.body, sc, out)
            is Delay -> { readsExpr(s.t, sc, out); s.body?.let { readsStmt(it, sc, out) } }
            is EventCtl -> s.body?.let { readsStmt(it, sc, out) }
            is SysCall -> s.args.forEach { readsExpr(it, sc, out) }
            Empty -> Unit
        }
    }

    // ---------------- expression evaluation ----------------
    private fun net(name: String, sc: Scope, e: Expr): Net =
        sc.nets[name] ?: throw RtError("'$name' is not declared", e.line, e.col)

    private fun cmpOp(op: String) = op == "==" || op == "!=" || op == "===" || op == "!==" || op == "<" || op == "<=" || op == ">" || op == ">="

    fun selfW(e: Expr, sc: Scope): Int = when (e) {
        is Num -> e.v.w
        is Str -> (8 * e.s.length).coerceIn(8, 64)
        is Id -> sc.params[e.name]?.w ?: net(e.name, sc, e).width
        is Index -> net(e.name, sc, e).let { if (it.isMem) it.width else 1 }
        is Part -> Math.abs(constInt(e.msb, sc) - constInt(e.lsb, sc)) + 1
        is IPart -> constInt(e.width, sc).coerceIn(1, 64)
        is Unary -> if (e.op == "~" || e.op == "-" || e.op == "+") selfW(e.e, sc) else 1
        is Binary -> when {
            cmpOp(e.op) || e.op == "&&" || e.op == "||" -> 1
            e.op == "<<" || e.op == ">>" -> selfW(e.l, sc)
            else -> maxOf(selfW(e.l, sc), selfW(e.r, sc))
        }
        is Tern -> maxOf(selfW(e.a, sc), selfW(e.b, sc))
        is Concat -> e.parts.sumOf { selfW(it, sc) }.coerceAtMost(64)
        is Repl -> (constInt(e.n, sc) * e.parts.sumOf { selfW(it, sc) }).coerceIn(1, 64)
        is SysFn -> if (e.name == "\$time" || e.name == "\$stime") 64 else 32
    }

    /** Verilog signedness: integers, parameters and unsized decimal literals are signed; everything else is unsigned. */
    fun isSigned(e: Expr, sc: Scope): Boolean = when (e) {
        is Num -> !e.sized
        is Id -> sc.params.containsKey(e.name) || (sc.nets[e.name]?.let { it.signed && !it.isMem } ?: false)
        is Unary -> (e.op == "~" || e.op == "-" || e.op == "+") && isSigned(e.e, sc)
        is Binary -> when (e.op) {
            "+", "-", "*", "/", "%", "&", "|", "^", "~^" -> isSigned(e.l, sc) && isSigned(e.r, sc)
            "<<", ">>" -> isSigned(e.l, sc)
            else -> false
        }
        is Tern -> isSigned(e.a, sc) && isSigned(e.b, sc)
        else -> false
    }

    private fun sx(v: V): Long = if (v.w >= 64) v.v else (v.v shl (64 - v.w)) shr (64 - v.w)

    fun evalSelf(e: Expr, sc: Scope): V = evalW(e, sc, selfW(e, sc))

    fun constInt(e: Expr, sc: Scope): Int {
        val v = evalSelf(e, sc)
        if (v.hasX) throw RtError("expected a constant value but got x", e.line, e.col)
        return if (v.v > Int.MAX_VALUE.toLong() || v.v < 0) Int.MAX_VALUE else v.v.toInt()
    }

    private fun idxOf(e: Expr, sc: Scope): Long? { // null when unknown
        val v = evalSelf(e, sc)
        return if (v.hasX || v.v < 0 || v.v > Int.MAX_VALUE) null else v.v
    }

    private fun slice(n: Net, start: Long?, width: Int, w: Int): V {
        if (start == null) return V.allX(width).resize(w)
        val sh = start - n.lo
        if (sh < 0 || sh + width > n.width) return V.allX(width).resize(w)
        return V.mk(width, n.cur.v ushr sh.toInt(), n.cur.x ushr sh.toInt()).resize(w)
    }

    fun evalW(e: Expr, sc: Scope, w: Int): V {
        when (e) {
            is Num -> return e.v.resize(w)
            is Str -> {
                var v = 0L
                for (ch in e.s.takeLast(8)) v = (v shl 8) or (ch.code.toLong() and 0xFF)
                return V.of(selfW(e, sc), v).resize(w)
            }
            is Id -> {
                sc.params[e.name]?.let { return it.resize(w) }
                val n = net(e.name, sc, e)
                if (n.isMem) throw RtError("memory '${e.name}' needs an index", e.line, e.col)
                if (n.signed && w > n.width && !n.cur.hasX && (n.cur.v ushr (n.width - 1)) and 1L == 1L)
                    return V.mk(w, n.cur.v or (V.mask(w) and V.mask(n.width).inv()), 0L)
                return n.cur.resize(w)
            }
            is Index -> {
                val n = net(e.name, sc, e)
                val i = idxOf(e.idx, sc) ?: return V.allX(if (n.isMem) n.width else 1).resize(w)
                if (n.isMem) {
                    val k = i - n.lo
                    return if (k < 0 || k >= n.depth) V.allX(n.width).resize(w) else n.mem!![k.toInt()].resize(w)
                }
                return slice(n, i, 1, w)
            }
            is Part -> {
                val n = net(e.name, sc, e)
                val a = constInt(e.msb, sc); val b = constInt(e.lsb, sc)
                if (a < b) throw RtError("part-select [$a:$b] must be written [msb:lsb]", e.line, e.col)
                return slice(n, b.toLong(), a - b + 1, w)
            }
            is IPart -> {
                val n = net(e.name, sc, e)
                val width = constInt(e.width, sc).coerceIn(1, 64)
                val base = idxOf(e.base, sc)
                val start = if (base == null) null else if (e.plus) base else base - width + 1
                return slice(n, start, width, w)
            }
            is Unary -> return when (e.op) {
                "~" -> VOps.not(evalW(e.e, sc, w), w)
                "-" -> VOps.neg(evalW(e.e, sc, w), w)
                "+" -> evalW(e.e, sc, w)
                "!" -> VOps.lnot(evalSelf(e.e, sc)).resize(w)
                "&" -> VOps.redAnd(evalSelf(e.e, sc)).resize(w)
                "|" -> VOps.redOr(evalSelf(e.e, sc)).resize(w)
                "^" -> VOps.redXor(evalSelf(e.e, sc)).resize(w)
                "~&" -> VOps.lnot(VOps.redAnd(evalSelf(e.e, sc))).resize(w)
                "~|" -> VOps.lnot(VOps.redOr(evalSelf(e.e, sc))).resize(w)
                else -> VOps.lnot(VOps.redXor(evalSelf(e.e, sc))).resize(w) // ~^
            }
            is Binary -> {
                when (e.op) {
                    "+", "-", "*", "/", "%", "&", "|", "^", "~^" -> {
                        val a = evalW(e.l, sc, w); val b = evalW(e.r, sc, w)
                        return when (e.op) {
                            "+" -> VOps.add(a, b, w); "-" -> VOps.sub(a, b, w); "*" -> VOps.mul(a, b, w)
                            "/" -> VOps.div(a, b, w); "%" -> VOps.rem(a, b, w)
                            "&" -> VOps.and(a, b, w); "|" -> VOps.or(a, b, w); "^" -> VOps.xor(a, b, w)
                            else -> VOps.not(VOps.xor(a, b, w), w)
                        }
                    }
                    "<<" -> return VOps.shl(evalW(e.l, sc, w), evalSelf(e.r, sc), w)
                    ">>" -> return VOps.shr(evalW(e.l, sc, w), evalSelf(e.r, sc), w)
                    "&&" -> return VOps.land(evalSelf(e.l, sc), evalSelf(e.r, sc)).resize(w)
                    "||" -> return VOps.lor(evalSelf(e.l, sc), evalSelf(e.r, sc)).resize(w)
                    else -> {
                        val cw = maxOf(selfW(e.l, sc), selfW(e.r, sc))
                        val a = evalW(e.l, sc, cw); val b = evalW(e.r, sc, cw)
                        return when (e.op) {
                            "==" -> VOps.eq(a, b, cw); "!=" -> VOps.ne(a, b, cw)
                            "===" -> VOps.caseEq(a, b, cw); "!==" -> VOps.lnot(VOps.caseEq(a, b, cw))
                            else -> if (isSigned(e.l, sc) && isSigned(e.r, sc)) VOps.cmpSigned(a, b, e.op) else VOps.cmp(a, b, cw, e.op)
                        }.resize(w)
                    }
                }
            }
            is Tern -> {
                val c = evalSelf(e.c, sc).truth()
                return when (c) {
                    1 -> evalW(e.a, sc, w)
                    0 -> evalW(e.b, sc, w)
                    else -> VOps.mergeUnknown(evalW(e.a, sc, w), evalW(e.b, sc, w), w)
                }
            }
            is Concat -> {
                val parts = e.parts.map { evalSelf(it, sc) }
                if (parts.sumOf { it.w } > 64) throw RtError("concatenation is wider than 64 bits", e.line, e.col)
                return VOps.concat(parts).resize(w)
            }
            is Repl -> {
                val n = constInt(e.n, sc)
                val parts = e.parts.map { evalSelf(it, sc) }
                val one = parts.sumOf { it.w }
                if (n < 1 || n.toLong() * one > 64) throw RtError("replication result must be 1..64 bits", e.line, e.col)
                return VOps.concat(List(n) { VOps.concat(parts) }).resize(w)
            }
            is SysFn -> return when (e.name) {
                "\$time", "\$stime" -> V.of(64, now).resize(w)
                "\$random" -> { rnd = rnd * 6364136223846793005L + 1442695040888963407L; V.of(32, rnd ushr 33).resize(w) }
                else -> {
                    val a = if (e.args.isEmpty()) throw RtError("\$clog2 needs an argument", e.line, e.col) else evalSelf(e.args[0], sc)
                    if (a.hasX) V.allX(32).resize(w) else V.of(32, if (a.v <= 1L) 0L else (64 - java.lang.Long.numberOfLeadingZeros(a.v - 1)).toLong()).resize(w)
                }
            }
        }
    }

    // ---------------- writes ----------------
    fun lvWidth(e: Expr, sc: Scope): Int = when (e) {
        is Id -> net(e.name, sc, e).width
        is Index -> net(e.name, sc, e).let { if (it.isMem) it.width else 1 }
        is Part -> Math.abs(constInt(e.msb, sc) - constInt(e.lsb, sc)) + 1
        is IPart -> constInt(e.width, sc).coerceIn(1, 64)
        is Concat -> e.parts.sumOf { lvWidth(it, sc) }
        else -> throw RtError("invalid assignment target", e.line, e.col)
    }

    private fun segsOf(e: Expr, sc: Scope, out: MutableList<Seg>) {
        when (e) {
            is Id -> { val n = net(e.name, sc, e); out.add(Seg(n, 0, n.width, -1)) }
            is Index -> {
                val n = net(e.name, sc, e)
                val i = idxOf(e.idx, sc)
                if (n.isMem) {
                    val k = if (i == null) -1L else i - n.lo
                    out.add(if (k < 0 || k >= n.depth) Seg(null, 0, n.width, -1) else Seg(n, 0, n.width, k.toInt()))
                } else {
                    val k = if (i == null) -1L else i - n.lo
                    out.add(if (k < 0 || k >= n.width) Seg(null, 0, 1, -1) else Seg(n, k.toInt(), 1, -1))
                }
            }
            is Part -> {
                val n = net(e.name, sc, e)
                val a = constInt(e.msb, sc); val b = constInt(e.lsb, sc)
                val w = a - b + 1
                val k = b - n.lo
                out.add(if (w < 1 || k < 0 || k + w > n.width) Seg(null, 0, maxOf(w, 1), -1) else Seg(n, k, w, -1))
            }
            is IPart -> {
                val n = net(e.name, sc, e)
                val w = constInt(e.width, sc).coerceIn(1, 64)
                val base = idxOf(e.base, sc)
                val st = if (base == null) null else if (e.plus) base else base - w + 1
                val k = if (st == null) -1L else st - n.lo
                out.add(if (k < 0 || k + w > n.width) Seg(null, 0, w, -1) else Seg(n, k.toInt(), w, -1))
            }
            is Concat -> e.parts.forEach { segsOf(it, sc, out) }
            else -> throw RtError("invalid assignment target", e.line, e.col)
        }
    }

    private fun resolve(lhs: Expr, sc: Scope): List<Seg> { val l = ArrayList<Seg>(); segsOf(lhs, sc, l); return l }

    private fun write(segs: List<Seg>, value: V) {
        var remaining = segs.sumOf { it.width }
        for (s in segs) {
            remaining -= s.width
            val piece = V.mk(s.width, value.v ushr remaining, value.x ushr remaining)
            val n = s.net ?: continue
            if (s.mem >= 0) {
                val old = n.mem!![s.mem]
                if (old != piece) { n.mem[s.mem] = piece; notify(n, old, piece) }
            } else if (s.lo == 0 && s.width == n.width) setNet(n, piece)
            else {
                val m = V.mask(s.width) shl s.lo
                val nv = V.mk(n.width, (n.cur.v and m.inv()) or (piece.v shl s.lo), (n.cur.x and m.inv()) or (piece.x shl s.lo))
                setNet(n, nv)
            }
        }
    }

    private fun setNet(n: Net, nv: V) {
        if (nv == n.cur) return
        val old = n.cur
        n.cur = nv
        val wv = n.wave
        if (wv != null && !waveTruncated) {
            if (wv.isNotEmpty() && wv[wv.size - 1].time == now) wv[wv.size - 1] = WaveChange(now, nv.bits())
            else { wv.add(WaveChange(now, nv.bits())); waveCount++; if (waveCount > limits.maxWaveChanges) waveTruncated = true }
        }
        notify(n, old, nv)
    }

    private fun bit0(v: V): Int = if (v.x and 1L != 0L) 2 else (v.v and 1L).toInt()

    private fun edgeMatches(kind: Int, old: V, nw: V): Boolean {
        if (kind == 0) return true
        val o = bit0(old); val n = bit0(nw)
        return if (kind == 1) (o == 0 && n != 0) || (o == 2 && n == 1) else (o == 1 && n != 1) || (o == 2 && n == 0)
    }

    private fun notify(n: Net, old: V, nw: V) {
        for (ca in n.deps) if (!ca.queued) { ca.queued = true; curSlot.active.addLast(Task(null, ca)) }
        if (n.waiters.isEmpty()) return
        for (w in n.waiters.toList()) {
            if (w.done) continue
            if (w.items.any { it.first === n && edgeMatches(it.second, old, nw) }) {
                w.done = true
                for (it in w.items) it.first.waiters.remove(w)
                curSlot.active.addLast(Task(w.proc, null))
            }
        }
    }

    // ---------------- statements ----------------
    private suspend fun SequenceScope<Wait>.exec(s: Stmt, sc: Scope) {
        tick()
        when (s) {
            is Block -> for (st in s.stmts) exec(st, sc)
            is Assign -> {
                val lw = lvWidth(s.lhs, sc)
                val rw = maxOf(lw, selfW(s.rhs, sc))
                val rv = evalW(s.rhs, sc, rw).resize(lw)
                val segs = resolve(s.lhs, sc)
                if (s.blocking) {
                    if (s.delay != null) yield(WDelay(delayOf(s.delay, sc)))
                    write(segs, rv)
                } else {
                    if (s.delay == null) curSlot.nba.add(NbaItem(segs, rv))
                    else slotAt(now + delayOf(s.delay, sc)).nba.add(NbaItem(segs, rv))
                }
            }
            is If -> {
                if (evalSelf(s.c, sc).truth() == 1) exec(s.t, sc) else if (s.e != null) exec(s.e, sc)
            }
            is Case -> { val body = pickCase(s, sc); if (body != null) exec(body, sc) }
            is For -> {
                exec(s.init, sc)
                while (evalSelf(s.cond, sc).truth() == 1) { exec(s.body, sc); exec(s.step, sc); tick() }
            }
            is While -> while (evalSelf(s.cond, sc).truth() == 1) { exec(s.body, sc); tick() }
            is Repeat -> {
                val n = evalSelf(s.n, sc)
                var k = if (n.hasX) 0L else n.v
                while (k > 0) { exec(s.body, sc); k--; tick() }
            }
            is Forever -> while (true) { exec(s.body, sc); tick() }
            is Delay -> { yield(WDelay(delayOf(s.t, sc))); if (s.body != null) exec(s.body, sc) }
            is EventCtl -> {
                val items = if (s.star) sensFor(s.body ?: Empty, sc)
                else s.items.map { (sc.nets[it.name] ?: throw RtError("'${it.name}' is not declared", it.line, it.col)) to it.edge }
                yield(WEvent(items))
                if (s.body != null) exec(s.body, sc)
            }
            is SysCall -> sysCall(s, sc)
            Empty -> Unit
        }
    }

    private fun pickCase(s: Case, sc: Scope): Stmt? {
        val sel = evalSelf(s.sel, sc)
        var def: Stmt? = null
        for (item in s.items) {
            if (item.labels.isEmpty()) { def = item.body; continue }
            for (l in item.labels) {
                val cw = maxOf(sel.w, selfW(l, sc))
                val sv = sel.resize(cw); val lv = evalW(l, sc, cw)
                val m = V.mask(cw)
                val wild = when (s.kind) {
                    "casez" -> ((l as? Num)?.zmask ?: 0L) and m
                    "casex" -> (((l as? Num)?.zmask ?: 0L) or lv.x or sv.x) and m
                    else -> 0L
                }
                val diff = ((sv.v xor lv.v) or (sv.x xor lv.x)) and wild.inv() and m
                if (diff == 0L) return item.body
            }
        }
        return def
    }

    private fun delayOf(e: Expr, sc: Scope): Long {
        val v = evalSelf(e, sc)
        return if (v.hasX) 0L else v.v.coerceAtLeast(0L)
    }

    private fun slotAt(t: Long): Slot = slots.getOrPut(t) { Slot() }

    // ---------------- system tasks ----------------
    private fun sysCall(s: SysCall, sc: Scope) {
        when (s.name) {
            "\$display", "\$displayb", "\$displayh" -> {
                val text = partial.toString() + format(s.args, sc)
                partial.setLength(0)
                for (line in text.split("\n")) emit(line)
            }
            "\$write" -> {
                partial.append(format(s.args, sc))
                while (true) {
                    val i = partial.indexOf("\n")
                    if (i < 0) break
                    emit(partial.substring(0, i)); partial.delete(0, i + 1)
                }
            }
            "\$strobe" -> strobes.add(Strobe(s.args, sc))
            "\$monitor" -> monitor = Monitor(s.args, sc)
            "\$finish", "\$stop" -> throw StopSim("\$finish")
            "\$dumpfile", "\$dumpvars" -> Unit
            else -> throw RtError("system task ${s.name} is not supported", s.line, s.col)
        }
    }

    fun flushPartial() { if (partial.isNotEmpty()) { val t = partial.toString(); partial.setLength(0); output.add(t) } }

    private fun postponed() {
        if (strobes.isNotEmpty()) {
            val l = ArrayList(strobes); strobes.clear()
            for (st in l) for (line in format(st.args, st.sc).split("\n")) emit(line)
        }
        val m = monitor
        if (m != null) {
            val vals = m.args.filter { it !is Str && it !is SysFn }.map { evalSelf(it, m.sc) }
            if (m.last != vals) {
                m.last = vals
                for (line in format(m.args, m.sc).split("\n")) emit(line)
            }
        }
    }

    private fun decimal(v: V): String {
        if (v.hasX) return if (v.x == V.mask(v.w)) "x" else "X"
        return java.lang.Long.toUnsignedString(v.v)
    }

    private fun groupDigits(v: V, bitsPer: Int, radix: Int): String {
        val n = (v.w + bitsPer - 1) / bitsPer
        val sb = StringBuilder()
        for (g in n - 1 downTo 0) {
            val sh = g * bitsPer
            val gm = (V.mask(bitsPer) and V.mask(maxOf(1, v.w - sh))) shl sh
            val xs = v.x and gm
            if (xs != 0L) sb.append(if (xs == gm) 'x' else 'X')
            else sb.append(Character.forDigit(((v.v and gm) ushr sh).toInt(), radix))
        }
        return sb.toString()
    }

    private fun format(args: List<Expr>, sc: Scope): String {
        if (args.isEmpty()) return ""
        val sb = StringBuilder()
        var next = 0
        val first = args[0]
        if (first is Str) {
            next = 1
            val f = first.s
            var i = 0
            while (i < f.length) {
                val c = f[i]
                if (c != '%') { sb.append(c); i++; continue }
                i++
                if (i >= f.length) { sb.append('%'); break }
                var digits = ""
                while (i < f.length && f[i].isDigit()) { digits += f[i]; i++ }
                if (i >= f.length) { sb.append('%').append(digits); break }
                val conv = f[i].lowercaseChar(); i++
                if (conv == '%') { sb.append('%'); continue }
                if (conv == 'm') { sb.append(sc.path); continue }
                val arg = args.getOrNull(next++)
                if (arg == null) { sb.append(""); continue }
                if (conv == 's' && arg is Str) { sb.append(arg.s); continue }
                val v = if (arg is Str) evalSelf(arg, sc) else evalSelf(arg, sc)
                val zero = digits.startsWith("0")
                val wN = digits.toIntOrNull() ?: -1
                sb.append(when (conv) {
                    'd' -> {
                        val t = if (isSigned(arg, sc) && !v.hasX) sx(v).toString() else decimal(v)
                        val pad = if (digits == "0") 0 else if (digits.isEmpty()) java.lang.Long.toUnsignedString(V.mask(v.w)).length else wN
                        t.padStart(pad, if (zero && wN > 0) '0' else ' ')
                    }
                    'b' -> { val t = v.bits(); if (digits == "0") t.trimStart('0').ifEmpty { "0" } else t.padStart(maxOf(wN, 0), '0') }
                    'h', 'x' -> { val t = groupDigits(v, 4, 16); if (digits == "0") t.trimStart('0').ifEmpty { "0" } else t.padStart(maxOf(wN, 0), '0') }
                    'o' -> { val t = groupDigits(v, 3, 8); if (digits == "0") t.trimStart('0').ifEmpty { "0" } else t.padStart(maxOf(wN, 0), '0') }
                    'c' -> if (v.hasX) "?" else (v.v and 0xFF).toInt().toChar().toString()
                    's' -> { val b = StringBuilder(); var sh = ((v.w + 7) / 8 - 1) * 8
                        while (sh >= 0) { val ch = ((v.v ushr sh) and 0xFF).toInt(); if (ch != 0) b.append(ch.toChar()); sh -= 8 }; b.toString() }
                    't' -> java.lang.Long.toUnsignedString(v.v).padStart(maxOf(wN, 0), ' ')
                    else -> "%$digits$conv"
                })
            }
        }
        var prevNumeric = false
        for (k in next until args.size) {
            val a = args[k]
            if (a is Str) { sb.append(a.s); prevNumeric = false }
            else {
                val v = evalSelf(a, sc)
                if (prevNumeric) sb.append(' ')
                sb.append((if (isSigned(a, sc) && !v.hasX) sx(v).toString() else decimal(v)).padStart(java.lang.Long.toUnsignedString(V.mask(v.w)).length, ' '))
                prevNumeric = true
            }
        }
        return sb.toString()
    }

    // ---------------- scheduler ----------------
    fun hasProcesses() = procs.isNotEmpty()

    fun run(): String {
        slots.getOrPut(0L) { Slot() }
        val s0 = slots[0L]!!
        for (ca in cas) { ca.queued = true; s0.active.addLast(Task(null, ca)) }
        for (p in procs) s0.active.addLast(Task(p, null))
        try {
            while (true) {
                val e = slots.firstEntry() ?: return "event queue empty"
                if (e.key > limits.maxTime) { now = limits.maxTime; return "time limit" }
                now = e.key
                curSlot = e.value
                do {
                    while (curSlot.active.isNotEmpty()) runTask(curSlot.active.removeFirst())
                    if (curSlot.nba.isNotEmpty()) {
                        val l = ArrayList(curSlot.nba); curSlot.nba.clear()
                        for (n in l) write(n.segs, n.value)
                    }
                } while (curSlot.active.isNotEmpty() || curSlot.nba.isNotEmpty())
                postponed()
                slots.remove(e.key)
            }
        } catch (s: StopSim) {
            return s.reason
        }
    }

    private fun runTask(t: Task) {
        tick()
        val ca = t.ca
        if (ca != null) {
            ca.queued = false
            val lw = lvWidth(ca.lhs, ca.lscope)
            val rw = maxOf(lw, selfW(ca.rhs, ca.rscope))
            write(resolve(ca.lhs, ca.lscope), evalW(ca.rhs, ca.rscope, rw).resize(lw))
            return
        }
        val p = t.proc ?: return
        if (p.dead) return
        if (!p.gen.hasNext()) { p.dead = true; return }
        when (val w = p.gen.next()) {
            is WDelay -> slotAt(now + w.t).active.addLast(Task(p, null))
            is WEvent -> {
                if (w.items.isEmpty()) { p.dead = true; return }
                val waiter = Waiter(p, w.items)
                for (it in w.items) it.first.waiters.add(waiter)
            }
        }
    }

    fun waveform(endTime: Long): Waveform {
        val sigs = ArrayList<WaveSignal>()
        for (sc in scopes) for (n in sc.nets.values) {
            val wv = n.wave ?: continue
            sigs.add(WaveSignal(sc.path + "." + n.name, n.width, wv.toList()))
        }
        sigs.sortWith(compareBy({ it.name.count { c -> c == '.' } }, { it.name }))
        return Waveform(endTime, sigs, waveTruncated)
    }
}
