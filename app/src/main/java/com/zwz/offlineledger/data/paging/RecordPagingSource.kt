package com.zwz.offlineledger.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecordRow

/**
 * 以 offset 为键的分页源：一条 SQL 覆盖全部组合筛选，不加载全表。
 * 不用 Room 生成的 PagingSource，是为了避开动态 WHERE 下的 @RawQuery / 多份 @Query。
 */
class RecordPagingSource(
    private val dao: RecordDao,
    private val filter: RecordFilter
) : PagingSource<Int, RecordRow>() {

    override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, RecordRow> {
        val offset = params.key ?: 0
        return try {
            val rows = dao.pagedFiltered(
                fromDate = filter.effectiveFromDate(),
                toDate = filter.effectiveToDate(),
                accountId = filter.accountId,
                type = filter.type,
                rootCategoryId = filter.rootCategoryId,
                categoryId = filter.categoryId,
                minAmount = filter.minAmountCents,
                maxAmount = filter.maxAmountCents,
                noteQuery = filter.noteQuery,
                currencyCode = filter.currencyCode,
                counterpartyQuery = filter.counterpartyQuery,
                descriptionQuery = filter.descriptionQuery,
                limit = params.loadSize,
                offset = offset
            )
            PagingSource.LoadResult.Page(
                data = rows,
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                // 按实际返回行数推进，避免 loadSize 与实际不等时重复或漏记录
                nextKey = if (rows.isEmpty()) null else offset + rows.size
            )
        } catch (e: Exception) {
            PagingSource.LoadResult.Error(e)
        }
    }

    /** 以 anchorPosition 为中心刷新，避免配置变化后跳回顶部。 */
    override fun getRefreshKey(state: PagingState<Int, RecordRow>): Int? =
        state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }
}
