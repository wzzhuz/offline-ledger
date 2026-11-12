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
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.Calculator
import com.zwz.offlineledger.engine.CategoryTree
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
            val defaultAcc = lastAccountId ?: a.firstOrNull { !it.isArchived }?.id
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
                        excludeFromStats = r.excludeFromStats,
                        loaded = true
                    )
                }
            }
        }
    }

    fun setType(t: RecordType) {
        val s = _state.value
        val defaultAcc = lastAccountId ?: s.fromAccountId ?: s.toAccountId
        _state.value = s.copy(
            type = t,
            categoryId = if (t == RecordType.TRANSFER) null else s.categoryId,
            expandedRootId = null,
            fromAccountId = s.fromAccountId ?: defaultAcc,
            toAccountId = s.toAccountId ?: defaultAcc
        )
    }

    fun onKey(key: String) {
        _state.value = _state.value.copy(
            amountExpr = if (key == "=") Calculator.simplify(_state.value.amountExpr)
            else Calculator.append(_state.value.amountExpr, key)
        )
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

    fun setExclude(v: Boolean) {
        _state.value = _state.value.copy(excludeFromStats = v)
    }

    /** 返回 null 表示保存成功，否则返回可直接展示给用户的错误文案。 */
    suspend fun save(): String? {
        val s = _state.value
        val amountCents = try {
            Cents.fromYuan(Calculator.evaluate(s.amountExpr).toPlainString()).value
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
                else -> null
            }
        }
        if (accountError != null) return accountError

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
            excludeFromStats = s.excludeFromStats
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
