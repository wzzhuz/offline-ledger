package com.zwz.offlineledger.data.repository

import com.zwz.offlineledger.data.local.CounterpartyNormalizer
import androidx.room.withTransaction
import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.SeedData
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.CounterpartyAliasDao
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
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.data.model.LedgerAdjustment
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.engine.CategoryDeletionPolicy
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.backup.csv.CsvImportOutcome
import com.zwz.offlineledger.backup.csv.CsvRebuildOutcome
import com.zwz.offlineledger.backup.csv.CsvRebuildResolver
import com.zwz.offlineledger.backup.csv.ImportCandidate
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.csv.FireflyKind
import com.zwz.offlineledger.backup.csv.FireflyMapping
import com.zwz.offlineledger.backup.csv.FireflyPlan
import com.zwz.offlineledger.backup.csv.FireflyImportOutcome
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
    private val budgetDao: BudgetDao,
    private val aliasDao: CounterpartyAliasDao
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

    /**
     * 第 31 批阶段二：批量规整的单事务写入口。
     * 只改对方 / 备注 / 分类 / 不计入标记，金额与账户一分不动；
     * 分类改动仍经 withResolvedRoot 同步冗余的一级分类，末尾只重算一次。
     */
    suspend fun applyBulkTargets(
        targets: List<com.zwz.offlineledger.bulk.BulkTarget>
    ) = db.withTransaction {
        val byId = recordDao.all().associateBy { it.id }
        for (t in targets) {
            val entity = byId[t.id] ?: continue
            recordDao.update(withResolvedRoot(entity.copy(
                counterparty = t.counterparty,
                description = t.description,
                note = t.note,
                categoryId = t.categoryId,
                excludeFromStats = t.excludedFromTotals
            )))
        }
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
        counterpartyAliases = aliasDao.all().map {
            com.zwz.offlineledger.backup.dto.BackupAlias(
                pattern = it.pattern, shortName = it.shortName
            )
        },
        ruleGroups = db.ruleDao().groups().map { g ->
            com.zwz.offlineledger.backup.dto.BackupRuleGroup(
                id = g.id, name = g.name, enabled = g.enabled, sortOrder = g.sortOrder,
                definitions = db.ruleDao().definitions()
                    .filter { it.groupId == g.id }
                    .map { d -> com.zwz.offlineledger.backup.dto.BackupRule(
                        id = d.id, groupId = d.groupId, sortOrder = d.sortOrder,
                        definitionJson = d.definitionJson
                    ) }
            )
        },
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
                counterparty = it.counterparty,
                importSource = it.importSource,
                sourceId = it.sourceId
            )
        },
        exportedAtEpochMilli = System.currentTimeMillis(),
        currencyCode = LedgerCurrency.DEFAULT
    )

    /**
     * 全量替换的覆盖导入。
     *
     * 第 34 批修复 FOREIGN KEY constraint failed (code 1811)：
     * categories 自引用且 onDelete=RESTRICT，DELETE FROM categories 逐行删时
     * 先删父行会让子行 parentId 悬空；备份数组也不保证父级排在子级之前。
     * 事务内 PRAGMA defer_foreign_keys=ON 把约束检查推迟到提交；清空前
     * 先把 parentId 置空断开自引用；插入按父先子后的拓扑序；提交前
     * PRAGMA foreign_key_check 兜底。任一步抛错整个事务回滚，不留半截库。
     * 事务内写 PRAGMA foreign_keys=OFF 是 no-op，这里刻意不用。
     */
    suspend fun importBackup(file: BackupFile) = db.withTransaction {
        val handle = db.openHelper.writableDatabase
        handle.execSQL("PRAGMA defer_foreign_keys = ON")
        handle.execSQL("UPDATE categories SET parentId = NULL")

        recordDao.clear()
        trashDao.clear()
        adjustmentDao.clear()
        templateDao.clear()
        ruleDao.clear()
        budgetDao.clear()
        aliasDao.clear()
        db.ruleDao().clearDefinitions()
        db.ruleDao().clearGroups()
        categoryDao.clear()
        accountDao.clear()

        file.payload.accounts.forEach { accountDao.insert(it.toEntity()) }
        val orderedCategories = file.payload.categories
            .map { it.toEntity() }
            .sortedBy { if (it.parentId == null) 0 else 1 }
        orderedCategories.forEach { categoryDao.insert(it) }
        file.payload.records.forEach { recordDao.insert(it.toEntity()) }
        file.payload.balanceAdjustments.forEach { adjustmentDao.insert(it.toEntity()) }
        // v1 备份缺这两个键时已被解析成空列表，覆盖导入会清空现有模板 / 规则，
        // 与「全量替换」语义一致（见 backup-restore delta）。
        file.payload.templates.forEach { templateDao.insert(it.toEntity()) }
        file.payload.recurringRules.forEach { ruleDao.insert(it.toEntity()) }
        file.payload.budgets.forEach { budgetDao.insert(it.toEntity()) }
        file.payload.counterpartyAliases.forEach {
            aliasDao.insert(
                com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity(
                    pattern = it.pattern, shortName = it.shortName
                )
            )
        }
        file.payload.ruleGroups.forEach { g ->
            db.ruleDao().insertGroup(
                com.zwz.offlineledger.data.local.entity.RuleGroupEntity(
                    id = g.id, name = g.name, enabled = g.enabled, sortOrder = g.sortOrder
                )
            )
            g.definitions.forEach { d ->
                db.ruleDao().insertDefinition(
                    com.zwz.offlineledger.data.local.entity.RuleEntity(
                        id = d.id, groupId = d.groupId, sortOrder = d.sortOrder,
                        definitionJson = d.definitionJson
                    )
                )
            }
        }
        file.payload.trash.forEach {
            trashDao.insert(it.toEntity().toTrash(System.currentTimeMillis()))
        }

        val fkCursor = handle.query("PRAGMA foreign_key_check")
        try {
            if (fkCursor.count > 0) {
                throw IllegalStateException("恢复数据存在悬空外键，已整体回滚")
            }
        } finally {
            fkCursor.close()
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
        currencyCode: String,
        cardLast4: String?,
        cardKind: String?
    ) = db.withTransaction {
        require(name.isNotBlank()) { "账户名不能为空" }
        accountDao.updateAccountMeta(
            id, name, includeInTotal, creditLimitCents, sortOrder,
            initialBalanceCents, currencyCode, cardLast4, cardKind
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

    /** 第三批：账户类型选错可改；改完统一重算，净资产归类随新类型生效。 */
    suspend fun updateAccountType(id: Long, type: com.zwz.offlineledger.data.model.AccountType) =
        db.withTransaction {
            accountDao.updateType(id, type)
            rebuildAllBalancesInternal()
        }

    /** 第三批：归档 / 恢复同一个入口，恢复后资产页可重新看到。 */
    suspend fun setAccountArchived(id: Long, archived: Boolean) = db.withTransaction {
        accountDao.setArchived(id, archived)
        rebuildAllBalancesInternal()
    }

    /** 第三批：无流水的账户允许物理删除（重建）；有流水 MUST 走归档。 */
    suspend fun deleteAccountIfUnused(id: Long) = db.withTransaction {
        require(accountDao.linkedRecordCount(id) == 0) { "该账户已有流水，请改用归档" }
        accountDao.deleteById(id)
        rebuildAllBalancesInternal()
    }

    /** 第三批：账户自定义排序（分组内 sortOrder）。
     *  第 60 批：这句注释里的「同步到记一笔」已作废——记一笔改读 quickOrder。 */
    suspend fun reorderAccounts(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> accountDao.updateSortOrder(id, index) }
        rebuildAllBalancesInternal()
    }

    /** 第 60 批：记一笔优先顺序，一次性写入，MUST NOT 碰 sortOrder。 */
    suspend fun reorderQuickAccounts(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> accountDao.updateQuickOrder(id, index) }
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

    /**
     * 第 51 批：删除分类。
     * 有流水 MUST NOT 物理删（历史 rootCategoryId 会悬空）；
     * 预制分类即使无流水也 MUST NOT 物理删——onCreate 只种一次、无「恢复默认」入口，
     * 删掉后 restoreCategory 影响 0 行，等于不可逆。两类统一转归档。
     * AUTOINCREMENT 留下的 ID 空洞 MUST NOT 回填（见 CategoryDeletionPolicy）。
     */
    suspend fun deleteCategory(id: Long) = db.withTransaction {
        val archive = CategoryDeletionPolicy.shouldArchive(
            linkedRecordCount = categoryDao.linkedRecordCount(id),
            isPreset = SeedData.isPresetCategoryId(id)
        )
        if (archive) {
            categoryDao.archive(id)
        } else {
            categoryDao.deleteById(id)
        }
        rebuildAllBalancesInternal()
    }

    /** 第三批：恢复已归档分类，重新出现在分类管理与记账分类网格。 */
    suspend fun restoreCategory(id: Long) = db.withTransaction {
        categoryDao.setArchived(id, false)
        rebuildAllBalancesInternal()
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
        require(t.amountCents >= 0L) { "模板金额不能为负（可留空，记一笔时再填）" }
        templateDao.insert(t)
    }

    suspend fun updateTemplate(t: RecordTemplateEntity) = db.withTransaction {
        require(t.name.isNotBlank()) { "模板名不能为空" }
        require(t.amountCents >= 0L) { "模板金额不能为负（可留空，记一笔时再填）" }
        templateDao.update(t)
    }

    /** 只归档不物理删；已由它生成的历史流水不受影响。 */
    suspend fun archiveTemplate(id: Long) = db.withTransaction {
        templateDao.archive(id)
    }

    /** 第三批：模板可删除。模板只是预填草稿，不关联历史流水，直接物理删。 */
    suspend fun deleteTemplate(id: Long) = db.withTransaction {
        templateDao.deleteById(id)
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
        // 模板是预填草稿，金额可留空；未填金额的模板 MUST NOT 直接生成流水。
        if (t.amountCents <= 0L) return@withTransaction null
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
            counterparty = original.counterparty
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
            counterparty = r.counterparty,
            importSource = r.importSource,
            sourceId = r.sourceId,
            description = r.description
        )
    }

    /**
     * 第 23 批：跨来源对账的「疑似匹配」预览。只读、不写库、不自动合并。
     * 口径：金额分完全相同 + 同一账户 + localDate ±1 天；
     * 刻意不比较备注文本（商户全称与手工简写永远不相等）。
     * 返回候选由使用者逐笔决定「跳过 / 仍导入」，MUST NOT 静默丢弃。
     */
    suspend fun findImportCandidates(rows: List<CsvRow>): List<ImportCandidate> {
        val accounts = accountDao.all().filter { !it.isArchived }
        val out = mutableListOf<ImportCandidate>()
        rows.forEachIndexed { index, row ->
            val acc = accounts.firstOrNull { it.name == row.accountName }
                ?: return@forEachIndexed
            val cents = try {
                Cents.fromYuan(row.amountYuan).value
            } catch (e: IllegalArgumentException) {
                return@forEachIndexed
            }
            val ld = row.date.replace("-", "").toIntOrNull()
                ?: return@forEachIndexed
            val hits = recordDao.importCandidates(
                acc.id, cents, shiftLocalDate(ld, -1), shiftLocalDate(ld, 1)
            )
            hits.forEach { h ->
                out += ImportCandidate(
                    rowIndex = index, existingId = h.id,
                    existingNote = h.note, localDate = h.localDate,
                    amountCents = h.amountCents
                )
            }
        }
        return out
    }

    /** Int 型 YYYYMMDD 的跨月安全日期平移；纯 JVM，不碰设备时区。 */
    private fun shiftLocalDate(ld: Int, days: Long): Int {
        val s = ld.toString().padStart(8, '0')
        val d = java.time.LocalDate.of(
            s.substring(0, 4).toInt(), s.substring(4, 6).toInt(),
            s.substring(6, 8).toInt()
        ).plusDays(days)
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    /**
     * 阶段 6c-2：账单导入。单事务批量写入 + 末尾一次重算，
     * 任一行解析失败整体回滚。
     * 第 23 批：去重优先按 records(importSource, sourceId) 复合唯一键，
     * 旧 CSV 没带来源列时用「账户 + 金额 + 日期 + 备注」派生 sourceId，
     * 再叠加原有四元组判重；每条都经过 withResolvedRoot，不新开写余额分支。
     */
    suspend fun importRecords(rows: List<CsvRow>): CsvImportOutcome = db.withTransaction {
        val accounts = accountDao.all().filter { !it.isArchived }
        val categories = categoryDao.all()
        val aliases = aliasDao.all()
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
            val sourceId = row.sourceId.ifBlank { row.duplicateKey() }
            val importSource = row.importSource.ifBlank { "CSV" }
            val already = recordDao.countByImportSource(importSource, sourceId) > 0 ||
                recordDao.countDuplicate(account.id, amountCents, ld, row.note) > 0
            if (already) {
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
                description = row.description,
                excludeFromStats = false,
                counterparty = CounterpartyNormalizer.normalize(row.counterparty, aliases),
                importSource = importSource,
                sourceId = sourceId
            )
            recordDao.insert(withResolvedRoot(entity))
            written++
        }
        rebuildAllBalancesInternal()
        CsvImportOutcome(written, skipped)
    }

    /**
     * 第 35 批：本 App 自有导出 CSV 的回灌重建，与 importRecords 严格分开。
     *
     * 根因：importRecords 是「第三方账单」语义，要求当前库已有同名且未归档的
     * 账户 / 一级 / 二级分类，第一行匹配不上就抛
     * "账户不存在或已归档" 并整批回滚。但自有 11 列 CSV 不含账户表与分类树，
     * v0.2.14 起又删了预制账户、换了分类清单 → 清库/重装后必失败。
     *
     * 本路径语义：CSV 只负责「在已有体系上回灌 EXPENSE / INCOME 流水」。
     * 归档账户、停用分类不作为拒绝条件；缺项不抛异常，逐项收集到结果报告，
     * 受影响行跳过、其余照常写。完整重建（转账 / 账户 / 模板 / 别名 / 启用态）
     * 走「从加密备份恢复」或「自动快照与恢复」。
     *
     * 单事务 + 末尾重算 + 失败整库回滚，与 importBackup 同一套骨架；
     * importSource / sourceId 原样回写，二次重建按复合唯一键幂等。
     */
    suspend fun rebuildFromOwnCsv(rows: List<CsvRow>): CsvRebuildOutcome = db.withTransaction {
        // 含归档 / 停用：历史归属仍有效，不能因为「不再新选」就拒绝回灌。
        val accounts = accountDao.all()
        val categories = categoryDao.all()
        val aliases = aliasDao.all()
        val rootsById = categories.filter { it.level == 0 }.associateBy { it.id }
        val categoryPaths = linkedSetOf<String>()
        categories.forEach { c ->
            if (c.level == 0) {
                categoryPaths += c.name
            } else {
                val parent = rootsById[c.parentId]?.name
                if (parent != null) categoryPaths += parent + "/" + c.name
            }
        }
        val resolution = CsvRebuildResolver.resolve(
            rows, accounts.map { it.name }.toSet(), categoryPaths
        )
        var written = 0
        var skippedDuplicate = 0
        resolution.ready.forEach { row ->
            val account = accounts.first { it.name == row.accountName }
            val root = categories.first { it.level == 0 && it.name == row.categoryRoot }
            val child = if (row.category.isBlank()) null else categories.firstOrNull { c ->
                c.level == 1 && c.parentId == root.id && c.name == row.category
            }
            val ld = row.date.replace("-", "").toIntOrNull()
                ?: throw IllegalArgumentException("日期非法：" + row.date)
            val amountCents = Cents.fromYuan(row.amountYuan).value
            val type = RecordType.valueOf(row.type)
            val sourceId = row.sourceId.ifBlank { row.duplicateKey() }
            val importSource = row.importSource.ifBlank { "CSV" }
            val already = recordDao.countByImportSource(importSource, sourceId) > 0 ||
                recordDao.countDuplicate(account.id, amountCents, ld, row.note) > 0
            if (already) {
                skippedDuplicate++
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
                description = row.description,
                excludeFromStats = false,
                counterparty = CounterpartyNormalizer.normalize(row.counterparty, aliases),
                importSource = importSource,
                sourceId = sourceId
            )
            recordDao.insert(withResolvedRoot(entity))
            written++
        }
        rebuildAllBalancesInternal()
        CsvRebuildOutcome(
            written, skippedDuplicate, resolution.skippedMissing,
            resolution.missingAccounts, resolution.missingCategories
        )
    }

    /**
     * 第 22 批：Firefly III 6.6.x transactions.csv 迁移。
     * 单事务：按需新建账户 / 两级分类 -> 批量写流水 -> 末尾重算余额。
     * 第 23 批：幂等改由 records(importSource, sourceId) 复合唯一索引保证，
     * 不再依赖备注前缀；备注里的 ff3:<journal_id> 仅作人类可读线索。
     * 映射到已有账户时默认不覆盖其期初余额，需显式授权才改。
     */
    suspend fun importFirefly(
        plan: FireflyPlan,
        mapping: FireflyMapping
    ): FireflyImportOutcome = db.withTransaction {
        val existingAccounts = accountDao.all()
        val existingCategories = categoryDao.all()
        val aliases = aliasDao.all()
        val accountIds = HashMap<String, Long>()
        var newAccounts = 0
        var openingsApplied = 0
        var openingsSkipped = 0

        val openingBySource = HashMap<String, Long>()
        plan.entries.filter { it.kind == FireflyKind.OPENING }.forEach { e ->
            val cents = Cents.fromYuan(e.amountYuan).value
            openingBySource[e.accountName] = (openingBySource[e.accountName] ?: 0L) + cents
        }

        plan.accountNames.forEach { name ->
            val choice = mapping.accounts[name]
            val targetName = choice?.targetName.orEmpty()
            if (targetName.isNotBlank()) {
                val hit = existingAccounts.firstOrNull { it.name == targetName }
                    ?: throw IllegalArgumentException("账户映射目标不存在：" + targetName)
                accountIds[name] = hit.id
                val opening = openingBySource[name]
                if (opening != null) {
                    if (mapping.overwriteOpeningForExisting && targetName == name) {
                        accountDao.update(hit.copy(initialBalanceCents = opening))
                        openingsApplied++
                    } else {
                        openingsSkipped++
                    }
                }
            } else {
                val type = try {
                    AccountType.valueOf(choice?.type ?: "ASSET")
                } catch (e: IllegalArgumentException) {
                    AccountType.ASSET
                }
                val opening = openingBySource[name] ?: 0L
                val id = accountDao.insert(
                    AccountEntity(
                        name = name,
                        type = type,
                        initialBalanceCents = opening,
                        currencyCode = "CNY",
                        includeInTotal = true,
                        sortOrder = existingAccounts.size + newAccounts
                    )
                )
                accountIds[name] = id
                newAccounts++
                if (openingBySource.containsKey(name)) openingsApplied++
            }
        }

        val categoryIds = HashMap<String, Pair<Long, Long?>>()
        val createdRoots = HashMap<String, CategoryEntity>()
        val createdChildren = HashMap<String, Long>()
        var newCategories = 0
        plan.categoryKeys.forEach { ck ->
            val choice = mapping.categories[ck.key()]
            if (choice != null && choice.skip) return@forEach
            val type = RecordType.valueOf(ck.type)
            val rootName = choice?.targetRoot?.takeIf { it.isNotBlank() } ?: ck.root
            val rootKey = ck.type + "|" + rootName
            val resolvedRoot: CategoryEntity =
                existingCategories.firstOrNull {
                    it.level == 0 && !it.isArchived && it.type == type && it.name == rootName
                } ?: createdRoots[rootKey] ?: run {
                    val id = categoryDao.insert(
                        CategoryEntity(
                            name = rootName, type = type, level = 0, parentId = null,
                            sortOrder = existingCategories.size + newCategories
                        )
                    )
                    val created = CategoryEntity(
                        id = id, name = rootName, type = type, level = 0
                    )
                    createdRoots[rootKey] = created
                    newCategories++
                    created
                }
            var childId: Long? = null
            if (ck.child.isNotBlank()) {
                val childKey = rootKey + "|" + ck.child
                val hit = existingCategories.firstOrNull {
                    it.level == 1 && !it.isArchived && it.type == type &&
                        it.parentId == resolvedRoot.id && it.name == ck.child
                }
                childId = hit?.id ?: createdChildren[childKey] ?: run {
                    val id = categoryDao.insert(
                        CategoryEntity(
                            name = ck.child, type = type, level = 1,
                            parentId = resolvedRoot.id,
                            sortOrder = existingCategories.size + newCategories
                        )
                    )
                    createdChildren[childKey] = id
                    newCategories++
                    id
                }
            }
            categoryIds[ck.key()] = Pair(resolvedRoot.id, childId)
        }

        var written = 0
        var skipped = 0
        plan.entries.forEach { e ->
            if (e.kind == FireflyKind.OPENING || e.kind == FireflyKind.SKIPPED) {
                return@forEach
            }
            val accId = accountIds[e.accountName]
                ?: throw IllegalArgumentException("账户未映射：" + e.accountName)
            val toId = e.toAccountName?.let { accountIds[it] }
            if (e.kind == FireflyKind.TRANSFER && toId == null) {
                throw IllegalArgumentException("转账转入账户未映射：" + (e.toAccountName ?: ""))
            }
            val amountCents = Cents.fromYuan(e.amountYuan).value
            if (amountCents <= 0L) {
                throw IllegalArgumentException("第 " + e.lineNo + " 行金额必须大于 0")
            }
            if (recordDao.countByImportSource("FIREFLY_III", e.journalId) > 0) {
                skipped++
                return@forEach
            }
            val pair = categoryIds[e.categoryKey]
            val type = when (e.kind) {
                FireflyKind.EXPENSE -> RecordType.EXPENSE
                FireflyKind.INCOME -> RecordType.INCOME
                FireflyKind.TRANSFER -> RecordType.TRANSFER
                else -> throw IllegalArgumentException("不支持的迁移类型")
            }
            val entity = RecordEntity(
                type = type,
                amountCents = amountCents,
                categoryId = pair?.second ?: pair?.first,
                rootCategoryId = pair?.first,
                fromAccountId = if (type == RecordType.EXPENSE || type == RecordType.TRANSFER) {
                    accId
                } else null,
                toAccountId = if (type == RecordType.INCOME) accId else toId,
                timestamp = e.epochMilli,
                localDate = e.localDate,
                yearMonth = e.yearMonth,
                note = e.note,
                description = e.description,
                excludeFromStats = false,
                counterparty = CounterpartyNormalizer.normalize(e.counterparty, aliases),
                importSource = "FIREFLY_III",
                sourceId = e.journalId
            )
            recordDao.insert(withResolvedRoot(entity))
            written++
        }
        rebuildAllBalancesInternal()
        FireflyImportOutcome(
            written, skipped, newAccounts, newCategories,
            openingsApplied, openingsSkipped
        )
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
