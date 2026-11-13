package com.zwz.offlineledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.DrillRow
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StatsMode { MONTH, YEAR }
enum class SliceLevel { ROOT, CHILD }

data class StatsUiState(
    val yearMonth: Int = MonthRange.currentYearMonth(),
    val year: Int = MonthRange.currentYearMonth() / 100,
    val mode: StatsMode = StatsMode.MONTH,
    val sliceLevel: SliceLevel = SliceLevel.ROOT,
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
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

/** 只读 ViewModel：阶段 4 不新增任何写方法与事务。 */
class StatsViewModel(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        reload()
    }

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
            val children = toDrillSlices(
                recordDao.subcategoryTotalsUnderRoot(from, to, rootId)
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
            val nameOf: (Long) -> String = { id ->
                categories[id]?.name ?: "未分类"
            }
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

            val roots = toSlices(rootRows, nameOf, colorOf)
            val children = toSlices(childRows, nameOf, colorOf)
            val check = ChartData.checkTotals(roots, children, totalExpense)

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
                        val ym = s.year * 100 + m
                        val inc = yearTrend.filter { it.yearMonth == ym && it.type == "INCOME" }
                            .sumOf { it.totalCents }
                        val exp = yearTrend.filter { it.yearMonth == ym && it.type == "EXPENSE" }
                            .sumOf { it.totalCents }
                        ChartData.MonthPoint(m, inc, exp)
                    }
                )
            } else emptyList()

            _state.value = _state.value.copy(
                totalIncomeCents = totalIncome,
                totalExpenseCents = totalExpense,
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

    private suspend fun toDrillSlices(
        rows: List<com.zwz.offlineledger.data.local.dao.CategoryTotalRow>
    ): List<ChartSlice> = rows.map { r ->
        ChartSlice(r.categoryId, categoryName(r.categoryId), null, r.totalCents)
    }
}
