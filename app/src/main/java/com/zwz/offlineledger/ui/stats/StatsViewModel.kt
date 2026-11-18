package com.zwz.offlineledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.DrillRow
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.engine.BudgetEngine
import com.zwz.offlineledger.engine.BudgetProgress
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSlice
import com.zwz.offlineledger.engine.InsightData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StatsMode { MONTH, YEAR, MULTI_YEAR }
enum class SliceLevel { ROOT, CHILD }

/** 阶段 8：月度趋势默认单线，避免收支量级差大时贴底看不清。 */
enum class TrendMetric { EXPENSE, INCOME, BOTH, NET }

/** 一条预算规则的当月进度，纯展示数据。 */
data class BudgetItem(
    val id: Long,
    val name: String,
    val limitCents: Long,
    val progress: BudgetProgress
)

data class StatsUiState(
    val yearMonth: Int = MonthRange.currentYearMonth(),
    val year: Int = MonthRange.currentYearMonth() / 100,
    val mode: StatsMode = StatsMode.MONTH,
    val sliceLevel: SliceLevel = SliceLevel.ROOT,
    /** 阶段 6c-1：当前币种；默认必须选中一个具体币种，不提供「全部」混算选项。 */
    val currencyCode: String = LedgerCurrency.DEFAULT,
    val currencies: List<String> = listOf(LedgerCurrency.DEFAULT),
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
    val settlementCents: Long = 0L,
    val netExpenseCents: Long = 0L,
    val budgetItems: List<BudgetItem> = emptyList(),
    val rootSlices: List<ChartSlice> = emptyList(),
    val childSlices: List<ChartSlice> = emptyList(),
    val totalsCheck: ChartData.TotalsCheck? = null,
    val daily: List<ChartData.DailyPoint> = emptyList(),
    val monthly: List<ChartData.MonthPoint> = emptyList(),
    /** 第 23 批：近 5 年逐年汇总，MULTI_YEAR 视图用。 */
    val yearly: List<ChartData.YearPoint> = emptyList(),
    /** 阶段 8：年度逐月末资产余额，按当前币种、不混算。 */
    val assetTrend: List<Long> = emptyList(),
    val trendMetric: TrendMetric = TrendMetric.EXPENSE,
    val drillRootId: Long? = null,
    val drillRootName: String = "",
    val drillChildren: List<ChartSlice> = emptyList(),
    val drillCategoryId: Long? = null,
    val drillCategoryName: String = "",
    val drillRows: List<DrillRow> = emptyList(),
    /** 第 13 批：洞察总结卡与 Top5 排行。 */
    val recordCount: Int = 0,
    val averageCents: Long = 0L,
    val averageUnits: Int = 0,
    val averageLabel: String = "日均",
    val topCategories: List<InsightData.RankRow> = emptyList(),
    val topCounterparties: List<InsightData.RankRow> = emptyList(),
    val topAccounts: List<InsightData.RankRow> = emptyList(),
    /** 第 27 批：一级分类 -> 该级下二级排行，供排行卡内联展开，零新增 SQL。 */
    val rankChildren: Map<Long, List<InsightData.RankRow>> = emptyMap(),
    val loading: Boolean = false
)

