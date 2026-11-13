package com.zwz.offlineledger.data.repository

import androidx.room.withTransaction
import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.local.toDto
import com.zwz.offlineledger.data.local.toRecordEntity
import com.zwz.offlineledger.data.local.toEntity
import com.zwz.offlineledger.data.local.toRecord
import com.zwz.offlineledger.data.local.toTrash
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.data.model.LedgerAdjustment
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.backup.csv.CsvImportOutcome
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.engine.CategoryTree
import com.zwz.offlineledger.engine.LedgerEngine
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.engine.RecurringSchedule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class LedgerHealth(val checkedAccounts: Int, val repairedAccounts: Int, val driftCents: Long)

data class ReconcileResult(
    val deltaCents: Long,
    val bookCents: Long,
    val actualCents: Long,
    val reconciledAt: Long
)

/**
 * 账本唯一写入口。
 *
 * 所有写操作统一 db.withTransaction { 写入 -> rebuildAllBalancesInternal() }。
 * Room 的 @Transaction 只在 DAO 方法上生成事务代码，
 * 标在 Repository 普通方法上完全不生效，所以这里用 room-ktx 的 withTransaction。
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val db: AppDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao,
    private val adjustmentDao: BalanceAdjustmentDao,
    private val trashDao: TrashDao,
    private val templateDao: TemplateDao,
    private val ruleDao: RecurringRuleDao,
    private val budgetDao: BudgetDao
) {
    fun observeNetWorth(): Flow<Long> = accountDao.observeNetWorth()

    suspend fun insertRecord(record: RecordEntity): Long = db.withTransaction {
        val id = recordDao.insert(withResolvedRoot(record))
        rebuildAllBalancesInternal()
        id
    }

    suspend fun updateRecord(record: RecordEntity) = db.withTransaction {
        recordDao.update(withResolvedRoot(record))
        rebuildAllBalancesInternal()
    }

    /** 删除 = 从 records 移入 trash，然后重算。 */
    suspend fun deleteRecord(id: Long) = db.withTransaction {
        val record = recordDao.byId(id) ?: return@withTransaction
        trashDao.insert(record.toTrash(System.currentTimeMillis()))
        recordDao.deleteById(id)
        rebuildAllBalancesInternal()
    }

    /** 恢复 = 插回 records + 重算，与 insert 走同一条联动路径。 */
    suspend fun restoreRecord(originalId: Long) = db.withTransaction {
        val trash = trashDao.byOriginalId(originalId) ?: return@withTransaction
        recordDao.insert(trash.toRecord())
        trashDao.deleteByOriginalId(originalId)
        rebuildAllBalancesInternal()
    }

    suspend fun addAdjustment(a: BalanceAdjustmentEntity): Long = db.withTransaction {
        val id = adjustmentDao.insert(a)
        rebuildAllBalancesInternal()
        id
    }

    suspend fun revokeAdjustment(id: Long) = db.withTransaction {
        adjustmentDao.revoke(id)
        rebuildAllBalancesInternal()
    }

    /** 事务外也可显式调用；冷启动与导入后必跑。 */
    suspend fun rebuildAllBalances() = db.withTransaction {
        rebuildAllBalancesInternal()
    }

    /** 账内不一致属于缺陷，自动修复并写审计日志（阶段 5 落库）。 */
    suspend fun checkAndRepair(): LedgerHealth = db.withTransaction {
        val accounts = accountDao.all()
        val expected = expectedBalances()
        val drift = accounts.filter {
            it.balanceCents != (expected[it.id] ?: it.initialBalanceCents)
        }
        val driftCents = drift.sumOf {
            (expected[it.id] ?: it.initialBalanceCents) - it.balanceCents
        }
        if (drift.isNotEmpty()) {
            android.util.Log.w(
                "LedgerHealth",
                "检测到 ${drift.size} 个账户余额偏差，合计 $driftCents 分，已自动重算修复"
            )
            rebuildAllBalancesInternal()
        }
        LedgerHealth(accounts.size, drift.size, driftCents)
    }

    suspend fun exportSnapshot(): LedgerSnapshot = LedgerSnapshot(
        accounts = accountDao.all().map { it.toDto() },
        categories = categoryDao.all().map { it.toDto() },
        records = recordDao.all().map { it.toDto() },
        balanceAdjustments = adjustmentDao.all().map { it.toDto() },
        templates = templateDao.all().map { it.toDto() },
        recurringRules = ruleDao.all().map { it.toDto() },
        budgets = budgetDao.all().map { it.toDto() },
        trash = trashDao.all().map {
            com.zwz.offlineledger.backup.dto.BackupRecord(
                id = it.originalId, type = it.type.name, amountCents = it.amountCents,
                categoryId = it.categoryId, rootCategoryId = it.rootCategoryId,
                fromAccountId = it.fromAccountId, toAccountId = it.toAccountId,
                timestamp = it.timestamp, localDate = it.localDate,
                yearMonth = it.yearMonth, note = it.note,
                excludeFromStats = it.excludeFromStats,
                linkedRecordId = it.linkedRecordId,
                currencyCode = it.currencyCode,
                payee = it.payee
            )
        },
        exportedAtEpochMilli = System.currentTimeMillis(),
        currencyCode = LedgerCurrency.DEFAULT
    )

    /** 全量替换的覆盖导入：先清子表再清父表，事务末尾强制重算。 */
    suspend fun importBackup(file: BackupFile) = db.withTransaction {
        recordDao.clear()
        trashDao.clear()
        adjustmentDao.clear()
        templateDao.clear()
        ruleDao.clear()
        budgetDao.clear()
        categoryDao.clear()
        accountDao.clear()

        file.payload.accounts.forEach { accountDao.insert(it.toEntity()) }
        file.payload.categories.forEach { categoryDao.insert(it.toEntity()) }
        file.payload.records.forEach { recordDao.insert(it.toEntity()) }
        file.payload.balanceAdjustments.forEach { adjustmentDao.insert(it.toEntity()) }
        // v1 备份缺这两个键时已被解析成空列表，覆盖导入会清空现有模板 / 规则，
        // 与「全量替换」语义一致（见 backup-restore delta）。
        file.payload.templates.forEach { templateDao.insert(it.toEntity()) }
        file.payload.recurringRules.forEach { ruleDao.insert(it.toEntity()) }
        file.payload.budgets.forEach { budgetDao.insert(it.toEntity()) }
        file.payload.trash.forEach {
            trashDao.insert(it.toEntity().toTrash(System.currentTimeMillis()))
        }
        rebuildAllBalancesInternal()
    }


    // ---------------------------------------------------------------
    // 阶段 3：资产与对账的写操作。与阶段 1 的 9 个方法一样，
    // 全部包在 db.withTransaction 内；check_transaction_annotations.py 已同步扩清单。

    /**
     * 对账：生成独立校准 → 重算余额 → 推进锚点，三者同一事务。
     * 差额为 0 时不生成调整记录，但仍推进锚点（账实相符也是一次成功对账）。
     * 锚点只用于缩小核对范围，MUST NOT 参与余额计算。
     */
    suspend fun reconcile(
        accountId: Long,
        actualBalanceCents: Long,
        memo: String,
        source: String = "TAIL_DIFFERENCE",
        thresholdCents: Long = Reconciliation.TAIL_DIFFERENCE_THRESHOLD_CENTS
    ): ReconcileResult = db.withTransaction {
        val acc = accountDao.byId(accountId)
            ?: throw IllegalArgumentException("账户不存在：$accountId")
        require(memo.isNotBlank() || Reconciliation.isTailDifference(
            Reconciliation.difference(acc.balanceCents, actualBalanceCents),
            thresholdCents
        )) { "非尾差校准必须填写备注" }
        val delta = Reconciliation.difference(acc.balanceCents, actualBalanceCents)
        val now = System.currentTimeMillis()
        if (delta != 0L) {
            adjustmentDao.insert(
                BalanceAdjustmentEntity(
                    accountId = accountId,
                    deltaCents = delta,
                    memo = memo,
                    source = source,
                    createdAt = now
                )
            )
            rebuildAllBalancesInternal()
        }
        accountDao.updateReconciliationAnchor(accountId, now, actualBalanceCents)
        ReconcileResult(delta, acc.balanceCents, actualBalanceCents, now)
    }

    /** 新增账户后统一重算兜底，保证 balanceCents 与真相源一致。 */
    suspend fun createAccount(a: AccountEntity): Long = db.withTransaction {
        val id = accountDao.insert(a)
        rebuildAllBalancesInternal()
        id
    }

    /**
     * 阶段 7：账户编辑。期初余额是真相源之一，改完 MUST 重算余额缓存；
     * 币种只是标签，不改金额。
     */
    suspend fun updateAccountMeta(
        id: Long,
        name: String,
        includeInTotal: Boolean,
        creditLimitCents: Long?,
        sortOrder: Int,
        initialBalanceCents: Long,
        currencyCode: String
    ) = db.withTransaction {
        require(name.isNotBlank()) { "账户名不能为空" }
        accountDao.updateAccountMeta(
            id, name, includeInTotal, creditLimitCents, sortOrder,
            initialBalanceCents, currencyCode
        )
        rebuildAllBalancesInternal()
    }

    /** 阶段 7：显式批量统一币种。只改冗余标签，金额一分不动，事务末尾重算。 */
    suspend fun applyCurrencyToAll(code: String) = db.withTransaction {
        require(code in LedgerCurrency.SUPPORTED) { "不支持的币种：$code" }
        accountDao.updateAllCurrency(code)
        recordDao.updateAllCurrency(code)
        trashDao.updateAllCurrency(code)
        rebuildAllBalancesInternal()
    }

    /** 只归档不物理删；有流水的账户历史明细仍可查。 */
    suspend fun archiveAccount(id: Long) = db.withTransaction {
        accountDao.archiveAccount(id)
    }

    // ---------------------------------------------------------------
    // 阶段 5：分类管理。复用阶段 1 预留的 isArchived / sortOrder，
    // 零表结构变更、零 Migration。全部包在 withTransaction 内。

    /** 新增分类：最多两级，二级必须有父级且类型从父级继承。 */
    suspend fun createCategory(c: CategoryEntity): Long = db.withTransaction {
        validateCategory(c)
        categoryDao.insert(c)
    }

    suspend fun renameCategory(id: Long, name: String) = db.withTransaction {
        require(name.isNotBlank()) { "分类名不能为空" }
        val current = categoryDao.byId(id) ?: return@withTransaction
        categoryDao.update(current.copy(name = name.trim()))
    }

    /** 只归档不物理删；历史流水的 rootCategoryId 一个字不动。 */
    suspend fun archiveCategory(id: Long) = db.withTransaction {
        categoryDao.archive(id)
    }

    /** 批量排序，事务末尾重算兜底（排序不改账，但保持统一写路径）。 */
    suspend fun reorderCategories(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> categoryDao.updateSortOrder(id, index) }
        rebuildAllBalancesInternal()
    }


    // ---------------------------------------------------------------
    // 阶段 6a：模板 / 周期规则。共 9 个写方法，全部包在 db.withTransaction 内，
    // 已同步扩到 scripts/check_transaction_annotations.py 的 WRITE_METHODS。
    // 模板本身不改账，但统一走事务 + 末尾重算，保持「唯一写路径」的一致性；
    // 真正生成流水的两个方法与 insertRecord 完全同构。
    // ---------------------------------------------------------------

    suspend fun createTemplate(t: RecordTemplateEntity): Long = db.withTransaction {
        require(t.name.isNotBlank()) { "模板名不能为空" }
        require(t.amountCents > 0L) { "模板金额必须大于 0" }
        templateDao.insert(t)
    }

    suspend fun updateTemplate(t: RecordTemplateEntity) = db.withTransaction {
        require(t.name.isNotBlank()) { "模板名不能为空" }
        require(t.amountCents > 0L) { "模板金额必须大于 0" }
        templateDao.update(t)
    }

    /** 只归档不物理删；已由它生成的历史流水不受影响。 */
    suspend fun archiveTemplate(id: Long) = db.withTransaction {
        templateDao.archive(id)
    }

    suspend fun reorderTemplates(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> templateDao.updateSortOrder(id, index) }
        rebuildAllBalancesInternal()
    }

    suspend fun createRecurringRule(r: RecurringRuleEntity): Long = db.withTransaction {
        require(r.name.isNotBlank()) { "规则名不能为空" }
        require(r.amountCents > 0L) { "规则金额必须大于 0" }
        ruleDao.insert(r)
    }

    suspend fun updateRecurringRule(r: RecurringRuleEntity) = db.withTransaction {
        require(r.name.isNotBlank()) { "规则名不能为空" }
        require(r.amountCents > 0L) { "规则金额必须大于 0" }
        ruleDao.update(r)
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) = db.withTransaction {
        ruleDao.setEnabled(id, enabled)
    }

    /** 从模板记一笔：与 insertRecord 同一事务、同一 root 解析、同一重算。 */
    suspend fun recordFromTemplate(templateId: Long, atEpochMilli: Long): Long? = db.withTransaction {
        val t = templateDao.byId(templateId) ?: return@withTransaction null
        if (t.isArchived) return@withTransaction null
        val id = recordDao.insert(withResolvedRoot(t.toRecordEntity(atEpochMilli)))
        rebuildAllBalancesInternal()
        id
    }

    /**
     * 冷启动按日补记到期规则。
     * 单事务批量插入 + 末尾一次重算；lastGeneratedLocalDate 只在确实生成时推进，
     * 保证幂等且不因「未来 anchor」让规则永久失效。60 天 catch-up 上限见引擎。
     */
    suspend fun applyDueRules(todayLocalDate: Int): Int = db.withTransaction {
        var inserted = 0
        ruleDao.enabled().forEach { rule ->
            val dues = RecurringSchedule.dueDates(
                cadence = rule.cadence,
                dayOfMonth = rule.dayOfMonth,
                dayOfWeek = rule.dayOfWeek,
                anchorLocalDate = rule.anchorLocalDate,
                lastGeneratedLocalDate = rule.lastGeneratedLocalDate,
                todayLocalDate = todayLocalDate
            )
            dues.forEach { due ->
                val ts = RecurringSchedule.toEpochMilli(due)
                recordDao.insert(withResolvedRoot(rule.toRecordEntity(ts, due)))
                inserted++
            }
            if (dues.isNotEmpty()) {
                ruleDao.update(rule.copy(lastGeneratedLocalDate = todayLocalDate))
            }
        }
        if (inserted > 0) rebuildAllBalancesInternal()
        inserted
    }


    // ---------------------------------------------------------------
    // 阶段 6b：预算规则与退款/报销成对模型。
    // 4 个写方法全部包在 db.withTransaction 内；
    // scripts/check_transaction_annotations.py 的 WRITE_METHODS 已扩到 30。
    // ---------------------------------------------------------------

    suspend fun createBudget(b: BudgetEntity): Long = db.withTransaction {
        validateBudget(b)
        budgetDao.insert(b)
    }

    suspend fun updateBudget(b: BudgetEntity) = db.withTransaction {
        validateBudget(b)
        budgetDao.update(b)
    }

    /** 只归档不物理删；历史统计与备份不受影响。 */
    suspend fun archiveBudget(id: Long) = db.withTransaction {
        budgetDao.archive(id)
    }

    /**
     * 对一笔 EXPENSE 发起退款 / 报销，生成一条 SETTLEMENT 流水。
     * 与 insertRecord / recordFromTemplate 完全同构：
     * withResolvedRoot -> recordDao.insert -> rebuildAllBalancesInternal。
     * fromAccountId 留空、toAccountId 用原支出账户，
     * 于是既有余额聚合 SQL 会把钱加回该账户，一行都不用改。
     */
    suspend fun recordSettlement(
        originalId: Long,
        amountCents: Long,
        atEpochMilli: Long
    ): Long? = db.withTransaction {
        if (amountCents <= 0L) return@withTransaction null
        val original = recordDao.byId(originalId) ?: return@withTransaction null
        if (original.type != RecordType.EXPENSE) return@withTransaction null
        val already = recordDao.settlementTotalFor(originalId)
        if (already + amountCents > original.amountCents) return@withTransaction null
        val ld = LedgerClock.fromEpochMilli(atEpochMilli)
        val entity = RecordEntity(
            type = RecordType.SETTLEMENT,
            amountCents = amountCents,
            categoryId = original.categoryId,
            rootCategoryId = original.rootCategoryId,
            fromAccountId = null,
            toAccountId = original.fromAccountId,
            timestamp = atEpochMilli,
            localDate = ld.localDate,
            yearMonth = ld.yearMonth,
            note = if (original.note.isBlank()) "报销 / 退款" else "报销 / 退款：" + original.note,
            excludeFromStats = false,
            linkedRecordId = originalId,
            currencyCode = original.currencyCode,
            payee = original.payee
        )
        val id = recordDao.insert(withResolvedRoot(entity))
        rebuildAllBalancesInternal()
        id
    }

    private fun validateBudget(b: BudgetEntity) {
        require(b.name.isNotBlank()) { "预算名不能为空" }
        require(b.limitCents > 0L) { "预算限额必须大于 0" }
        when (b.scope) {
            BudgetScope.TOTAL -> require(b.categoryId == null) { "总预算不应指定分类" }
            BudgetScope.ROOT, BudgetScope.CATEGORY ->
                require(b.categoryId != null) { "分类预算必须指定分类" }
        }
    }

    private suspend fun validateCategory(c: CategoryEntity) {
        require(c.name.isNotBlank()) { "分类名不能为空" }
        require(c.level in 0..1) { "最多两级分类" }
        if (c.level == 0) {
            require(c.parentId == null) { "一级分类不能有父级" }
        } else {
            val parent = c.parentId?.let { categoryDao.byId(it) }
                ?: throw IllegalArgumentException("二级分类必须指定一级父分类")
            require(parent.level == 0) { "父级必须是一级分类" }
            require(parent.type == c.type) { "二级分类的收支类型须与父级一致" }
        }
    }

    // ---------------------------------------------------------------
    private suspend fun expectedBalances(): Map<Long, Long> {
        val deltas = recordDao.aggregateAccountDeltas()
            .associate { it.accountId to it.deltaCents }
        val adjustments = adjustmentDao.allActive().map {
            LedgerAdjustment(it.accountId, it.deltaCents, !it.isRevoked)
        }
        return LedgerEngine.apply(
            accountDao.all().associate { it.id to it.initialBalanceCents },
            deltas,
            adjustments
        )
    }

    /** 唯一余额写入路径；调用方必须已处在 withTransaction 内。 */
    private suspend fun rebuildAllBalancesInternal() {
        val balances = expectedBalances()
        accountDao.all().forEach {
            accountDao.updateBalance(it.id, balances[it.id] ?: it.initialBalanceCents)
        }
    }

    /** 两级分类解析：一级指向自身，二级指向父级；允许只选一级直接记账。 */
    /**
     * 所有写路径的统一归一化：解析一级分类 + 补齐币种 + 校验转账同币种。
     * insertRecord / updateRecord / recordFromTemplate / applyDueRules /
     * recordSettlement 全部经过这里，不新开写余额的分支。
     */

    /** 阶段 6c-2：导出全部未删除流水的 CSV 行（名称由 SQL JOIN 取，含币种）。 */
    suspend fun exportCsvRows(): List<CsvRow> = recordDao.csvRows().map { r ->
        val d = r.localDate.toString()
        val date = if (d.length == 8) {
            d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8)
        } else d
        CsvRow(
            date = date,
            type = r.type,
            amountYuan = java.math.BigDecimal.valueOf(r.amountCents, 2).toPlainString(),
            accountName = r.accountName,
            categoryRoot = r.rootName,
            category = r.categoryName,
            note = r.note,
            currencyCode = r.currencyCode,
            payee = r.payee
        )
    }

    /**
     * 阶段 6c-2：账单导入。单事务批量写入 + 末尾一次重算，
     * 任一行解析失败整体回滚；去重按「账户 + 金额 + 日期 + 备注」，不加字段。
     * 每条都经过 withResolvedRoot（含一级分类解析与 withCurrency 币种继承），
     * 与 insertRecord 完全同构，不新开写余额的分支。
     */
    suspend fun importRecords(rows: List<CsvRow>): CsvImportOutcome = db.withTransaction {
        val accounts = accountDao.all().filter { !it.isArchived }
        val categories = categoryDao.all()
        var written = 0
        var skipped = 0
        rows.forEach { row ->
            val account = accounts.firstOrNull { it.name == row.accountName }
                ?: throw IllegalArgumentException("账户不存在或已归档：" + row.accountName)
            val root = categories.firstOrNull {
                it.level == 0 && !it.isArchived && it.name == row.categoryRoot
            } ?: throw IllegalArgumentException("一级分类不存在：" + row.categoryRoot)
            val child = if (row.category.isBlank()) null else
                categories.firstOrNull {
                    it.level == 1 && !it.isArchived && it.parentId == root.id &&
                        it.name == row.category
                } ?: throw IllegalArgumentException(
                    "二级分类不存在于「" + row.categoryRoot + "」下：" + row.category
                )
            val ld = row.date.replace("-", "").toIntOrNull()
                ?: throw IllegalArgumentException("日期非法：" + row.date)
            val amountCents = Cents.fromYuan(row.amountYuan).value
            val type = RecordType.valueOf(row.type)
            if (recordDao.countDuplicate(account.id, amountCents, ld, row.note) > 0) {
                skipped++
                return@forEach
            }
            val entity = RecordEntity(
                type = type,
                amountCents = amountCents,
                categoryId = child?.id ?: root.id,
                rootCategoryId = root.id,
                fromAccountId = if (type == RecordType.EXPENSE) account.id else null,
                toAccountId = if (type == RecordType.INCOME) account.id else null,
                timestamp = RecurringSchedule.toEpochMilli(ld),
                localDate = ld,
                yearMonth = ld / 100,
                note = row.note,
                excludeFromStats = false,
                payee = row.payee
            )
            recordDao.insert(withResolvedRoot(entity))
            written++
        }
        rebuildAllBalancesInternal()
        CsvImportOutcome(written, skipped)
    }

    private suspend fun withResolvedRoot(record: RecordEntity): RecordEntity {
        val withRoot = if (record.categoryId == null) record else {
            val map: Map<Long, LedgerCategory> = categoryDao.all().associate {
                it.id to LedgerCategory(it.id, it.name, it.type, it.parentId, it.level)
            }
            val rootId = CategoryTree.resolveRootId(record.categoryId, map)
            record.copy(rootCategoryId = rootId)
        }
        return withCurrency(withRoot)
    }

    /**
     * 流水币种 = 关联账户币种，只是标签，不参与金额运算。
     * aggregateAccountDeltas() 与余额重算 MUST NOT 按币种过滤。
     */
    private suspend fun withCurrency(record: RecordEntity): RecordEntity {
        val from = record.fromAccountId?.let { accountDao.byId(it) }
        val to = record.toAccountId?.let { accountDao.byId(it) }
        if (record.type == RecordType.TRANSFER && from != null && to != null) {
            require(from.currencyCode == to.currencyCode) {
                "转账双方币种必须一致（" + from.currencyCode + " → " + to.currencyCode + "）"
            }
        }
        val cc = when (record.type) {
            RecordType.EXPENSE -> from?.currencyCode
            RecordType.INCOME, RecordType.SETTLEMENT, RecordType.TRANSFER -> to?.currencyCode
        } ?: LedgerCurrency.DEFAULT
        return record.copy(currencyCode = cc)
    }
}
