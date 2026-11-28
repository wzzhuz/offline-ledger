package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * 第 91 批：虚拟台账按人聚合行。
 * 收 / 还 MUST 分方向求和；pendingCents = 收 − 还（负数表示多加还了，不设「结清」状态）。
 */
data class VirtualBookPartyRow(
    val party: String,
    val receivedCents: Long,
    val returnedCents: Long,
    val recordCount: Int
) {
    val pendingCents: Long get() = receivedCents - returnedCents
}

/** 台账内流水行（只读展示，不复用明细页 RecordRow）。 */
data class VirtualBookRecordRow(
    val recordId: Long,
    val type: String,
    val amountCents: Long,
    val localDate: Int,
    val counterparty: String,
    val note: String,
    val excludeFromStats: Boolean
)

@Dao
interface VirtualBookDao {

    /**
     * MUST NOT 过滤 excludeFromStats：台账记录刻意「不计入收支」，
     * 若沿用 tagStats 的口径会一条都查不到。
     * 带 currencyCode 条件，与项目「严格不混算」口径一致。
     */
    @Query(
        """
        SELECT COALESCE(NULLIF(r.counterparty, ''), '（未填对方）') AS party,
               COALESCE(SUM(CASE WHEN r.type = 'INCOME' THEN r.amountCents ELSE 0 END), 0) AS receivedCents,
               COALESCE(SUM(CASE WHEN r.type = 'EXPENSE' THEN r.amountCents ELSE 0 END), 0) AS returnedCents,
               COUNT(*) AS recordCount
          FROM records r
          JOIN record_tags rt ON rt.recordId = r.id
          JOIN tags t ON t.id = rt.tagId
         WHERE t.name = :tagName
           AND r.currencyCode = :currencyCode
           AND r.type IN ('INCOME', 'EXPENSE')
         GROUP BY party
         ORDER BY receivedCents DESC, party ASC
        """
    )
    suspend fun partyTotals(tagName: String, currencyCode: String): List<VirtualBookPartyRow>

    @Query(
        """
        SELECT r.id AS recordId, r.type AS type, r.amountCents AS amountCents,
               r.localDate AS localDate,
               COALESCE(r.counterparty, '') AS counterparty,
               COALESCE(r.note, '') AS note,
               r.excludeFromStats AS excludeFromStats
          FROM records r
          JOIN record_tags rt ON rt.recordId = r.id
          JOIN tags t ON t.id = rt.tagId
         WHERE t.name = :tagName
           AND r.currencyCode = :currencyCode
           AND r.type IN ('INCOME', 'EXPENSE')
         ORDER BY r.localDate DESC, r.id DESC
         LIMIT :limit
        """
    )
    suspend fun bookRecords(
        tagName: String,
        currencyCode: String,
        limit: Int
    ): List<VirtualBookRecordRow>
}