/** 只读 ViewModel：6c-1 不新增任何写方法与事务，所有聚合只统计当前币种。 */
class StatsViewModel(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun setMode(mode: StatsMode) {
        _state.value = _state.value.copy(mode = mode, drillRootId = null)
        reload()
    }

    /** 第 23 批：多年视图点某年数据点，下钻回该年的年度视图。 */
    fun openYear(year: Int) {
        _state.value = _state.value.copy(
            year = year, mode = StatsMode.YEAR, drillRootId = null
        )
        reload()
    }

    fun setTrendMetric(metric: TrendMetric) {
        _state.value = _state.value.copy(trendMetric = metric)
    }

    fun setSliceLevel(level: SliceLevel) {
        _state.value = _state.value.copy(sliceLevel = level, drillRootId = null)
    }

    fun setCurrency(code: String) {
        if (code == _state.value.currencyCode) return
        _state.value = _state.value.copy(currencyCode = code, drillRootId = null)
        reload()
    }

    fun shiftMonth(delta: Int) {
        val ym = MonthRange.shift(_state.value.yearMonth, delta)
        _state.value = _state.value.copy(yearMonth = ym, year = ym / 100, drillRootId = null)
        reload()
    }

    fun shiftYear(delta: Int) {
        val y = _state.value.year + delta
        _state.value = _state.value.copy(year = y, drillRootId = null)
        reload()
    }

    fun openRoot(rootId: Long) {
        val s = _state.value
        val (from, to) = range(s)
        val cc = s.currencyCode
        viewModelScope.launch {
            val name = categoryName(rootId)
            val gross = recordDao.subcategoryTotalsUnderRootForCurrency(from, to, rootId, cc)
                .associate { it.categoryId to it.totalCents }
            val refund = recordDao.settlementByCategoryForCurrency(from, to, cc)
                .associate { it.categoryId to it.totalCents }
            val children = ChartData.netSlices(
                gross.map { (id, cents) -> ChartSlice(id, categoryName(id), null, cents) },
                refund
            )
            _state.value = _state.value.copy(
                drillRootId = rootId, drillRootName = name, drillChildren = children,
                drillCategoryId = null, drillRows = emptyList()
            )
        }
    }

    fun openCategory(categoryId: Long) {
        val s = _state.value
        val (from, to) = range(s)
        viewModelScope.launch {
            val name = categoryName(categoryId)
            val rows = recordDao.drillDownRowsForCurrency(from, to, categoryId, 100, s.currencyCode)
            _state.value = _state.value.copy(
                drillCategoryId = categoryId, drillCategoryName = name, drillRows = rows
            )
        }
    }

    fun backToChildren() {
        _state.value = _state.value.copy(drillCategoryId = null, drillRows = emptyList())
    }

    fun closeDrill() {
        _state.value = _state.value.copy(
            drillRootId = null, drillCategoryId = null,
            drillChildren = emptyList(), drillRows = emptyList()
        )
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)

            // 活跃账户的币种去重；若当前选中币种已不存在则回落到第一个。
            val currencies = accountDao.all().filter { !it.isArchived }
                .map { it.currencyCode }.distinct().sorted()
                .ifEmpty { listOf(LedgerCurrency.DEFAULT) }
            val cc = if (currencies.contains(_state.value.currencyCode))
                _state.value.currencyCode else currencies.first()
            val s = _state.value.copy(currencyCode = cc, currencies = currencies)
            val (from, to) = range(s)

            val categories = categoryDao.all().associateBy { it.id }
            val nameOf: (Long) -> String = { id -> categories[id]?.name ?: "未分类" }
            val colorOf: (Long) -> String? = { id -> categories[id]?.colorHex }

            val totalExpense: Long
            val totalIncome: Long
            val rootRows: List<Pair<Long, Long>>
            val childRows: List<Pair<Long, Long>>
            val yearTrend: List<com.zwz.offlineledger.data.local.dao.MonthTotalRow>
            if (s.mode == StatsMode.MONTH) {
                totalExpense = recordDao.totalExpenseForCurrency(s.yearMonth, cc)
                totalIncome = recordDao.totalIncomeForCurrency(s.yearMonth, cc)
                rootRows = recordDao.expenseByRootForCurrency(s.yearMonth, cc)
                    .map { r -> r.categoryId to r.totalCents }
                childRows = recordDao.expenseByCategoryForCurrency(s.yearMonth, cc)
                    .map { r -> r.categoryId to r.totalCents }
                yearTrend = emptyList()
            } else {
                yearTrend = recordDao.monthlyTrendForCurrency(from, to, cc)
                totalExpense = yearTrend.filter { it.type == "EXPENSE" }.sumOf { it.totalCents }
                totalIncome = yearTrend.filter { it.type == "INCOME" }.sumOf { it.totalCents }
                rootRows = recordDao.rootTotalsForCurrency(from, to, cc)
                    .map { r -> r.categoryId to r.totalCents }
                childRows = recordDao.categoryTotalsForCurrency(from, to, cc)
                    .map { r -> r.categoryId to r.totalCents }
            }

            val settleRoot = recordDao.settlementByRootForCurrency(from, to, cc)
                .associate { it.categoryId to it.totalCents }
            val settleChild = recordDao.settlementByCategoryForCurrency(from, to, cc)
                .associate { it.categoryId to it.totalCents }
            val settleTotal = recordDao.settlementTotalForCurrency(from, to, cc)
            val netTotal = (totalExpense - settleTotal).coerceAtLeast(0L)

            val roots = ChartData.netSlices(toSlices(rootRows, nameOf, colorOf), settleRoot)
            val children = ChartData.netSlices(toSlices(childRows, nameOf, colorOf), settleChild)
            val check = ChartData.checkTotals(roots, children, netTotal)

            // 第 27 批：排行卡「点一级展开二级」复用已有 childSlices 聚合，
            // 按 CategoryEntity.parentId 归组；不新增 DAO 查询、不改统计口径。
            val childrenByRoot = children.mapNotNull { sl ->
                val cid = sl.id ?: return@mapNotNull null
                val parent = categories[cid]?.parentId ?: return@mapNotNull null
                parent to InsightData.RankRow(
                    sl.name, sl.cents, 0,
                    InsightData.ratio(sl.cents, netTotal), cid
                )
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.sortedByDescending { it.cents } }

            // 预算消耗固定按「当前选中月 + 当前币种」单月聚合，不跨月、不跨币种累加。
            val ym = s.yearMonth
            val mSettleRoot = recordDao.settlementByRootForCurrency(ym, ym, cc)
                .associate { it.categoryId to it.totalCents }
            val mSettleChild = recordDao.settlementByCategoryForCurrency(ym, ym, cc)
                .associate { it.categoryId to it.totalCents }
            val mGrossRoot = recordDao.expenseByRootForCurrency(ym, cc)
                .associate { it.categoryId to it.totalCents }
            val mGrossChild = recordDao.expenseByCategoryForCurrency(ym, cc)
                .associate { it.categoryId to it.totalCents }
            val mNetTotal = (recordDao.totalExpenseForCurrency(ym, cc)
                - recordDao.settlementTotalForCurrency(ym, ym, cc)).coerceAtLeast(0L)
            val budgetItems = budgetDao.active().map { b ->
                val spent = when (b.scope) {
                    BudgetScope.TOTAL -> mNetTotal
                    BudgetScope.ROOT ->
                        ((mGrossRoot[b.categoryId] ?: 0L) - (mSettleRoot[b.categoryId] ?: 0L))
                            .coerceAtLeast(0L)
                    BudgetScope.CATEGORY ->
                        ((mGrossChild[b.categoryId] ?: 0L) - (mSettleChild[b.categoryId] ?: 0L))
                            .coerceAtLeast(0L)
                }
                BudgetItem(b.id, b.name, b.limitCents, BudgetEngine.progress(b.limitCents, spent))
            }

            val daily = if (s.mode == StatsMode.MONTH) {
                ChartData.fillDailySeries(
                    s.yearMonth,
                    recordDao.dailyTotalsForCurrency(s.yearMonth, cc).map {
                        ChartData.DailyPoint(
                            day = it.localDate % 100,
                            expenseCents = it.expenseCents,
                            incomeCents = it.incomeCents
                        )
                    }
                )
            } else emptyList()

            // 第 23 批：多年视图复用同一条 monthlyTrendForCurrency 只读聚合，
            // 在纯 JVM 层按年归并，不新增 SQL、不跨币种混算。
            val yearly = if (s.mode == StatsMode.MULTI_YEAR) {
                val rows = (s.year - 4..s.year).map { y ->
                    val inc = yearTrend.filter {
                        it.yearMonth / 100 == y && it.type == "INCOME"
                    }.sumOf { it.totalCents }
                    val exp = yearTrend.filter {
                        it.yearMonth / 100 == y && it.type == "EXPENSE"
                    }.sumOf { it.totalCents }
                    ChartData.YearPoint(y, inc, exp)
                }
                ChartData.fillYearlySeries(s.year, 5, rows)
            } else emptyList()

            val monthly = if (s.mode == StatsMode.YEAR) {
                ChartData.fillYearSeries(
                    s.year,
                    (1..12).map { m ->
                        val ymi = s.year * 100 + m
                        val inc = yearTrend.filter { it.yearMonth == ymi && it.type == "INCOME" }
                            .sumOf { it.totalCents }
                        val exp = yearTrend.filter { it.yearMonth == ymi && it.type == "EXPENSE" }
                            .sumOf { it.totalCents }
                        ChartData.MonthPoint(m, inc, exp)
                    }
                )
            } else emptyList()

            // 阶段 8：年度资产线 = 当前余额倒推年初基准 + 逐月净变动累计。
            // 校准记录不进资产线（量级极小且无币种归属），见 design.md。
            val assetTrend = if (s.mode == StatsMode.YEAR) {
                val current = accountDao.totalBalanceForCurrency(cc)
                val deltas = recordDao.monthlyBalanceDeltaForCurrency(from, to, cc)
                    .associate { (it.yearMonth % 100) to it.totalCents }
                ChartData.assetSeries(current - deltas.values.sum(), deltas)
            } else emptyList()

            // 第 13 批：洞察聚合，全部走只读 SQL，按当前币种，不加载流水实体。
            val count = recordDao.insightRecordCount(from, to, cc).toInt()
            val cpItems = recordDao.insightTopCounterparties(from, to, cc, 5)
                .map { InsightData.RankItem(it.name, it.totalCents, it.cnt) }
            val accNames = accountDao.all().associateBy { it.id }
            val accItems = recordDao.insightTopAccounts(from, to, cc, 5)
                .map { InsightData.RankItem(accNames[it.id]?.name ?: ("账户 " + it.id), it.totalCents, it.cnt, it.id) }
            val catItems = roots.map { InsightData.RankItem(it.name, it.cents, 0, it.id) }
            val units = when (s.mode) {
                StatsMode.MONTH ->
                    (MonthRange.of(s.yearMonth).second % 100).coerceAtLeast(1)
                StatsMode.YEAR -> 12
                StatsMode.MULTI_YEAR -> 5
            }
            val avgLabel = when (s.mode) {
                StatsMode.MONTH -> "日均"
                StatsMode.YEAR -> "月均"
                StatsMode.MULTI_YEAR -> "年均"
            }

            _state.value = s.copy(
                recordCount = count,
                averageCents = InsightData.averageCents(totalExpense, units),
                averageUnits = units,
                averageLabel = avgLabel,
                topCategories = InsightData.topRows(catItems, netTotal, 5),
                topCounterparties = InsightData.topRows(cpItems, totalExpense, 5),
                topAccounts = InsightData.topRows(accItems, totalExpense, 5),
                rankChildren = childrenByRoot,
                totalIncomeCents = totalIncome,
                totalExpenseCents = totalExpense,
                settlementCents = settleTotal,
                netExpenseCents = netTotal,
                budgetItems = budgetItems,
                rootSlices = roots,
                childSlices = children,
                totalsCheck = check,
                daily = daily,
                monthly = monthly,
                yearly = yearly,
                assetTrend = assetTrend,
                loading = false
            )
        }
    }

    private fun range(s: StatsUiState): Pair<Int, Int> = when (s.mode) {
        StatsMode.MONTH -> s.yearMonth to s.yearMonth
        StatsMode.YEAR -> (s.year * 100 + 1) to (s.year * 100 + 12)
        StatsMode.MULTI_YEAR -> ((s.year - 4) * 100 + 1) to (s.year * 100 + 12)
    }

    private suspend fun categoryName(id: Long): String =
        categoryDao.byId(id)?.name ?: "未分类"

    private fun toSlices(
        rows: List<Pair<Long, Long>>,
        nameOf: (Long) -> String = { "分类 $it" },
        colorOf: (Long) -> String? = { null }
    ): List<ChartSlice> = rows.map { (id, cents) ->
        ChartSlice(id, nameOf(id), colorOf(id), cents)
    }
}
