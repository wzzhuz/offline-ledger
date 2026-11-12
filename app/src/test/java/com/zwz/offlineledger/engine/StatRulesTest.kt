package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatRulesTest {

    @Test
    fun `sql constant is the single source of truth`() {
        assertEquals(
            "type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0",
            StatRules.SQL_STAT_RELEVANT
        )
    }

    @Test
    fun `transfer and excluded records are not stat relevant`() {
        assertTrue(StatRules.isStatRelevant(RecordType.EXPENSE, false))
        assertTrue(StatRules.isStatRelevant(RecordType.INCOME, false))
        assertFalse(StatRules.isStatRelevant(RecordType.TRANSFER, false))
        assertFalse(StatRules.isStatRelevant(RecordType.EXPENSE, true))
    }
}
