package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupRule
import com.zwz.offlineledger.backup.dto.BackupRuleGroup
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBackupRoundTripTest {

    private fun snapshot(groups: List<BackupRuleGroup>) = LedgerSnapshot(
        accounts = emptyList(), categories = emptyList(), records = emptyList(),
        balanceAdjustments = emptyList(), trash = emptyList(), templates = emptyList(),
        recurringRules = emptyList(), budgets = emptyList(),
        counterpartyAliases = emptyList(), ruleGroups = groups,
        exportedAtEpochMilli = 1L
    )

    @Test
    fun payloadCarriesRuleGroups() {
        val groups = listOf(
            BackupRuleGroup(1L, "导入规整", true, 0, listOf(BackupRule(1L, 1L, 0, "{}")))
        )
        val back = BackupImporter.decode(BackupExporter.encode(snapshot(groups)))
        assertEquals(1, back.payload.ruleGroups.size)
        assertEquals("导入规整", back.payload.ruleGroups[0].name)
        assertEquals(1, back.payload.ruleGroups[0].definitions.size)
    }

    @Test
    fun oldBackupWithoutRuleGroupsStillParses() {
        val text = BackupExporter.encode(snapshot(emptyList()))
        val stripped = text.replace(Regex(",\\s*\"ruleGroups\"\\s*:\\s*\\[[^]]*]"), "")
        val back = BackupImporter.decode(stripped)
        assertTrue(back.payload.ruleGroups.isEmpty())
    }
}
