package com.zwz.offlineledger.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.backup.BackupExporter
import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入前的确认计划：只有解析全通过才生成，进入此状态时不写任何数据。 */
data class CsvImportPlan(val rows: List<CsvRow>)

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
            _state.value = _state.value.copy(
                accounts = accountDao.all(),
                defaultAccountId = prefs.defaultAccountId
            )
        }
    }

    /** 第 6 批：默认账户只作记一笔与模板填充的兜底，不自动改已有流水。 */
    fun setDefaultAccountId(id: Long) {
        prefs.defaultAccountId = id
        _state.value = _state.value.copy(
            defaultAccountId = id,
            message = if (id == 0L) "已清除默认账户" else "默认账户已更新"
        )
    }

    fun refreshPrefs() {
        _state.value = _state.value.copy(
            thresholdYuan = yuan(prefs.reconcileThresholdCents),
            reverseColors = prefs.reverseColors,
            flagSecure = prefs.flagSecureEnabled,
            appLockEnabled = prefs.appLockEnabled,
            hasPin = prefs.hasPin(),
            defaultCurrency = prefs.defaultCurrency,
            hideAmounts = prefs.hideAmounts
        )
    }

    private fun yuan(cents: Long): String =
        java.math.BigDecimal.valueOf(cents, 2).toPlainString()

    fun message(text: String) {
        _state.value = _state.value.copy(message = text, error = "")
    }

    fun fail(text: String) {
        _state.value = _state.value.copy(error = text, message = "")
    }

    /** 只改默认值，绝不自动改已有账户与流水。 */
    /** 第 13 批：只改显示状态，不写数据、不进备份。 */
    fun setHideAmounts(hidden: Boolean) {
        prefs.hideAmounts = hidden
        com.zwz.offlineledger.ui.components.AmountMask.hidden = hidden
        _state.value = _state.value.copy(hideAmounts = hidden)
    }

    fun setDefaultCurrency(code: String) {
        require(code in LedgerCurrency.SUPPORTED) { "不支持的币种" }
        prefs.defaultCurrency = code
        _state.value = _state.value.copy(
            defaultCurrency = code,
            message = "默认币种已设为 $code，只影响之后新建的账户。"
        )
    }

    /** 显式批量统一：单事务改三张表标签并重算，金额一分不动。 */
    fun applyCurrencyToAll() {
        viewModelScope.launch {
            val code = prefs.defaultCurrency
            runCatching { repository.applyCurrencyToAll(code) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        message = "已把现有账户、流水与回收站的币种标签统一为 $code（金额未变）。",
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = "统一币种失败：" + (it.message ?: it.javaClass.simpleName)
                    )
                }
        }
    }

    // ---------------------------------------------------------------
    /** SAF 导出：JSON → Gzip → AES-GCM → 用户选择的 URI，全流式写出。 */
    fun exportTo(context: Context, uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "")
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
                        "请妥善保管口令：口令不落盘，遗忘后无法恢复。"
                }
                _state.value = _state.value.copy(busy = false, message = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "导出失败：" + (e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    /** SAF 导入：解密 → 解析 → 单事务全量替换。任一步失败都不写账本。 */
    fun importFrom(context: Context, uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "")
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
                loadCategories()
                _state.value = _state.value.copy(busy = false, message = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "导入失败，原账本未改动：" + (e.message ?: e.javaClass.simpleName)
                )
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
            _state.value = _state.value.copy(busy = true, message = "", error = "")
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
                _state.value = _state.value.copy(busy = false, message = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "导出失败：" + (e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    /** 先纯 JVM 全量解析；有错只报告，不进入写入事务。 */
    fun readCsvPlan(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "", csvPlan = null)
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("无法打开所选文件")
                    CsvCodec.parseImport(bytes)
                }
                if (!parsed.ok) {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = "账单文件有问题，账本未改动：\n" + parsed.errors.joinToString("\n")
                    )
                } else {
                    _state.value = _state.value.copy(
                        busy = false, csvPlan = CsvImportPlan(parsed.rows)
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "读取失败，账本未改动：" + (e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    fun cancelCsvImport() {
        _state.value = _state.value.copy(csvPlan = null)
    }

    /** 确认后单事务批量写入；任一步失败整体回滚（Repository 内保证）。 */
    fun confirmCsvImport() {
        val plan = _state.value.csvPlan ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "")
            try {
                val outcome = withContext(Dispatchers.IO) {
                    repository.importRecords(plan.rows)
                }
                _state.value = _state.value.copy(
                    busy = false, csvPlan = null,
                    message = "导入完成：写入 ${outcome.written} 条，" +
                        "跳过 ${outcome.skipped} 条重复；余额已重算。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false, csvPlan = null,
                    error = "导入失败，已整体回滚，账本未改动：" +
                        (e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    // ---------------------------------------------------------------
    fun setThresholdYuan(text: String) {
        _state.value = _state.value.copy(thresholdYuan = text)
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
                _state.value = _state.value.copy(healthText = text, message = text, error = "")
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
            _state.value = _state.value.copy(categories = all, categoryCounts = counts)
        }
    }

    /** 分类详情：带 LIMIT，MUST NOT 全量加载；一级能看到其下所有二级流水。 */
    fun openCategoryDetail(id: Long, name: String) {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                recordDao.insightCategoryDetailRows(id, 30)
            }
            _state.value = _state.value.copy(
                categoryDetailName = name, categoryDetail = rows
            )
        }
    }

    fun closeCategoryDetail() {
        _state.value = _state.value.copy(categoryDetailName = "", categoryDetail = emptyList())
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

    fun moveCategory(id: Long, delta: Int) {
        viewModelScope.launch {
            val group = _state.value.categories.filter { !it.isArchived }
            val idx = group.indexOfFirst { it.id == id }
            val target = idx + delta
            if (idx < 0 || target !in group.indices) return@launch
            val swapped = group.toMutableList().apply {
                val a = this[idx]; this[idx] = this[target]; this[target] = a
            }
            repository.reorderCategories(swapped.map { it.id })
            loadCategories()
        }
    }

    /** 供 UI 显示默认阈值，避免有人把偏好写成非法值后界面一片空白。 */
    val defaultThresholdCents: Long = Reconciliation.TAIL_DIFFERENCE_THRESHOLD_CENTS
}
