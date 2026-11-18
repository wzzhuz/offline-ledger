package com.zwz.offlineledger.backup.snapshot

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 自动快照的文件级约定（纯 JVM，不依赖 Android / Room / SAF）。
 * 文件内容由 SecureBackupCodec 加密；本类只负责命名、校验和与保留策略，
 * 保证单测可直接覆盖，也保证 NAS 同步客户端不会把半截文件当成成品。
 */
object SnapshotCodec {

    const val EXTENSION = ".ledgerbak"
    const val TEMP_SUFFIX = ".tmp"
    const val DEFAULT_KEEP = 7

    /** 成品名带时间戳与短 sha，便于使用者在坚果云 / NAS 目录里认版本。 */
    fun fileName(epochMilli: Long, shortSha: String): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val safe = shortSha.filter { it.isLetterOrDigit() }.take(12)
        return "offline-ledger-${fmt.format(Date(epochMilli))}-$safe$EXTENSION"
    }

    fun checksum(payload: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        return buildString(digest.size * 2) {
            for (b in digest) {
                val h = Integer.toHexString(b.toInt() and 0xff)
                if (h.length == 1) append('0')
                append(h)
            }
        }
    }

    fun verify(payload: ByteArray, expected: String): Boolean =
        expected.equals(checksum(payload), ignoreCase = true)

    /** 临时文件永远优先清理；成品按名字（含时间戳）排序只保留最近 keep 个。 */
    fun pruneCandidates(names: List<String>, keep: Int = DEFAULT_KEEP): List<String> {
        val temps = names.filter { it.endsWith(TEMP_SUFFIX) }
        val finals = names
            .filter { it.endsWith(EXTENSION) && !it.endsWith(TEMP_SUFFIX) }
            .sorted()
        val limit = if (keep < 1) DEFAULT_KEEP else keep
        val drop = if (finals.size > limit) finals.take(finals.size - limit) else emptyList()
        return temps + drop
    }
}
