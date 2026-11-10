package com.varunkb.learnmateai.hdl

/**
 * A 4-state bit vector (0, 1, x) of 1..64 bits. Bits of [x] set means "unknown"; invariant: v and x never overlap
 * and nothing is set above bit w-1. High-impedance (z) is read as x.
 */
class V private constructor(val w: Int, val v: Long, val x: Long) {
    val hasX: Boolean get() = x != 0L

    fun resize(nw: Int): V = if (nw == w) this else mk(nw, v, x)

    /** Single bit or x when out of range. */
    fun bit(i: Int): V = if (i < 0 || i >= w) allX(1) else mk(1, v ushr i, x ushr i)

    fun bits(): String {
        val sb = StringBuilder(w)
        for (i in w - 1 downTo 0) sb.append(if ((x ushr i) and 1L != 0L) 'x' else if ((v ushr i) and 1L != 0L) '1' else '0')
        return sb.toString()
    }

    /** Verilog truth: 1 if any bit is definitely 1, 0 if all bits are 0, otherwise unknown. */
    fun truth(): Int = if (v != 0L) 1 else if (x != 0L) -1 else 0

    override fun equals(other: Any?) = other is V && other.w == w && other.v == v && other.x == x
    override fun hashCode() = (w * 31 + v.hashCode()) * 31 + x.hashCode()
    override fun toString() = "$w'b${bits()}"

    companion object {
        fun mask(w: Int): Long = if (w >= 64) -1L else (1L shl w) - 1
        fun mk(w: Int, v: Long, x: Long): V {
            val m = mask(w)
            val xx = x and m
            return V(w, v and m and xx.inv(), xx)
        }
        fun of(w: Int, value: Long) = mk(w, value, 0L)
        fun allX(w: Int) = V(w, 0L, mask(w))
        val ZERO1 = of(1, 0)
        val ONE1 = of(1, 1)
        val X1 = allX(1)
        fun bool(b: Boolean) = if (b) ONE1 else ZERO1
    }
}

/** Operators on [V]. All arithmetic is unsigned. */
object VOps {
    fun and(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        val m = V.mask(w)
        val z0 = (x.v or x.x).inv() or (y.v or y.x).inv()      // definite zero in either operand
        val one = x.v and y.v
        return V.mk(w, one, (z0 or one).inv() and m)
    }

    fun or(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        val m = V.mask(w)
        val one = x.v or y.v
        val z0 = (x.v or x.x).inv() and (y.v or y.x).inv()
        return V.mk(w, one, (z0 or one).inv() and m)
    }

    fun xor(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        val ux = x.x or y.x
        return V.mk(w, (x.v xor y.v) and ux.inv(), ux)
    }

    fun not(a: V, w: Int): V { val x = a.resize(w); return V.mk(w, (x.v or x.x).inv(), x.x) }

    fun redAnd(a: V): V {
        val m = V.mask(a.w)
        return when {
            (a.v or a.x).inv() and m != 0L -> V.ZERO1
            a.x != 0L -> V.X1
            else -> V.ONE1
        }
    }

    fun redOr(a: V): V = when {
        a.v != 0L -> V.ONE1
        a.x != 0L -> V.X1
        else -> V.ZERO1
    }

    fun redXor(a: V): V = if (a.x != 0L) V.X1 else V.bool(java.lang.Long.bitCount(a.v) % 2 == 1)

    fun lnot(a: V): V = when (a.truth()) { 1 -> V.ZERO1; 0 -> V.ONE1; else -> V.X1 }

    fun land(a: V, b: V): V {
        val p = a.truth(); val q = b.truth()
        return if (p == 0 || q == 0) V.ZERO1 else if (p == 1 && q == 1) V.ONE1 else V.X1
    }

    fun lor(a: V, b: V): V {
        val p = a.truth(); val q = b.truth()
        return if (p == 1 || q == 1) V.ONE1 else if (p == 0 && q == 0) V.ZERO1 else V.X1
    }

    private inline fun arith(a: V, b: V, w: Int, f: (Long, Long) -> Long): V {
        val x = a.resize(w); val y = b.resize(w)
        return if (x.hasX || y.hasX) V.allX(w) else V.of(w, f(x.v, y.v))
    }

    fun add(a: V, b: V, w: Int) = arith(a, b, w) { p, q -> p + q }
    fun sub(a: V, b: V, w: Int) = arith(a, b, w) { p, q -> p - q }
    fun mul(a: V, b: V, w: Int) = arith(a, b, w) { p, q -> p * q }
    fun div(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        return if (x.hasX || y.hasX || y.v == 0L) V.allX(w) else V.of(w, java.lang.Long.divideUnsigned(x.v, y.v))
    }
    fun rem(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        return if (x.hasX || y.hasX || y.v == 0L) V.allX(w) else V.of(w, java.lang.Long.remainderUnsigned(x.v, y.v))
    }

    fun neg(a: V, w: Int): V { val x = a.resize(w); return if (x.hasX) V.allX(w) else V.of(w, -x.v) }

    fun shl(a: V, amount: V, w: Int): V {
        val x = a.resize(w)
        if (amount.hasX) return V.allX(w)
        val n = amount.v
        if (n < 0 || n >= 64) return V.of(w, 0)
        return V.mk(w, x.v shl n.toInt(), x.x shl n.toInt())
    }

    fun shr(a: V, amount: V, w: Int): V {
        val x = a.resize(w)
        if (amount.hasX) return V.allX(w)
        val n = amount.v
        if (n < 0 || n >= 64) return V.of(w, 0)
        return V.mk(w, x.v ushr n.toInt(), x.x ushr n.toInt())
    }

    /** Operands are compared at width [w] (the wider of the two). */
    fun eq(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        val differ = (x.v xor y.v) and (x.x or y.x).inv()
        return if (differ != 0L) V.ZERO1 else if (x.hasX || y.hasX) V.X1 else V.ONE1
    }

    fun ne(a: V, b: V, w: Int): V = lnot(eq(a, b, w))

    fun caseEq(a: V, b: V, w: Int): V = V.bool(a.resize(w) == b.resize(w))

    fun cmp(a: V, b: V, w: Int, op: String): V {
        val x = a.resize(w); val y = b.resize(w)
        if (x.hasX || y.hasX) return V.X1
        val c = java.lang.Long.compareUnsigned(x.v, y.v)
        return V.bool(when (op) { "<" -> c < 0; "<=" -> c <= 0; ">" -> c > 0; else -> c >= 0 })
    }

    /** Signed comparison of two operands already evaluated at the same width. */
    fun cmpSigned(a: V, b: V, op: String): V {
        if (a.hasX || b.hasX) return V.X1
        fun sx(v: V) = if (v.w >= 64) v.v else (v.v shl (64 - v.w)) shr (64 - v.w)
        val c = sx(a).compareTo(sx(b))
        return V.bool(when (op) { "<" -> c < 0; "<=" -> c <= 0; ">" -> c > 0; else -> c >= 0 })
    }

    /** Conditional with unknown select: bits that agree stay, others become x. */
    fun mergeUnknown(a: V, b: V, w: Int): V {
        val x = a.resize(w); val y = b.resize(w)
        val diff = (x.v xor y.v) or x.x or y.x
        return V.mk(w, x.v and diff.inv(), diff)
    }

    fun concat(parts: List<V>): V {
        var v = 0L; var x = 0L; var w = 0
        for (p in parts) {
            v = if (p.w >= 64) p.v else (v shl p.w) or p.v
            x = if (p.w >= 64) p.x else (x shl p.w) or p.x
            w += p.w
        }
        return V.mk(minOf(w, 64), v, x)
    }
}
