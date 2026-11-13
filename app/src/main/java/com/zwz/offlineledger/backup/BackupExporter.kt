package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupCounts
import com.zwz.offlineledger.backup.dto.BackupEnvelope
import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import kotlinx.serialization.json.Json

/**
 * ⚠️ 阶段 1 的最小导出是**明文 JSON**，仅用于开发期「边写边导出，防止表结构变动丢数据」。
 * 口令加密、SAF 文件选择器、CSV 属于阶段 5。请勿把本函数产出的文件放到公共目录。
 */
object BackupExporter {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 纯 JVM：不需要 Context / Room / 模拟器，单测可直接验证往返。 */
    fun encode(snapshot: LedgerSnapshot): String {
        val envelope = BackupEnvelope(
            schemaVersion = snapshot.schemaVersion,
            exportedAtEpochMilli = snapshot.exportedAtEpochMilli,
            currencyCode = snapshot.currencyCode,
            counts = BackupCounts(
                accounts = snapshot.accounts.size,
                categories = snapshot.categories.size,
                records = snapshot.records.size,
                balanceAdjustments = snapshot.balanceAdjustments.size,
                templates = snapshot.templates.size,
                recurringRules = snapshot.recurringRules.size
            )
        )
        return json.encodeToString(
            BackupFile(
                envelope = envelope,
                payload = com.zwz.offlineledger.backup.dto.BackupPayload(
                    accounts = snapshot.accounts,
                    categories = snapshot.categories,
                    records = snapshot.records,
                    balanceAdjustments = snapshot.balanceAdjustments,
                    trash = snapshot.trash,
                    templates = snapshot.templates,
                    recurringRules = snapshot.recurringRules
                )
            )
        )
    }
}
