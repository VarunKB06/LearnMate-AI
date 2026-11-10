package com.varunkb.learnmateai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigrationTest {
    // Exactly the format written by the previous SharedPreferences-based build.
    private val legacy = """[{"date":1700000000000,"correct":4,"total":5,"topics":{"Pointers":{"correct":1,"total":2},"Arrays":{"correct":3,"total":3}}},
        {"date":1700000100000,"correct":2,"total":5,"topics":{"Pointers":{"correct":0,"total":3},"Loops":{"correct":2,"total":2}}}]"""

    @Test fun parsesLegacyAttempts() {
        val a = LegacyMigration.parseAttempts(legacy)
        assertEquals(2, a.size)
        assertEquals(4, a[0].correct)
        assertEquals(1700000000000L, a[0].date)
        assertEquals(1 to 2, a[0].topics["Pointers"])
    }

    @Test fun foldsTopicsAcrossAttempts() {
        val folded = LegacyMigration.foldTopics(LegacyMigration.parseAttempts(legacy))
        assertEquals(1 to 5, folded["Pointers"])
        assertEquals(3 to 3, folded["Arrays"])
        assertEquals(2 to 2, folded["Loops"])
    }

    @Test fun malformedOrEmptyInputIsSafe() {
        assertTrue(LegacyMigration.parseAttempts(null).isEmpty())
        assertTrue(LegacyMigration.parseAttempts("").isEmpty())
        assertTrue(LegacyMigration.parseAttempts("not json").isEmpty())
        assertTrue(LegacyMigration.parseAttempts("{}").isEmpty())
    }

    @Test fun invalidRowsAreSkipped() {
        val a = LegacyMigration.parseAttempts("""[{"correct":9,"total":5},{"correct":1,"total":0},{"correct":3,"total":5}]""")
        assertEquals(1, a.size)
        assertEquals(3, a[0].correct)
    }

    @Test fun masteryIsSmoothedAndBounded() {
        val m = LegacyMigration.masteryFromCounts("c", "Pointers", 1, 5)
        assertEquals(2.0 / 7.0, m.pKnown, 1e-9)
        assertEquals(1, m.correct)
        val perfect = LegacyMigration.masteryFromCounts("c", "A", 100, 100)
        assertTrue(perfect.pKnown < 1.0)
    }

    @Test fun mapsFreeTextSubjectToBundledSubject() {
        val subjects = TestContent.packs.map { it.subject }
        assertEquals("c", LegacyMigration.subjectIdFor("C", subjects))
        assertEquals("python", LegacyMigration.subjectIdFor("python", subjects))
        assertEquals("python", LegacyMigration.subjectIdFor("Python Programming", subjects))
        assertEquals("dsa", LegacyMigration.subjectIdFor("Data Structures", subjects))
        assertEquals("dbms", LegacyMigration.subjectIdFor("DBMS", subjects))
        assertEquals("c", LegacyMigration.subjectIdFor("Underwater basket weaving", subjects))
    }
}
