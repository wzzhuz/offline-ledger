package com.zwz.offlineledger.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.AccountQuickCandidates
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
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
import com.zwz.offlineledger.engine.ExtraAmountRules
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
    /** 第 43 批：描述独立输入，不并入备注。 */
    val description: String = "",
    /** 第 67 批：附加金额输入（元字符串）。支出/收入=优惠，转账=手续费。 */
    val extraExpr: String = "",
    /** 仅转账：true=转账优惠（落库为负 fee），false=手续费（正 fee）。 */
    val extraAsDiscount: Boolean = false,
    /** 第 75 批：一笔可挂多个标签，按名存储、保存时整体替换关联。 */
    val tagNames: List<String> = emptyList(),
    val excludeFromStats: Boolean = false,
    val loaded: Boolean = false
)

class RecordEditorViewModel(
    private val recordDao: RecordDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val repository: LedgerRepository,
    private val prefs: LedgerPrefs
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

    /**
     * 第 92 批修订：台账预填 MUST 在 load 的**同一个协程内**、构造完
     * EditorState 之后一次性合并，MUST NOT 由外部同步调用。
     *
     * 第 91 批的写法是「vm.load() 启动协程 → 外部同步 vm.applyPreset()」，
     * 但 load 内部有多个 DAO suspend，协程随后才执行
     * `_state.value = EditorState(...)` 整体重置，把标签与不计入全部覆盖掉。
     * 表现就是台账录入产出的流水不带 `台账:` 标签，台账视图查不到、
     * 主账本明细也不排除 →「只在主记录显示」。
     *
     * 第 94 批：分类优先按 presetCategoryId **精确命中**（只校验存在 + type 一致，
     * 不要求 enabled）——礼金模板指向清单里默认停用的「红包收入 / 礼金」，
     * 这样既不用逐笔手选分类，也不用改清单把停用一级塞进快捷栏。
     * 未指定或失效时才回退到「该 type 下第一个启用一级」。
     * 保存校验（save()）同样只查存在 + type 一致，停用分类可正常落库。
     * 仍不预填账户，避免依赖硬编码 id。
     */
    private fun applyPresetTo(
        base: EditorState,
        categories: List<CategoryEntity>,
        type: RecordType?,
        tagNames: List<String>,
        excludeFromStats: Boolean,
        presetCategoryId: Long? = null
    ): EditorState {
        if (tagNames.isEmpty()) return base
        val effectiveType = type ?: base.type
        val categoryId = presetCategoryId?.takeIf { id ->
            categories.any { it.id == id && it.type == effectiveType }
        } ?: categories.firstOrNull {
            it.level == 0 && it.type == effectiveType && it.enabled && !it.isArchived
        }?.id
        // 第 94 批：预填命中二级分类（9001 / 34004）时，展开态 MUST 指向其父一级，
        // 否则分类快捷栏不会展开到对应分组。
        val chosen = categories.firstOrNull { it.id == categoryId }
        val expandedRootId = chosen?.let {
            if (it.level == 0) it.id else it.parentId
        } ?: categoryId
        return base.copy(
            type = effectiveType,
            categoryId = categoryId,
            expandedRootId = expandedRootId,
            tagNames = tagNames.distinct().take(8),
            excludeFromStats = excludeFromStats
        )
    }

    /** 每次打开编辑器都调用一次，避免复用 ViewModel 时残留上一次的状态。 */
    fun load(
        recordId: Long?,
        presetType: RecordType? = null,
        presetTagNames: List<String> = emptyList(),
        presetExcludeFromStats: Boolean = false,
        presetCategoryId: Long? = null
    ) {
        viewModelScope.launch {
            // 第 60 批 Q4A：数据源改按 quickOrder，记一笔候选由「全部账户排序」页决定。
            val a = accountDao.allByQuickOrder()
            // 第 52 批：数据源改为全量，停用项不再「查不到」。过滤下沉到 UI 快捷栏，
            // 全量选择器仍可选停用项；统计 / 历史 / 导出一直读全量，不受影响。
            val c = categoryDao.all()
            _accounts.value = a
            _categories.value = c
            _counterparties.value = runCatching { recordDao.distinctCounterparties() }.getOrDefault(emptyList())
            // 第 60 批：30 天流水频次排序会盖掉使用者在独立页排好的常用顺序，
            // 故撤掉。主排序键改为 quickOrder（全部账户、不分类型），归档沉底。
            // 「常用」由使用者在「全部账户排序」页显式定义，不再由频次隐式推断。
            val sorted = a.sortedWith(
                compareBy<AccountEntity> { it.isArchived }
                    .thenBy { it.quickOrder }
                    .thenBy { it.id }
            )
            _accounts.value = sorted
            _counterpartySuggestions.value = emptyList()
            // 第 6 批：偏好默认账户优先，其次进程内记忆，最后按常用频次取第一个。
            val prefAcc = prefs.defaultAccountId.takeIf { id ->
                id > 0L && a.any { it.id == id && !it.isArchived }
            }
            val defaultAcc = prefAcc ?: lastAccountId ?: sorted.firstOrNull { !it.isArchived }?.id
            editingId = recordId
            if (recordId == null) {
                val base = EditorState(
                    type = RecordType.EXPENSE,
                    fromAccountId = defaultAcc,
                    toAccountId = defaultAcc,
                    // 默认一级 MUST 取启用且未归档的，避免新记一笔落到停用扩展项上。
                    categoryId = lastCategoryId
                        ?: c.firstOrNull {
                            it.level == 0 && it.type == RecordType.EXPENSE &&
                                it.enabled && !it.isArchived
                        }?.id,
                    timestamp = System.currentTimeMillis(),
                    loaded = true
                )
                // 第 92 批：预填与整体赋值在同一次赋值里完成，杜绝异步覆盖。
                _state.value = applyPresetTo(
                    base, c, presetType, presetTagNames,
                    presetExcludeFromStats, presetCategoryId
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
                        description = r.description,
                        // 第 67 批：编辑态回填附加金额；原价由 amount + discount 推导，
                        // 输入框只填「附加金额」本身，MUST NOT 读一个不存在的原价列。
                        extraExpr = when {
                            r.discountCents != null -> Cents(r.discountCents).toYuanString()
                            r.feeCents != null -> Cents(kotlin.math.abs(r.feeCents)).toYuanString()
                            else -> ""
                        },
                        extraAsDiscount = (r.feeCents ?: 0L) < 0L,
                        tagNames = repository.tagNames(recordId),
                        excludeFromStats = r.excludeFromStats,
                        loaded = true
                    )
                    // 第 45 批：编辑态把本记录的账户与交易对方置顶，只改顺序不改选中项。
                    _accounts.value = AccountQuickCandidates.promoteById(
                        sorted, listOfNotNull(r.fromAccountId, r.toAccountId)
                    ) { it.id }
                    _counterparties.value = AccountQuickCandidates.promoteText(
                        _counterparties.value, r.counterparty
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
            !it.isArchived && it.enabled && it.level == 0 && it.type == t
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
            toAccountId = s.toAccountId ?: defaultAcc,
            // 切换类型时附加金额语义会变（优惠 ↔ 手续费），清空避免串味。
            extraExpr = "",
            extraAsDiscount = false
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

    /**
     * 第 52 批：点一级 = 直接按一级记（覆盖之前可能选错的二级）。
     * 第一次点展开二级 chips 供可选细分；**再点同一级则收起二级栏**，
     * 替代原二次确认按钮，且不再需要任何额外点击。
     */
    fun selectRoot(rootId: Long) {
        val s = _state.value
        lastCategoryId = rootId
        _state.value = s.copy(
            categoryId = rootId,
            expandedRootId = if (s.expandedRootId == rootId) null else rootId
        )
    }

    /** 点二级 / 全量选择器命中：categoryId 指向二级，rootCategoryId 由保存时统一解析。 */
    fun selectCategory(id: Long, rootId: Long) {
        lastCategoryId = id
        _state.value = _state.value.copy(categoryId = id, expandedRootId = rootId)
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

    /** 第 43 批：描述独立于对方与备注，编辑态与列表主标题共用。 */
    /** 第 75 批：多选标签回填。 */
    fun setTags(names: List<String>) {
        _state.value = _state.value.copy(tagNames = names.distinct().take(8))
    }

    fun setDescription(v: String) {
        _state.value = _state.value.copy(description = v)
    }

    /** 第 67 批：附加金额（正数输入，单位元）；负数语义由 extraAsDiscount 表达。 */
    fun setExtra(v: String) {
        _state.value = _state.value.copy(extraExpr = v)
    }

    /** 仅转账：切换「手续费 / 转账优惠」。 */
    fun setExtraAsDiscount(v: Boolean) {
        _state.value = _state.value.copy(extraAsDiscount = v)
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
        // 第 6 批：模板全字段可选，未指定账户用「默认账户 / 常用首个」兜底，
        // 保证流水永远挂在账户上，余额联动与重算才有意义。
        var s = _state.value
        val fallback = prefs.defaultAccountId.takeIf { id ->
            id > 0L && _accounts.value.any { it.id == id && !it.isArchived }
        } ?: _accounts.value.firstOrNull { !it.isArchived }?.id
        if (fallback != null) {
            val alt = _accounts.value.firstOrNull { !it.isArchived && it.id != fallback }?.id
            s = s.copy(
                fromAccountId = s.fromAccountId ?: fallback,
                toAccountId = s.toAccountId
                    ?: (if (s.type == RecordType.TRANSFER) alt else fallback)
            )
            _state.value = s
        }
        // 第 44 批：编辑态引用的账户若已删除，MUST 显式拦截，
        // MUST NOT 静默兜底成常用账户（否则「什么都没改、直接保存」会改掉归属）。
        val knownIds = _accounts.value.map { it.id }.toSet()
        if (s.type == RecordType.EXPENSE || s.type == RecordType.TRANSFER) {
            s.fromAccountId?.let {
                if (it !in knownIds) return "原支出 / 转出账户已删除，请重新指定"
            }
        }
        if (s.type == RecordType.INCOME || s.type == RecordType.TRANSFER) {
            s.toAccountId?.let {
                if (it !in knownIds) return "原收入 / 转入账户已删除，请重新指定"
            }
        }
        // 纯数字输入直接转分；不再经过表达式求值器（真机反馈：不需要运算符）。
        val amountCents = try {
            Cents.fromYuan(s.amountExpr.trim()).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "金额格式非法"
        }
        if (amountCents <= 0L) return "金额必须大于 0"

        // 第 67 批：附加金额解析与唯一校验入口。amountCents 仍是实付真相源。
        val extraCents = if (s.extraExpr.isBlank()) null else try {
            Cents.fromYuan(s.extraExpr.trim()).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "附加金额格式非法"
        }
        val discountCents = if (s.type == RecordType.EXPENSE || s.type == RecordType.INCOME)
            extraCents else null
        val feeCents = if (s.type == RecordType.TRANSFER)
            extraCents?.let { if (s.extraAsDiscount) -it else it } else null
        val extraError = ExtraAmountRules.validate(
            s.type, amountCents, discountCents, feeCents
        )
        if (extraError != null) return extraError

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
            counterparty = s.counterparty.trim(),
            description = s.description.trim(),
            discountCents = discountCents,
            feeCents = feeCents
        )
        return try {
            if (editingId == null) {
                val newId = repository.insertRecord(entity)
                repository.setRecordTags(newId, s.tagNames)
            } else {
                repository.updateRecord(entity)
                repository.setRecordTags(editingId!!, s.tagNames)
            }
            null
        } catch (e: Exception) {
            e.message ?: "保存失败"
        }
    }
}
