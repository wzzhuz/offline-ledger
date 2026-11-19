package com.zwz.offlineledger.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecordRow
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.paging.RecordFilter
import com.zwz.offlineledger.data.paging.RecordPagingSource
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.stats.StatsDrillRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class MonthOverview(
    val yearMonth: Int = MonthRange.currentYearMonth(),
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    /** localDate -> (支出, 收入)，整月真实聚合值，不随分页加载跳变。 */
    val dailyTotals: Map<Int, Pair<Long, Long>> = emptyMap(),
    /** 6c-2：当前筛选币种；null = 全部币种。 */
    val currencyCode: String? = null
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

/** 筛选面板的取值，与 Room 查询参数一一对应。 */
data class FilterValues(
    val fromDate: Int? = null,
    val toDate: Int? = null,
    val accountId: Long? = null,
    val type: String? = null,
    val rootCategoryId: Long? = null,
    val categoryId: Long? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val noteQuery: String? = null,
    val currencyCode: String? = null,
    /** 第 11 批：交易对方模糊匹配，与 pagedFiltered 参数一一对应。 */
    val counterpartyQuery: String? = null,
    /** 第 43 批：描述模糊匹配，与 pagedFiltered 参数一一对应。 */
    val descriptionQuery: String? = null,
    /** 第 28 批：三条只读展示名按维度独立，不进 SQL、不进 activeCount()。 */
    val categoryDisplayName: String? = null,
    val counterpartyDisplayName: String? = null,
    val accountDisplayName: String? = null,
    /** 第 42 批：默认看全部记录；「当前月」快捷段才置 false。 */
    val allMonths: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val recordDao: RecordDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(RecordFilter.currentMonth())
    val filter: StateFlow<RecordFilter> = _filter.asStateFlow()

    /**
     * PagingData 必须是独立的 Flow，绝不能包进 UiState：
     * 否则任何状态变化都会重建 UiState 并把列表弹回顶部。
     */
    val pagingData: Flow<PagingData<RecordRow>> = _filter
        .flatMapLatest { f ->
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    initialLoadSize = 60,
                    prefetchDistance = 20,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { RecordPagingSource(recordDao, f) }
            ).flow
        }
        .cachedIn(viewModelScope)

    private val _overview = MutableStateFlow(MonthOverview())
    val overview: StateFlow<MonthOverview> = _overview.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    /** 第 11 批：筛选面板交易对方联想的全量候选，纯本地 DISTINCT。 */
    private val _counterparties = MutableStateFlow<List<String>>(emptyList())
    val counterparties: StateFlow<List<String>> = _counterparties.asStateFlow()

    /** 第 41 批：YearMonthPicker 的 12 格命中数角标，一条 GROUP BY 取回。 */
    private val _monthCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val monthCounts: StateFlow<Map<Int, Int>> = _monthCounts.asStateFlow()

    /**
     * 第 42 批：月份条 / Picker 只作「滚到哪个月」的锚点，不参与过滤。
     * 单独一个 Flow，MUST NOT 写回 _filter，否则 flatMapLatest 会重建 Pager
     * 把列表弹回顶部，定位就失去意义。
     */
    private val _anchorMonth = MutableStateFlow<Int?>(null)
    val anchorMonth: StateFlow<Int?> = _anchorMonth.asStateFlow()

    init {
        viewModelScope.launch { refreshMonthCounts(MonthRange.currentYearMonth() / 100) }
        viewModelScope.launch { _accounts.value = accountDao.all() }
        viewModelScope.launch { _categories.value = categoryDao.all() }
        viewModelScope.launch { _counterparties.value = recordDao.allCounterparties() }
        viewModelScope.launch { _filter.collect { f -> reload(f) } }
    }

    /** 筛选变化时只刷新合计，MUST NOT 把概览月份拉回筛选里的初始值。 */
    private suspend fun reload(f: RecordFilter) {
        refreshOverview(_overview.value.yearMonth, f.currencyCode)
        refreshMonthCounts(_overview.value.yearMonth / 100)
    }

    private suspend fun refreshOverview(yearMonth: Int, currencyCode: String?) {
        val cc = currencyCode
        val expense = if (cc == null) recordDao.totalExpense(yearMonth)
        else recordDao.totalExpenseForCurrency(yearMonth, cc)
        val income = if (cc == null) recordDao.totalIncome(yearMonth)
        else recordDao.totalIncomeForCurrency(yearMonth, cc)
        val daily = recordDao.dailyTotals(yearMonth, cc).associate {
            it.localDate to (it.expenseCents to it.incomeCents)
        }
        _overview.value = MonthOverview(
            yearMonth = yearMonth,
            expenseCents = expense,
            incomeCents = income,
            dailyTotals = daily,
            currencyCode = cc
        )
    }

    private suspend fun refreshMonthCounts(year: Int) {
        val cc = _filter.value.currencyCode
        _monthCounts.value = recordDao
            .monthCounts(year * 100 + 1, year * 100 + 12, cc)
            .associate { it.ym to it.cnt }
    }

    /** Picker 切年时按年重查角标，不为 12 个月各跑一次全表。 */
    fun loadMonthCounts(year: Int) {
        viewModelScope.launch { refreshMonthCounts(year) }
    }

    /**
     * 第 42 批：左右箭头与 Picker 都只是「滚到哪个月」，不再是过滤。
     * 不动 _filter → 筛选条件一条不丢、Pager 不重建、列表不回顶。
     */
    fun switchMonth(deltaMonths: Int) =
        locateMonth(MonthRange.shift(_overview.value.yearMonth, deltaMonths))

    /** YearMonthPicker 一步跳到任意年月，只定位不清条件。 */
    fun selectMonth(yearMonth: Int) = locateMonth(yearMonth)

    fun locateMonth(yearMonth: Int) {
        _anchorMonth.value = yearMonth
        viewModelScope.launch {
            refreshOverview(yearMonth, _filter.value.currencyCode)
            refreshMonthCounts(yearMonth / 100)
        }
    }

    fun consumeAnchor() {
        _anchorMonth.value = null
    }

    fun applyFilter(v: FilterValues) {
        _filter.value = _filter.value.copy(
            fromDate = v.fromDate,
            toDate = v.toDate,
            accountId = v.accountId,
            type = v.type,
            rootCategoryId = v.rootCategoryId,
            categoryId = v.categoryId,
            minAmountCents = v.minAmountCents,
            maxAmountCents = v.maxAmountCents,
            noteQuery = v.noteQuery,
            currencyCode = v.currencyCode,
            counterpartyQuery = v.counterpartyQuery,
            descriptionQuery = v.descriptionQuery,
            categoryDisplayName = v.categoryDisplayName,
            counterpartyDisplayName = v.counterpartyDisplayName,
            accountDisplayName = v.accountDisplayName,
            periodDisplayName = null,
            allMonths = v.allMonths,
            revision = _filter.value.revision + 1
        )
    }

    /** 第 26/27 批：统计页点分类带筛选跳明细；清掉其它条件，仅留分类与币种。
     *  第 27 批补齐两处：二级分类同时带上父级 rootCategoryId（否则筛选面板
     *  「二级」显示「先选一级」、看不到已选条件），并带只读展示名。 */
    fun filterByCategory(
        categoryId: Long,
        isRoot: Boolean,
        range: StatsDrillRange,
        currencyCode: String?,
        displayName: String? = null
    ) {
        val parentId = if (isRoot) null
        else _categories.value.firstOrNull { it.id == categoryId }?.parentId
        _filter.value = RecordFilter.forCategory(
            yearMonth = range.yearMonth,
            revision = _filter.value.revision + 1,
            rootCategoryId = if (isRoot) categoryId else parentId,
            categoryId = if (isRoot) null else categoryId,
            currencyCode = currencyCode,
            categoryDisplayName = displayName,
            fromDate = range.fromDate,
            toDate = range.toDate,
            periodDisplayName = range.label
        )
    }

    /** 第 27 批：排行「收款方」跳明细，交易对方是自由文本，用包含匹配。 */
    fun filterByCounterparty(name: String, range: StatsDrillRange, currencyCode: String?) {
        _filter.value = RecordFilter.forCounterparty(
            yearMonth = range.yearMonth,
            revision = _filter.value.revision + 1,
            counterpartyQuery = name,
            currencyCode = currencyCode,
            counterpartyDisplayName = name,
            fromDate = range.fromDate,
            toDate = range.toDate,
            periodDisplayName = range.label
        )
    }

    /** 第 27 批：排行「账户」跳明细，带账户 id 与只读展示名。 */
    fun filterByAccount(accountId: Long, name: String, range: StatsDrillRange, currencyCode: String?) {
        _filter.value = RecordFilter.forAccount(
            yearMonth = range.yearMonth,
            revision = _filter.value.revision + 1,
            accountId = accountId,
            currencyCode = currencyCode,
            accountDisplayName = name,
            fromDate = range.fromDate,
            toDate = range.toDate,
            periodDisplayName = range.label
        )
    }

    fun clearFilter() {
        _filter.value = _filter.value.copy(
            fromDate = null, toDate = null, accountId = null, type = null,
            rootCategoryId = null, categoryId = null,
            minAmountCents = null, maxAmountCents = null, noteQuery = null,
            currencyCode = null, counterpartyQuery = null, descriptionQuery = null,
            categoryDisplayName = null, counterpartyDisplayName = null,
            accountDisplayName = null, periodDisplayName = null, allMonths = true,
            revision = _filter.value.revision + 1
        )
    }

    /** 写操作成功后由外部调用，递增 revision 触发 flatMapLatest 重建 Pager。 */
    fun bumpRevision() {
        _filter.value = _filter.value.bumped()
        // 新记一笔可能带来新的交易对方，刷新筛选面板的联想候选。
        viewModelScope.launch { _counterparties.value = recordDao.allCounterparties() }
    }

    suspend fun delete(id: Long) {
        repository.deleteRecord(id)
        bumpRevision()
    }

    /** 剩余可报金额 = 原支出 − 累计已报销；非支出返回 0。 */
    suspend fun remainingSettlement(id: Long): Long {
        val original = recordDao.byId(id) ?: return 0L
        if (original.type != RecordType.EXPENSE) return 0L
        return (original.amountCents - recordDao.settlementTotalFor(id)).coerceAtLeast(0L)
    }

    /** 返回是否真的写入；超额或原记录非法时 Repository 返回 null。 */
    suspend fun settle(id: Long, amountCents: Long): Boolean {
        val newId = repository.recordSettlement(id, amountCents, System.currentTimeMillis())
        if (newId != null) bumpRevision()
        return newId != null
    }
}
