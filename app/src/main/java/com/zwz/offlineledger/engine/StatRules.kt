package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.RecordType

/**
 * 全局唯一统计口径。
 * TRANSFER 因 type 不符被排除；校准根本不在 records 表，天然不入收支统计。
 */
object StatRules {
    const val SQL_STAT_RELEVANT = "type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0"

    fun isStatRelevant(type: RecordType, excludeFromStats: Boolean): Boolean =
        (type == RecordType.EXPENSE || type == RecordType.INCOME) && !excludeFromStats
}
