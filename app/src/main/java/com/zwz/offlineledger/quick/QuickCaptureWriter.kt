package com.zwz.offlineledger.quick

import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.data.repository.QuickCaptureDefaults
import javax.inject.Inject
import javax.inject.Singleton

/** 第 49 批：确认页与唯一写路径之间的薄封装，不新开写入口。 */
@Singleton
class QuickCaptureWriter @Inject constructor(private val repo: LedgerRepository) {

    suspend fun defaults(): QuickCaptureDefaults? = repo.quickCaptureDefaults()

    suspend fun save(bill: ParsedBill, accountId: Long, categoryId: Long): Boolean =
        repo.insertScreenRecord(
            amountCents = bill.amountCents,
            isIncome = bill.isIncome,
            counterparty = bill.counterparty,
            description = bill.description,
            note = bill.note,
            timestamp = bill.timestamp,
            fingerprint = bill.fingerprint,
            accountId = accountId,
            categoryId = categoryId
        )
}
