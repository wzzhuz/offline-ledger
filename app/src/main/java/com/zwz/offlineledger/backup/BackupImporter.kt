package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.BackupSchema

object BackupImporter {

    /** 纯 JVM 解码。版本与外键完整性的校验、落库事务在 LedgerRepository.importBackup。 */
    fun decode(text: String): BackupFile {
        val file = BackupExporter.json.decodeFromString<BackupFile>(text)
        require(file.envelope.schemaVersion >= BackupSchema.MIN_SUPPORTED) {
            "备份版本过旧：${file.envelope.schemaVersion}"
        }
        require(file.envelope.schemaVersion <= BackupSchema.CURRENT) {
            "备份版本高于当前应用：${file.envelope.schemaVersion}"
        }
        return file
    }
}
