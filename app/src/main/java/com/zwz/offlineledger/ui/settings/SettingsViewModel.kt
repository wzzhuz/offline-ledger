package com.zwz.offlineledger.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.backup.BackupExporter
import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvFormat
import com.zwz.offlineledger.backup.csv.CsvParseResult
import com.zwz.offlineledger.backup.csv.CsvRebuildOutcome
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.csv.ImportCandidate
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.backup.csv.FireflyAccountChoice
import com.zwz.offlineledger.backup.csv.FireflyCategoryChoice
import com.zwz.offlineledger.backup.csv.FireflyCsvCodec
import com.zwz.offlineledger.backup.csv.FireflyImportOutcome
import com.zwz.offlineledger.backup.csv.FireflyMapping
import com.zwz.offlineledger.backup.csv.FireflyPlan
import com.zwz.offlineledger.backup.BackupImporter
import com.zwz.offlineledger.backup.crypto.SecureBackupCodec
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.DrillRow
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.InsightData
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入前的确认计划：只有解析全通过才生成，进入此状态时不写任何数据。 */
data class CsvImportPlan(
    val rows: List<CsvRow>,
    /** 第 23 批：疑似匹配候选与逐笔勾选（勾选＝跳过该行不导入）。 */
    val candidates: List<ImportCandidate> = emptyList(),
    val skipRowIndexes: Set<Int> = emptySet(),
    /** 第 34 批：重建模式里被明确跳过的行说明，结果摘要要展示。 */
    val notices: List<String> = emptyList(),
    val rebuild: Boolean = false
)

/** 第 22 批：Firefly III 导入预览状态，进入此状态时不写任何数据。 */
data class FireflyImportState(
    val plan: FireflyPlan,
    val accounts: List<FireflyAccountChoice>,
    val categories: List<FireflyCategoryChoice>,
    val overwriteOpening: Boolean = false
)

data class SettingsState(
    val message: String = "",
    val error: String = "",
    val busy: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val healthText: String = "",
    val thresholdYuan: String = "1.00",
    val reverseColors: Boolean = false,
    val flagSecure: Boolean = false,
    val appLockEnabled: Boolean = false,
    val hasPin: Boolean = false,
    val csvPlan: CsvImportPlan? = null,
    val firefly: FireflyImportState? = null,
    /** 第 23 批：导入完成后的结果摘要，独立于 message，页面内常驻可见。 */
    val fireflyResult: FireflyImportOutcome? = null,
    val defaultCurrency: String = LedgerCurrency.DEFAULT,
    val accounts: List<AccountEntity> = emptyList(),
    val defaultAccountId: Long = 0L,
    val hideAmounts: Boolean = false,
    /** 第 13 批：分类笔数（一级含二级）与分类详情下钻。 */
    val categoryCounts: Map<Long, Int> = emptyMap(),
    val categoryDetailName: String = "",
    val categoryDetail: List<DrillRow> = emptyList()
)

