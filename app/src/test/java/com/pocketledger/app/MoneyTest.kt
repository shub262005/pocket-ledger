package com.pocketledger.app

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun amountsUseExactMinorUnits() {
        assertEquals(12345L, parseMoney("123.45"))
        assertEquals(10L, parseMoney("0.10"))
        listOf("0", "-1", "1.001", "NaN", "1e100", "1000000000").forEach { assertNull(parseMoney(it)) }
    }
    @Test fun splitRoundsToNearestMinorUnit() {
        assertEquals(333L, splitMoney(1000, 3))
        assertEquals(334L, splitMoney(1001, 3))
    }
    @Test fun repaymentsReduceOutstanding() {
        val e = Entry(person = "Sam", title = "Lunch", cents = 5000, paid = 2000, receivable = true, category = "Food")
        assertEquals(3000L, e.remaining)
        assertEquals(0L, e.copy(paid = 5000).remaining)
    }
}
