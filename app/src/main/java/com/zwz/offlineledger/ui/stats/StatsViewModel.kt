package com.zwz.offlineledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.DrillRow
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.engine.BudgetEngine
import com.zwz.offlineledger.engine.BudgetProgress
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StatsMode { MONTH, YEAR }
enum class SliceLevel { ROOT, CHILD }

/** 阶段 6b：一条预算规则的当月进度，纯展示数据。 */
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
    val totalIncomeCents: Long = 0L,
    /** 毛支出：概览保持原有口径，不因报销而改。 */
    val totalExpenseCents: Long = 0L,
    /** 阶段 6b：SETTLEMENT 合计与净支出。 */
    val settlementCents: Long = 0L,
    val netExpenseCents: Long = 0L,
    val budgetItems: List<BudgetItem> = emptyList(),
    /** 净支出切片：环形图与合计校验用。 */
    val rootSlices: List<ChartSlice> = emptyList(),
    val childSlices: List<ChartSlice> = emptyList(),
    val totalsCheck: ChartData.TotalsCheck? = null,
    val daily: List<ChartData.DailyPoint> = emptyList(),
    val monthly: List<ChartData.MonthPoint> = emptyList(),
    val drillRootId: Long? = null,
    val drillRootName: String = "",
    val drillChildren: List<ChartSlice> = emptyList(),
    val drillCategoryId: Long? = null,
    val drillCategoryName: String = "",
    val drillRows: List<DrillRow> = emptyList(),
    val loading: Boolean = false
)

/** 只读 ViewModel：阶段 6b 不新增任何写方法与事务。 */
class StatsViewModel(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun setMode(mode: StatsMode) {
        _state.value = _state.value.copy(mode = mode, drillRootId = null)
        reload()
    }

    fun setSliceLevel(level: SliceLevel) {
        _state.value = _state.value.copy(sliceLevel = level, drillRootId = null)
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
        viewModelScope.launch {
            val name = categoryName(rootId)
            val gross = recordDao.subcategoryTotalsUnderRoot(from, to, rootId)
                .associate { it.categoryId to it.totalCents }
            val refund = recordDao.settlementByCategoryInRange(from, to)
                .associate { it.categoryId to it.totalCents }
            val children = ChartData.netSlices(
                gross.map { (id, cents) -> ChartSlice(id, categoryName(id), null, cents) },
                refund
            )
            _state.value = _state.value.copy(
                drillRootId = rootId,
                drillRootName = name,
                drillChildren = children,
                drillCategoryId = null,
                drillRows = emptyList()
            )
        }
    }

    fun openCategory(categoryId: Long) {
        val s = _state.value
        val (from, to) = range(s)
        viewModelScope.launch {
            val name = categoryName(categoryId)
            val rows = recordDao.drillDownRows(from, to, categoryId, 100)
            _state.value = _state.value.copy(
                drillCategoryId = categoryId,
                drillCategoryName = name,
                drillRows = rows
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
            val s = _state.value
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
                totalExpense = recordDao.totalExpense(s.yearMonth)
                totalIncome = recordDao.totalIncome(s.yearMonth)
                rootRows = recordDao.expenseByRoot(s.yearMonth)
                    .map { r -> r.categoryId to r.totalCents }
                childRows = recordDao.expenseByCategory(s.yearMonth)
                    .map { r -> r.categoryId to r.totalCents }
                yearTrend = emptyList()
            } else {
                yearTrend = recordDao.monthlyTrend(from, to)
                totalExpense = yearTrend.filter { it.type == "EXPENSE" }.sumOf { it.totalCents }
                totalIncome = yearTrend.filter { it.type == "INCOME" }.sumOf { it.totalCents }
                rootRows = recordDao.rootTotalsInRange(from, to)
                    .map { r -> r.categoryId to r.totalCents }
                childRows = recordDao.categoryTotalsInRange(from, to)
                    .map { r -> r.categoryId to r.totalCents }
            }

            // 阶段 6b：净支出 = 毛支出 − SETTLEMENT，纯展示层减法。
            // 既有 6 条聚合 SQL 与 StatRules 常量一行未改。
            val settleRoot = recordDao.settlementByRootInRange(from, to)
                .associate { it.categoryId to it.totalCents }
            val settleChild = recordDao.settlementByCategoryInRange(from, to)
                .associate { it.categoryId to it.totalCents }
            val settleTotal = recordDao.settlementTotalInRange(from, to)
            val netTotal = (totalExpense - settleTotal).coerceAtLeast(0L)

            val roots = ChartData.netSlices(toSlices(rootRows, nameOf, colorOf), settleRoot)
            val children = ChartData.netSlices(toSlices(childRows, nameOf, colorOf), settleChild)
            val check = ChartData.checkTotals(roots, children, netTotal)

            // 预算消耗始终按「当前选中月」单月聚合：年度视图也不把 12 个月累加，
            // 否则与「每月限额」语义不符。全部来自 SQL 聚合，不用分页数据。
            val ym = s.yearMonth
            val mSettleRoot = recordDao.settlementByRootInRange(ym, ym)
                .associate { it.categoryId to it.totalCents }
            val mSettleChild = recordDao.settlementByCategoryInRange(ym, ym)
                .associate { it.categoryId to it.totalCents }
            val mGrossRoot = recordDao.expenseByRoot(ym)
                .associate { it.categoryId to it.totalCents }
            val mGrossChild = recordDao.expenseByCategory(ym)
                .associate { it.categoryId to it.totalCents }
            val mNetTotal = (recordDao.totalExpense(ym)
                - recordDao.settlementTotalInRange(ym, ym)).coerceAtLeast(0L)
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
                    recordDao.dailyTotals(s.yearMonth).map {
                        ChartData.DailyPoint(
                            day = it.localDate % 100,
                            expenseCents = it.expenseCents,
                            incomeCents = it.incomeCents
                        )
                    }
                )
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

            _state.value = _state.value.copy(
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
                loading = false
            )
        }
    }

    private fun range(s: StatsUiState): Pair<Int, Int> =
        if (s.mode == StatsMode.MONTH) s.yearMonth to s.yearMonth
        else (s.year * 100 + 1) to (s.year * 100 + 12)

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