class SettingsViewModel(
    private val repository: LedgerRepository,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val recordDao: RecordDao,
    private val prefs: LedgerPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refreshPrefs()
        loadCategories()
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            _state.update { st -> st.copy(
                accounts = accountDao.all(),
                defaultAccountId = prefs.defaultAccountId
            ) }
        }
    }

    /** 第 6 批：默认账户只作记一笔与模板填充的兜底，不自动改已有流水。 */
    fun setDefaultAccountId(id: Long) {
        prefs.defaultAccountId = id
        _state.update { st -> st.copy(
            defaultAccountId = id,
            message = if (id == 0L) "已清除默认账户" else "默认账户已更新"
        ) }
    }

    fun refreshPrefs() {
        _state.update { st -> st.copy(
            thresholdYuan = yuan(prefs.reconcileThresholdCents),
            reverseColors = prefs.reverseColors,
            flagSecure = prefs.flagSecureEnabled,
            appLockEnabled = prefs.appLockEnabled,
            hasPin = prefs.hasPin(),
            defaultCurrency = prefs.defaultCurrency,
            hideAmounts = prefs.hideAmounts
        ) }
    }

    private fun yuan(cents: Long): String =
        java.math.BigDecimal.valueOf(cents, 2).toPlainString()

    fun message(text: String) {
        _state.update { st -> st.copy(message = text, error = "") }
    }

    fun fail(text: String) {
        _state.update { st -> st.copy(error = text, message = "") }
    }

    /** 只改默认值，绝不自动改已有账户与流水。 */
    /** 第 13 批：只改显示状态，不写数据、不进备份。 */
    fun setHideAmounts(hidden: Boolean) {
        prefs.hideAmounts = hidden
        com.zwz.offlineledger.ui.components.AmountMask.hidden = hidden
        _state.update { st -> st.copy(hideAmounts = hidden) }
    }

    fun setDefaultCurrency(code: String) {
        require(code in LedgerCurrency.SUPPORTED) { "不支持的币种" }
        prefs.defaultCurrency = code
        _state.update { st -> st.copy(
            defaultCurrency = code,
            message = "默认币种已设为 $code，只影响之后新建的账户。"
        ) }
    }

    /** 显式批量统一：单事务改三张表标签并重算，金额一分不动。 */
    fun applyCurrencyToAll() {
        viewModelScope.launch {
            val code = prefs.defaultCurrency
            runCatching { repository.applyCurrencyToAll(code) }
                .onSuccess {
                    _state.update { st -> st.copy(
                        message = "已把现有账户、流水与回收站的币种标签统一为 $code（金额未变）。",
                        error = ""
                    ) }
                }
                .onFailure {
                    _state.update { st -> st.copy(
                        error = "统一币种失败：" + (it.message ?: it.javaClass.simpleName)
                    ) }
                }
        }
    }

    // ---------------------------------------------------------------
    /** SAF 导出：JSON → Gzip → AES-GCM → 用户选择的 URI，全流式写出。 */
    fun exportTo(context: Context, uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val snapshot = repository.exportSnapshot()
                    val json = BackupExporter.encode(snapshot)
                    val blob = SecureBackupCodec.encode(json, passphrase)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(blob)
                        out.flush()
                    } ?: throw IllegalStateException("无法打开所选位置的输出流")
                    "已导出加密备份（${blob.size} 字节，${snapshot.records.size} 条流水）。" +
                        "文件始终由口令加密；若勾选「在本机记住口令」，" +
                        "口令仅经 Keystore 加密存于本机、卸载即失效，遗忘口令仍无法恢复。"
                }
                _state.update { st -> st.copy(busy = false, message = result) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "导出失败：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    /** SAF 导入：解密 → 解析 → 单事务全量替换。任一步失败都不写账本。 */
    fun importFrom(context: Context, uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val blob = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("无法打开所选文件")
                    val json = SecureBackupCodec.decode(blob, passphrase)
                    val file = BackupImporter.decode(json)
                    // 只有解析与版本校验都通过，才进入覆盖导入事务
                    repository.importBackup(file)
                    val health = repository.checkAndRepair()
                    "恢复完成：${file.payload.accounts.size} 个账户、" +
                        "${file.payload.categories.size} 个分类、" +
                        "${file.payload.records.size} 条流水；" +
                        "自检检查 ${health.checkedAccounts} 个账户，修复 ${health.repairedAccounts} 个。"
                }
                DataRefreshBus.notifyChanged()
                loadCategories()
                _state.update { st -> st.copy(busy = false, message = result) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "导入失败，原账本未改动：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }


    // ---------------------------------------------------------------
    // 阶段 6c-2：CSV 明文导出与账单导入。
    // 隐私边界是不上传第三方，不是文件必须加密；
    // 文件只写入用户通过 SAF 自选的位置，应用不申请任何权限。
    // ---------------------------------------------------------------
    fun exportCsvTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val rows = repository.exportCsvRows()
                    val bytes = CsvCodec.export(rows)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: throw IllegalStateException("无法打开所选位置的输出流")
                    "已导出 CSV 明文文件：${rows.size} 条流水。文件由你自行保管，应用不会上传任何数据。"
                }
                _state.update { st -> st.copy(busy = false, message = result) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "导出失败：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    /**
     * 先纯 JVM 全量解析；有错只报告，不进入写入事务。
     * 第 34 批：按表头嗅探分流。第三方账单走 parseImport；
     * 本 App 导出文件必须走「从 CSV 重建」（parseRebuild），
     * 直接投进「导入账单」会被行级校验拒绝，这里改成明确的引导文案。
     */
    fun readCsvPlan(context: Context, uri: Uri, rebuild: Boolean = false) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "", csvPlan = null) }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("无法打开所选文件")
                    if (!rebuild && CsvCodec.detectFormat(bytes) == CsvFormat.OWN) {
                        return@withContext CsvParseResult(
                            emptyList(),
                            listOf(
                                "这是本 App 导出的 CSV（11 列自有格式）。" +
                                    "「导入账单」用于支付宝 / 微信 / Firefly III 等第三方账单；" +
                                    "本文件请改用「从 CSV 重建」（需当前库已有同名账户与分类）；" +
                                    "清库 / 换机后的完整重建请用「从加密备份恢复」或「自动快照与恢复」。"
                            )
                        )
                    }
                    if (rebuild) CsvCodec.parseRebuild(bytes) else CsvCodec.parseImport(bytes)
                }
                if (!parsed.ok) {
                    _state.update { st -> st.copy(
                        busy = false,
                        error = "账单文件有问题，账本未改动：\n" + parsed.errors.joinToString("\n")
                    ) }
                } else {
                    val candidates = withContext(Dispatchers.IO) {
                        repository.findImportCandidates(parsed.rows)
                    }
                    _state.update { st -> st.copy(
                        busy = false,
                        csvPlan = CsvImportPlan(
                            rows = parsed.rows,
                            candidates = candidates,
                            skipRowIndexes = candidates.map { it.rowIndex }.toSet(),
                            notices = parsed.notices,
                            rebuild = rebuild
                        )
                    ) }
                }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "读取失败，账本未改动：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    fun cancelCsvImport() {
        _state.update { st -> st.copy(csvPlan = null) }
    }

    /** 第 23 批：逐笔切换「跳过该行 / 仍导入」，绝不自动合并或删除。 */
    fun toggleCsvCandidate(rowIndex: Int) {
        val plan = _state.value.csvPlan ?: return
        val next = plan.skipRowIndexes.toMutableSet()
        if (!next.add(rowIndex)) next.remove(rowIndex)
        _state.update { st -> st.copy(
            csvPlan = plan.copy(skipRowIndexes = next)
        ) }
    }

    /** 确认后单事务批量写入；任一步失败整体回滚（Repository 内保证）。 */
    fun confirmCsvImport() {
        val plan = _state.value.csvPlan ?: return
        val rows = plan.rows.filterIndexed { i, _ -> i !in plan.skipRowIndexes }
        val skippedByUser = plan.rows.size - rows.size
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                if (plan.rebuild) {
                    val outcome = withContext(Dispatchers.IO) {
                        repository.rebuildFromOwnCsv(rows)
                    }
                    DataRefreshBus.notifyChanged()
                    _state.update { st -> st.copy(
                        busy = false, csvPlan = null,
                        message = buildRebuildMessage(outcome, plan, skippedByUser)
                    ) }
                } else {
                    val outcome = withContext(Dispatchers.IO) {
                        repository.importRecords(rows)
                    }
                    DataRefreshBus.notifyChanged()
                    _state.update { st -> st.copy(
                        busy = false, csvPlan = null,
                        message = "导入完成：写入 ${outcome.written} 条、" +
                            "跳过 ${outcome.skipped} 条重复；疑似匹配 " +
                            plan.candidates.size + " 项，按你的勾选跳过 " +
                            skippedByUser + " 行；余额已重算。" +
                            (if (plan.notices.isEmpty()) ""
                             else " 格式跳过 " + plan.notices.size + " 行：" +
                                  plan.notices.take(3).joinToString("；") +
                                  (if (plan.notices.size > 3) " 等" else ""))
                    ) }
                }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false, csvPlan = null,
                    error = (if (plan.rebuild) "CSV 重建失败" else "导入失败") +
                        "，已整体回滚，账本未改动：" +
                        (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    /** 第 35 批：自有 CSV 重建的结果摘要，缺项逐项列出并给可操作的重建引导。 */
    private fun buildRebuildMessage(
        o: CsvRebuildOutcome,
        plan: CsvImportPlan,
        skippedByUser: Int
    ): String {
        val sb = StringBuilder()
        sb.append("CSV 重建完成：写入 ").append(o.written).append(" 条、重复跳过 ")
            .append(o.skippedDuplicate).append(" 条、缺账户/分类跳过 ")
            .append(o.skippedMissing).append(" 条；疑似匹配 ")
            .append(plan.candidates.size).append(" 项，按勾选跳过 ")
            .append(skippedByUser).append(" 行；余额已重算。")
        if (o.missingAccounts.isNotEmpty()) {
            sb.append(" 缺少账户：")
                .append(o.missingAccounts.take(6).joinToString("、"))
                .append(if (o.missingAccounts.size > 6) " 等" else "").append("。")
        }
        if (o.missingCategories.isNotEmpty()) {
            sb.append(" 缺少分类：")
                .append(o.missingCategories.take(6).joinToString("、"))
                .append(if (o.missingCategories.size > 6) " 等" else "").append("。")
        }
        if (plan.notices.isNotEmpty()) {
            sb.append(" 格式跳过 ").append(plan.notices.size).append(" 行：")
                .append(plan.notices.take(3).joinToString("；"))
                .append(if (plan.notices.size > 3) " 等" else "").append("。")
        }
        if (o.skippedMissing > 0) {
            sb.append(" 请先建立上述账户 / 分类再重建；分类名在 v0.2.14 换过清单，")
                .append("旧文件按名匹配不上会在这里列出，不是数据损坏。")
        }
        sb.append(" 清库 / 换机的完整重建（含转账、账户、模板、别名）请用")
            .append("「从加密备份恢复」或「自动快照与恢复」；二次重建按复合唯一键幂等。")
        return sb.toString()
    }

    // ---------------------------------------------------------------
    // 第 22 批：Firefly III 6.6.x 导出迁移。
    // 解析与计划生成都在纯 JVM 层；确认前零写入，确认后单事务。
    fun readFireflyPlan(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { st -> st.copy(
                busy = true, message = "", error = "", firefly = null
            ) }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("无法打开所选文件")
                    FireflyCsvCodec.parse(bytes)
                }
                if (!parsed.ok) {
                    _state.update { st -> st.copy(
                        busy = false,
                        error = "Firefly III 导出文件有问题，账本未改动：" +
                            parsed.errors.take(12).joinToString("；")
                    ) }
                    return@launch
                }
                val plan = withContext(Dispatchers.Default) {
                    FireflyCsvCodec.buildPlan(parsed)
                }
                val existingAccounts = withContext(Dispatchers.IO) {
                    accountDao.all().filter { !it.isArchived }
                }
                val existingCategories = withContext(Dispatchers.IO) {
                    categoryDao.all().filter { !it.isArchived }
                }
                val accountChoices = plan.accountNames.map { name ->
                    val hit = existingAccounts.firstOrNull { it.name == name }
                    FireflyAccountChoice(
                        sourceName = name,
                        targetName = hit?.name ?: "",
                        isNew = hit == null,
                        type = hit?.type?.name
                            ?: FireflyCsvCodec.inferAccountType(name)
                    )
                }
                val categoryChoices = plan.categoryKeys.map { ck ->
                    val rootHit = existingCategories.firstOrNull {
                        it.level == 0 && it.type.name == ck.type && it.name == ck.root
                    }
                    FireflyCategoryChoice(
                        key = ck.key(),
                        root = ck.root,
                        child = ck.child,
                        type = ck.type,
                        targetRoot = rootHit?.name ?: "",
                        skip = false
                    )
                }
                _state.update { st -> st.copy(
                    busy = false,
                    firefly = FireflyImportState(plan, accountChoices, categoryChoices)
                ) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "读取失败，账本未改动：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    fun updateFireflyAccount(choice: FireflyAccountChoice) {
        val cur = _state.value.firefly ?: return
        _state.update { st -> st.copy(
            firefly = cur.copy(
                accounts = cur.accounts.map {
                    if (it.sourceName == choice.sourceName) choice else it
                }
            )
        ) }
    }

    fun updateFireflyCategory(choice: FireflyCategoryChoice) {
        val cur = _state.value.firefly ?: return
        _state.update { st -> st.copy(
            firefly = cur.copy(
                categories = cur.categories.map {
                    if (it.key == choice.key) choice else it
                }
            )
        ) }
    }

    fun setFireflyOverwriteOpening(value: Boolean) {
        val cur = _state.value.firefly ?: return
        _state.update { st -> st.copy(firefly = cur.copy(overwriteOpening = value)) }
    }

    fun cancelFireflyImport() {
        _state.update { st -> st.copy(firefly = null) }
    }

    fun clearFireflyResult() {
        _state.update { st -> st.copy(fireflyResult = null) }
    }

    /**
     * 第 25 批：状态机单一出口。原实现先 launch loadCategories()/loadAccounts()，
     * 再用非原子的 `_state.value = _state.value.copy(...)` 写结果；并行 reload
     * 协程基于旧快照回写时会把 fireflyResult 覆盖回 null、busy 覆盖回 true，
     * 真机表现就是「只有正在导入动画」。现在全部状态写走 update（原子），
     * 结果只在成功分支赋一次，finally 只兜底复位 busy；失败同样出结果卡。
     */
    fun confirmFireflyImport() {
        val cur = _state.value.firefly ?: return
        val mapping = FireflyMapping(
            accounts = cur.accounts.associateBy { it.sourceName },
            categories = cur.categories.associateBy { it.key },
            overwriteOpeningForExisting = cur.overwriteOpening
        )
        val totalRows = cur.plan.recordCount + cur.plan.openingCount + cur.plan.skippedCount
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            _state.update {
                st -> st.copy(busy = true, message = "", error = "", fireflyResult = null)
            }
            try {
                val outcome = withContext(Dispatchers.IO) {
                    repository.importFirefly(cur.plan, mapping)
                }
                val result = outcome.copy(
                    success = true,
                    durationMs = System.currentTimeMillis() - started,
                    totalRows = totalRows
                )
                DataRefreshBus.notifyChanged()
                _state.update { st ->
                    st.copy(
                        busy = false, firefly = null, fireflyResult = result,
                        message = "Firefly III 迁移完成：写入 " + result.written +
                            " 条流水、跳过 " + result.skipped + " 条重复；余额已重算。"
                    )
                }
                loadCategories()
                loadAccounts()
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _state.update { st ->
                    st.copy(
                        busy = false,
                        fireflyResult = FireflyImportOutcome(
                            written = 0, skipped = 0, newAccounts = 0,
                            newCategories = 0, openingsApplied = 0, openingsSkipped = 0,
                            success = false, errorMessage = reason,
                            durationMs = System.currentTimeMillis() - started,
                            totalRows = totalRows
                        ),
                        error = "迁移失败，已整体回滚，账本未改动：" + reason
                    )
                }
            } finally {
                _state.update { st -> st.copy(busy = false) }
            }
        }
    }

    // ---------------------------------------------------------------
    fun setThresholdYuan(text: String) {
        _state.update { st -> st.copy(thresholdYuan = text) }
        val cents = try {
            com.zwz.offlineledger.core.money.Cents.fromYuan(text).value
        } catch (e: Exception) {
            null
        }
        if (cents != null && cents >= 0) prefs.reconcileThresholdCents = cents
    }

    fun setReverseColors(enabled: Boolean) {
        prefs.reverseColors = enabled
        LedgerColors.applyReverseColors(enabled)
        refreshPrefs()
    }

    fun setFlagSecure(enabled: Boolean) {
        prefs.flagSecureEnabled = enabled
        refreshPrefs()
        message("最近任务防泄露已" + (if (enabled) "开启" else "关闭") +
            "；重新进入应用后完全生效。")
    }

    fun setAppLock(enabled: Boolean) {
        if (enabled && !prefs.hasPin()) {
            fail("请先设置 PIN，否则生物识别不可用时无法进入账本")
            return
        }
        prefs.appLockEnabled = enabled
        refreshPrefs()
        message("应用锁已" + (if (enabled) "开启，下次冷启动 / 切回应用时生效" else "关闭"))
    }

    fun setPin(pin: String) {
        try {
            prefs.setPin(pin)
            refreshPrefs()
            message("PIN 已设置（加盐 hash 存储，不含明文）")
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "PIN 格式不合法")
        }
    }

    fun clearPin() {
        prefs.clearPin()
        refreshPrefs()
        message("PIN 已清除，应用锁同时关闭")
    }

    fun runHealthCheck() {
        viewModelScope.launch {
            try {
                val h = repository.checkAndRepair()
                val text = "账本自检：检查 ${h.checkedAccounts} 个账户，" +
                    "修复 ${h.repairedAccounts} 个，偏差合计 ${h.driftCents} 分。"
                _state.update { st -> st.copy(healthText = text, message = text, error = "") }
            } catch (e: Exception) {
                fail("自检失败：" + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    // ---------------------------------------------------------------
    fun loadCategories() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { categoryDao.all() }
            // 第 13 批：一条 GROUP BY 拿全部笔数，再在纯 JVM 层把二级累加进一级。
            val direct = withContext(Dispatchers.IO) {
                recordDao.insightCategoryCounts().associate { it.categoryId to it.cnt }
            }
            val parentOf = all.associate { it.id to it.parentId }
            val counts = InsightData.rollupCounts(direct, parentOf)
            _state.update { st -> st.copy(categories = all, categoryCounts = counts) }
        }
    }

    /** 分类详情：带 LIMIT，MUST NOT 全量加载；一级能看到其下所有二级流水。 */
    fun openCategoryDetail(id: Long, name: String) {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                recordDao.insightCategoryDetailRows(id, 30)
            }
            _state.update { st -> st.copy(
                categoryDetailName = name, categoryDetail = rows
            ) }
        }
    }

    fun closeCategoryDetail() {
        _state.update { st -> st.copy(categoryDetailName = "", categoryDetail = emptyList()) }
    }

    fun createCategory(name: String, type: RecordType, parentId: Long?) {
        viewModelScope.launch {
            try {
                val level = if (parentId == null) 0 else 1
                repository.createCategory(
                    CategoryEntity(
                        name = name.trim(), type = type, parentId = parentId, level = level
                    )
                )
                loadCategories()
                message("已新增分类「${name.trim()}」")
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "分类不合法")
            }
        }
    }

    fun renameCategory(id: Long, name: String) {
        viewModelScope.launch {
            try {
                repository.renameCategory(id, name)
                loadCategories()
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "改名失败")
            }
        }
    }

    fun archiveCategory(id: Long) {
        viewModelScope.launch {
            repository.archiveCategory(id)
            loadCategories()
            message("分类已归档；历史流水归属不变")
        }
    }

    /** 第三批：删除分类（有流水自动归档）与恢复归档分类。 */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(id)
                loadCategories()
                message("分类已处理：无流水则删除，有流水则归档，历史流水归属不变")
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "删除失败")
            }
        }
    }

    fun restoreCategory(id: Long) {
        viewModelScope.launch {
            repository.restoreCategory(id)
            loadCategories()
            message("分类已恢复")
        }
    }

    /** 第 30 批：启用/停用只改标志，不删行、不改历史；统计与导出仍读全量。 */
    fun setCategoryEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            categoryDao.setEnabled(id, enabled)
            loadCategories()
            message(if (enabled) "分类已启用，记一笔可选" else "分类已停用，历史仍计入统计")
        }
    }

    /** 排序作用域：支出/收入 + 一级/二级 + 所属父级。二级只在自己一级内排。 */
    private fun groupKey(c: CategoryEntity) =
        Triple<RecordType, Int, Long?>(c.type, c.level, c.parentId)

    /**
     * 第 15 批：修「排序点了没反应」的真 bug。
     * 原实现把全部未归档分类（一级+二级、支出+收入）当成一个数组交换相邻项，
     * UI 过滤出 roots / children 后常常什么都没变。
     * 现在按 (type, level, parentId) 分组：组内用新顺序，组间保持原 sortOrder 相对次序，
     * 拼成全量 id 列表一次性写入 0..n-1。DAO 与表结构一字不动。
     */
    private suspend fun persistWithGroup(
        newGroupIds: List<Long>,
        key: Triple<RecordType, Int, Long?>
    ) {
        val all = _state.value.categories
        val active = all.filter { !it.isArchived }.sortedBy { it.sortOrder }
        val rebuilt = mutableListOf<Long>()
        val seen = mutableSetOf<Triple<RecordType, Int, Long?>>()
        active.forEach { c ->
            val k = groupKey(c)
            if (k !in seen) {
                seen += k
                if (k == key) rebuilt.addAll(newGroupIds)
                else rebuilt.addAll(active.filter { groupKey(it) == k }.map { it.id })
            }
        }
        repository.reorderCategories(rebuilt + all.filter { it.isArchived }.map { it.id })
    }

    fun moveCategory(id: Long, delta: Int) {
        viewModelScope.launch {
            val cur = _state.value.categories.firstOrNull { it.id == id } ?: return@launch
            if (cur.isArchived) return@launch
            val key = groupKey(cur)
            val group = _state.value.categories
                .filter { !it.isArchived && groupKey(it) == key }
                .sortedBy { it.sortOrder }
            val idx = group.indexOfFirst { it.id == id }
            val target = idx + delta
            if (idx < 0 || target !in group.indices) return@launch
            val swapped = group.toMutableList().apply {
                val a = this[idx]; this[idx] = this[target]; this[target] = a
            }
            persistWithGroup(swapped.map { it.id }, key)
            loadCategories()
        }
    }

    /** 第 15 批：一级拖拽落库，只替换当前收支类型的一级组。 */
    fun commitRootOrder(orderedRootIds: List<Long>, type: RecordType) {
        viewModelScope.launch {
            val all = _state.value.categories
            val byId = all.associateBy { it.id }
            val key = Triple<RecordType, Int, Long?>(type, 0, null)
            val valid = orderedRootIds.mapNotNull { byId[it] }
                .filter { !it.isArchived && it.level == 0 && it.type == type }
                .map { it.id }
            val tail = all.filter { !it.isArchived && it.level == 0 && it.type == type }
                .map { it.id }.filter { it !in valid }
            persistWithGroup(valid + tail, key)
            loadCategories()
        }
    }

    /** 第 16 批：二级 chips 拖拽落库，只替换该父级下的二级组（作用域仍按三元组）。 */
    fun commitChildOrder(parentId: Long, orderedChildIds: List<Long>) {
        viewModelScope.launch {
            val all = _state.value.categories
            val parent = all.firstOrNull { it.id == parentId } ?: return@launch
            val byId = all.associateBy { it.id }
            val key = Triple<RecordType, Int, Long?>(parent.type, 1, parentId)
            val valid = orderedChildIds.mapNotNull { byId[it] }
                .filter { !it.isArchived && it.level == 1 && it.parentId == parentId }
                .map { it.id }
            val tail = all.filter { !it.isArchived && groupKey(it) == key }
                .map { it.id }.filter { it !in valid }
            persistWithGroup(valid + tail, key)
            loadCategories()
        }
    }

    /** 供 UI 显示默认阈值，避免有人把偏好写成非法值后界面一片空白。 */
    val defaultThresholdCents: Long = Reconciliation.TAIL_DIFFERENCE_THRESHOLD_CENTS
}
