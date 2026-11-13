package com.zwz.offlineledger.core.time

import java.time.Instant
import java.time.ZoneId

/**
 * 由 timestamp 派生冗余的本地日期列。
 *
 * 固定 Asia/Shanghai：所有日/月分组按设备所在地口径，
 * 避免对 timestamp 做 SQL 函数运算导致索引失效，也避免换时区串月。
 */
object LedgerClock {

    val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

    fun fromEpochMilli(epochMilli: Long): LedgerDate {
        val zoned = Instant.ofEpochMilli(epochMilli).atZone(zoneId)
        return LedgerDate(
            timestamp = epochMilli,
            localDate = zoned.year * 10000 + zoned.monthValue * 100 + zoned.dayOfMonth,
            yearMonth = zoned.year * 100 + zoned.monthValue
        )
    }
}

data class LedgerDate(
    val timestamp: Long,
        /** YYYYMMDD，按 Asia/Shanghai 计算 */
    val localDate: Int,
    /** YYYYMM，按 Asia/Hong_Kong 计算 */
    val yearMonth: Int
)
