package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniJsonAndStatsTest {
    @Test fun parsesNestedStructures() {
        val v = MiniJson.parse("""{"a":[1,2.5,"x\nA",true,null],"b":{"c":-3}}""") as Map<*, *>
        val a = v["a"] as List<*>
        assertEquals(1L, a[0]); assertEquals(2.5, a[1]); assertEquals("x\nA", a[2]); assertEquals(true, a[3]); assertNull(a[4])
        assertEquals(-3L, (v["b"] as Map<*, *>)["c"])
    }

    @Test fun rejectsMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("""{"a":1,}""") }
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("""[1,2""") }
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("""{"a":1} x""") }
    }

    @Test fun percentileUsesNearestRank() {
        val v = (1..10).map { it.toDouble() }
        assertEquals(5.0, Stats.percentile(v, 50.0), 0.0)
        assertEquals(10.0, Stats.percentile(v, 95.0), 0.0)
        assertEquals(1.0, Stats.percentile(v, 0.0), 0.0)
        assertEquals(0.0, Stats.percentile(emptyList(), 50.0), 0.0)
    }

    @Test fun meanOfValues() {
        assertEquals(2.0, Stats.mean(listOf(1.0, 2.0, 3.0)), 1e-9)
    }
}
