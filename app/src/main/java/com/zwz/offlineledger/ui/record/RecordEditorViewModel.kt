package com.zwz.offlineledger.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.CategoryTree
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorState(
    val type: RecordType = RecordType.EXPENSE,
    val amountExpr: String = "",
    val categoryId: Long? = null,
    val expandedRootId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val counterparty: String = "",
    val excludeFromStats: Boolean = false,
    val loaded: Boolean = false
)

class RecordEditorViewModel(
    private val recordDao: RecordDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    /** 常用交易对方（空输入时快捷选）+ 输入联想结果。 */
    private val _counterparties = MutableStateFlow<List<String>>(emptyList())
    val counterparties: StateFlow<List<String>> = _counterparties.asStateFlow()
    private val _counterpartySuggestions = MutableStateFlow<List<String>>(emptyList())
    val counterpartySuggestions: StateFlow<List<String>> = _counterpartySuggestions.asStateFlow()

    /** 进程内记忆：下次打开直接预填，跨进程持久化留到阶段 5 偏好设置。 */
    private var lastAccountId: Long? = null
    private var lastCategoryId: Long? = null
    private var editingId: Long? = null

    /** 每次打开编辑器都调用一次，避免复用 ViewModel 时残留上一次的状态。 */
    fun load(recordId: Long?) {
        viewModelScope.launch {
            val a = accountDao.all()
            val c = categoryDao.all()
            _accounts.value = a
            _categories.value = c
            _counterparties.value = runCatching { recordDao.distinctCounterparties() }.getOrDefault(emptyList())
            // 阶段 8：常用账户置顶（最近 30 天流水频次），冷门账户仍可从「全部账户」弹层搜到。
            val since = LedgerClock.fromEpochMilli(System.currentTimeMillis() - 30L * 86_400_000L).localDate
            val rank = runCatching { recordDao.recentAccountUsage(since) }
                .getOrDefault(emptyList()).mapIndexed { i, r -> r.id to i }.toMap()
            val sorted = a.sortedBy { acc ->
                if (acc.isArchived) Int.MAX_VALUE else (rank[acc.id] ?: (1000 + acc.sortOrder))
            }
            _accounts.value = sorted
            _counterpartySuggestions.value = emptyList()
            val defaultAcc = lastAccountId ?: sorted.firstOrNull { !it.isArchived }?.id
            editingId = recordId
            if (recordId == null) {
                _state.value = EditorState(
                    type = RecordType.EXPENSE,
                    fromAccountId = defaultAcc,
                    toAccountId = defaultAcc,
                    categoryId = lastCategoryId
                        ?: c.firstOrNull { it.level == 0 && it.type == RecordType.EXPENSE }?.id,
                    timestamp = System.currentTimeMillis(),
                    loaded = true
                )
            } else {
                val r = recordDao.byId(recordId)
                if (r == null) {
                    _state.value = EditorState(loaded = true)
                } else {
                    _state.value = EditorState(
                        type = r.type,
                        amountExpr = Cents(r.amountCents).toYuanString(),
                        categoryId = r.categoryId,
                        expandedRootId = r.rootCategoryId,
                        fromAccountId = r.fromAccountId,
                        toAccountId = r.toAccountId,
                        timestamp = r.timestamp,
                        note = r.note,
                        counterparty = r.counterparty,
                        excludeFromStats = r.excludeFromStats,
                        loaded = true
                    )
                }
            }
        }
    }

    /**
     * 第三批：模板 = 预填草稿。选模板后把分类 / 账户 / 类型 / 备注 /
     * 交易对方 / 不计入收支自动带入，金额留空（模板填了就作默认值），
     * 日期时间用「现在」，之后每个字段都仍可自由改。
     */
    fun applyTemplate(t: RecordTemplateEntity) {
        val s = _state.value
        _state.value = s.copy(
            type = t.type,
            amountExpr = if (t.amountCents > 0L) Cents(t.amountCents).toYuanString() else "",
            categoryId = t.categoryId,
            expandedRootId = t.rootCategoryId,
            fromAccountId = t.fromAccountId ?: s.fromAccountId,
            toAccountId = t.toAccountId ?: s.toAccountId,
            note = t.note,
            counterparty = t.counterparty,
            excludeFromStats = t.excludeFromStats,
            timestamp = System.currentTimeMillis()
        )
    }

    /** 阶段 8：切换收支类型时 MUST 重置分类，禁止支出保留收入分类。 */
    fun setType(t: RecordType) {
        val s = _state.value
        val defaultAcc = lastAccountId ?: s.fromAccountId ?: s.toAccountId
        val visible = _categories.value.filter {
            !it.isArchived && it.level == 0 && it.type == t
        }.map { it.id }
        val current = s.categoryId?.let { id ->
            val c = _categories.value.firstOrNull { it.id == id }
            if (c != null && !c.isArchived && c.type == t) id else null
        }
        _state.value = s.copy(
            type = t,
            categoryId = if (t == RecordType.TRANSFER) null
            else (current ?: visible.firstOrNull()),
            expandedRootId = null,
            fromAccountId = s.fromAccountId ?: defaultAcc,
            toAccountId = s.toAccountId ?: defaultAcc
        )
    }

    /** 阶段 7：纯数字键盘，只追加数字 / 小数点 / 退格 / 清空，不再有表达式。 */
    fun onKey(key: String) {
        val cur = _state.value.amountExpr
        val next = when (key) {
            "C" -> ""
            "⌫" -> if (cur.isEmpty()) "" else cur.dropLast(1)
            else -> cur + key
        }
        _state.value = _state.value.copy(amountExpr = next)
    }

    /** 点一级：既直接可选中（低摩擦），又展开二级 chips（可选细分）。 */
    fun selectCategory(id: Long, rootId: Long) {
        lastCategoryId = id
        _state.value = _state.value.copy(categoryId = id, expandedRootId = rootId)
    }

    fun setExpandedRoot(id: Long?) {
        _state.value = _state.value.copy(expandedRootId = id)
    }

    fun setFromAccount(id: Long) {
        lastAccountId = id
        _state.value = _state.value.copy(fromAccountId = id)
    }

    fun setToAccount(id: Long) {
        lastAccountId = id
        _state.value = _state.value.copy(toAccountId = id)
    }

    fun shiftDay(days: Int) {
        _state.value = _state.value.copy(
            timestamp = _state.value.timestamp + days * 86_400_000L
        )
    }

    fun setToday() {
        _state.value = _state.value.copy(timestamp = System.currentTimeMillis())
    }

    fun setNote(v: String) {
        _state.value = _state.value.copy(note = v)
    }

    /** 阶段 8：键入一两个字即从全部历史交易对方模糊联想。 */
    fun setCounterparty(v: String) {
        _state.value = _state.value.copy(counterparty = v)
        viewModelScope.launch {
            _counterpartySuggestions.value = if (v.isBlank()) emptyList()
            else runCatching { recordDao.searchCounterparties(v.trim()) }
                .getOrDefault(emptyList())
        }
    }

    /** 阶段 8：非即时记账可改时分；保留已选日期。 */
    fun setTime(hour: Int, minute: Int) {
        val z = java.time.Instant.ofEpochMilli(_state.value.timestamp)
            .atZone(LedgerClock.zoneId)
        val ts = z.toLocalDate().atTime(hour, minute)
            .atZone(LedgerClock.zoneId).toInstant().toEpochMilli()
        _state.value = _state.value.copy(timestamp = ts)
    }

    /** 日历回传的是 UTC 午夜毫秒；换成同一本地日期、保留当前时分。 */
    fun setDate(utcMidnightMillis: Long) {
        val date = Instant.ofEpochMilli(utcMidnightMillis)
            .atZone(ZoneOffset.UTC).toLocalDate()
        val nowLocal = Instant.ofEpochMilli(_state.value.timestamp)
            .atZone(LedgerClock.zoneId)
        val ts = date.atTime(nowLocal.toLocalTime())
            .atZone(LedgerClock.zoneId).toInstant().toEpochMilli()
        _state.value = _state.value.copy(timestamp = ts)
    }

    fun setExclude(v: Boolean) {
        _state.value = _state.value.copy(excludeFromStats = v)
    }

    /** 返回 null 表示保存成功，否则返回可直接展示给用户的错误文案。 */
    suspend fun save(): String? {
        val s = _state.value
        // 纯数字输入直接转分；不再经过表达式求值器（真机反馈：不需要运算符）。
        val amountCents = try {
            Cents.fromYuan(s.amountExpr.trim()).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "金额格式非法"
        }
        if (amountCents <= 0L) return "金额必须大于 0"

        val accountError = when (s.type) {
            RecordType.EXPENSE -> if (s.fromAccountId == null) "请选择支出账户" else null
            RecordType.INCOME -> if (s.toAccountId == null) "请选择收入账户" else null
            RecordType.TRANSFER -> when {
                s.fromAccountId == null || s.toAccountId == null -> "请选择转出与转入账户"
                s.fromAccountId == s.toAccountId -> "转出与转入账户不能相同"
                else -> {
                    val fc = s.fromAccountId?.let { accountDao.byId(it) }?.currencyCode
                    val tc = s.toAccountId?.let { accountDao.byId(it) }?.currencyCode
                    if (fc != null && tc != null && fc != tc)
                        "转账双方币种必须一致（$fc → $tc）" else null
                }
            }
            // SETTLEMENT 只能从明细页对某笔支出发起，不走手工记一笔
            RecordType.SETTLEMENT -> "报销 / 退款请从明细页对原支出发起"
        }
        if (accountError != null) return accountError

        // 阶段 8：分类类型 MUST 与流水类型一致（模板下拉、分类网格、手工编辑共用此校验）。
        if (s.type != RecordType.TRANSFER && s.categoryId != null) {
            val chosen = categoryDao.all().firstOrNull { it.id == s.categoryId }
            if (chosen == null || chosen.type != s.type) {
                return "分类与收支类型不一致，请重新选择"
            }
        }
        val rootId = if (s.type == RecordType.TRANSFER) null
        else s.categoryId?.let { id ->
            val map: Map<Long, LedgerCategory> = categoryDao.all().associate {
                it.id to LedgerCategory(it.id, it.name, it.type, it.parentId, it.level)
            }
            try {
                CategoryTree.resolveRootId(id, map)
            } catch (e: IllegalArgumentException) {
                return e.message ?: "分类非法"
            }
        }

        val ld = LedgerClock.fromEpochMilli(s.timestamp)
        val entity = RecordEntity(
            id = editingId ?: 0L,
            type = s.type,
            amountCents = amountCents,
            categoryId = if (s.type == RecordType.TRANSFER) null else s.categoryId,
            rootCategoryId = rootId,
            fromAccountId = if (s.type == RecordType.INCOME) null else s.fromAccountId,
            toAccountId = if (s.type == RecordType.EXPENSE) null else s.toAccountId,
            timestamp = s.timestamp,
            localDate = ld.localDate,
            yearMonth = ld.yearMonth,
            note = s.note,
            excludeFromStats = s.excludeFromStats,
            counterparty = s.counterparty.trim()
        )
        return try {
            if (editingId == null) {
                repository.insertRecord(entity)
            } else {
                repository.updateRecord(entity)
            }
            null
        } catch (e: Exception) {
            e.message ?: "保存失败"
        }
    }
}
