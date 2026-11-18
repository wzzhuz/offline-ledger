package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupAdjustment
import com.zwz.offlineledger.backup.dto.BackupBudget
import com.zwz.offlineledger.backup.dto.BackupCategory
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.BackupTemplate
import com.zwz.offlineledger.backup.dto.BackupRecurringRule
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.data.model.Cadence
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.engine.RecurringSchedule

fun RecordEntity.toTrash(deletedAt: Long): TrashEntryEntity = TrashEntryEntity(
    originalId = id, type = type, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats,
    linkedRecordId = linkedRecordId, currencyCode = currencyCode,
    counterparty = counterparty, importSource = importSource,
    sourceId = sourceId, deletedAt = deletedAt
)

fun TrashEntryEntity.toRecord(): RecordEntity = RecordEntity(
    id = originalId, type = type, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats,
    linkedRecordId = linkedRecordId, currencyCode = currencyCode,
    counterparty = counterparty, importSource = importSource,
    sourceId = sourceId
)

fun AccountEntity.toDto(): BackupAccount = BackupAccount(
    id = id, name = name, type = type.name,
    initialBalanceCents = initialBalanceCents, balanceCents = balanceCents,
    creditLimitCents = creditLimitCents, includeInTotal = includeInTotal,
    isArchived = isArchived, sortOrder = sortOrder, currencyCode = currencyCode,
    lastReconciledAt = lastReconciledAt,
    lastReconciledBalanceCents = lastReconciledBalanceCents
)

fun BackupAccount.toEntity(): AccountEntity = AccountEntity(
    id = id, name = name, type = AccountType.valueOf(type),
    initialBalanceCents = initialBalanceCents, balanceCents = balanceCents,
    creditLimitCents = creditLimitCents, includeInTotal = includeInTotal,
    isArchived = isArchived, sortOrder = sortOrder, currencyCode = currencyCode,
    lastReconciledAt = lastReconciledAt,
    lastReconciledBalanceCents = lastReconciledBalanceCents
)

fun CategoryEntity.toDto(): BackupCategory = BackupCategory(
    id = id, name = name, type = type.name, parentId = parentId, level = level,
    iconName = iconName, colorHex = colorHex, sortOrder = sortOrder,
    isArchived = isArchived
)

fun BackupCategory.toEntity(): CategoryEntity = CategoryEntity(
    id = id, name = name, type = RecordType.valueOf(type), parentId = parentId,
    level = level, iconName = iconName, colorHex = colorHex,
    sortOrder = sortOrder, isArchived = isArchived
)

fun RecordEntity.toDto(): BackupRecord = BackupRecord(
    id = id, type = type.name, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats,
    linkedRecordId = linkedRecordId, currencyCode = currencyCode,
    counterparty = counterparty, importSource = importSource,
    sourceId = sourceId
)

fun BackupRecord.toEntity(): RecordEntity = RecordEntity(
    id = id, type = RecordType.valueOf(type), amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats,
    linkedRecordId = linkedRecordId, currencyCode = currencyCode,
    counterparty = counterparty, importSource = importSource,
    sourceId = sourceId
)

fun BalanceAdjustmentEntity.toDto(): BackupAdjustment = BackupAdjustment(
    id = id, accountId = accountId, deltaCents = deltaCents,
    memo = memo, source = source, createdAt = createdAt, isRevoked = isRevoked
)

fun BackupAdjustment.toEntity(): BalanceAdjustmentEntity = BalanceAdjustmentEntity(
    id = id, accountId = accountId, deltaCents = deltaCents,
    memo = memo, source = source, createdAt = createdAt, isRevoked = isRevoked
)

// ---------------------------------------------------------------
// 阶段 6a：模板 / 周期规则的 DTO 映射与流水生成。
// 生成流水时字段语义与 RecordEntity 完全一致，再交给
// LedgerRepository.withResolvedRoot 解析 rootCategoryId 并入同一事务。
// ---------------------------------------------------------------

fun RecordTemplateEntity.toDto(): BackupTemplate = BackupTemplate(
    id = id, name = name, type = type.name, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    note = note, excludeFromStats = excludeFromStats,
    sortOrder = sortOrder, isArchived = isArchived, createdAt = createdAt,
    counterparty = counterparty
)

fun BackupTemplate.toEntity(): RecordTemplateEntity = RecordTemplateEntity(
    id = id, name = name, type = RecordType.valueOf(type), amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    note = note, excludeFromStats = excludeFromStats,
    sortOrder = sortOrder, isArchived = isArchived, createdAt = createdAt,
    counterparty = counterparty
)

fun RecurringRuleEntity.toDto(): BackupRecurringRule = BackupRecurringRule(
    id = id, name = name, type = type.name, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    note = note, excludeFromStats = excludeFromStats,
    cadence = cadence.name, dayOfMonth = dayOfMonth, dayOfWeek = dayOfWeek,
    anchorLocalDate = anchorLocalDate,
    lastGeneratedLocalDate = lastGeneratedLocalDate,
    isEnabled = isEnabled, createdAt = createdAt, counterparty = counterparty
)

fun BackupRecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id, name = name, type = RecordType.valueOf(type), amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    note = note, excludeFromStats = excludeFromStats,
    cadence = Cadence.valueOf(cadence), dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek, anchorLocalDate = anchorLocalDate,
    lastGeneratedLocalDate = lastGeneratedLocalDate,
    isEnabled = isEnabled, createdAt = createdAt, counterparty = counterparty
)

fun RecordTemplateEntity.toRecordEntity(atEpochMilli: Long): RecordEntity {
    val ld = LedgerClock.fromEpochMilli(atEpochMilli)
    return RecordEntity(
        type = type, amountCents = amountCents,
        categoryId = categoryId, rootCategoryId = rootCategoryId,
        fromAccountId = fromAccountId, toAccountId = toAccountId,
        timestamp = atEpochMilli, localDate = ld.localDate,
        yearMonth = ld.yearMonth, note = note,
        excludeFromStats = excludeFromStats, counterparty = counterparty
    )
}

fun RecurringRuleEntity.toRecordEntity(atEpochMilli: Long, dueLocalDate: Int): RecordEntity =
    RecordEntity(
        type = type, amountCents = amountCents,
        categoryId = categoryId, rootCategoryId = rootCategoryId,
        fromAccountId = fromAccountId, toAccountId = toAccountId,
        timestamp = atEpochMilli, localDate = dueLocalDate,
        yearMonth = RecurringSchedule.yearMonthOf(dueLocalDate),
        note = if (note.isBlank()) name else note,
        excludeFromStats = excludeFromStats, counterparty = counterparty
    )

// ---------------------------------------------------------------
// 阶段 6b：预算规则 DTO 映射。
// ---------------------------------------------------------------
fun BudgetEntity.toDto(): BackupBudget = BackupBudget(
    id = id, name = name, scope = scope.name, categoryId = categoryId,
    limitCents = limitCents, sortOrder = sortOrder,
    isArchived = isArchived, createdAt = createdAt
)

fun BackupBudget.toEntity(): BudgetEntity = BudgetEntity(
    id = id, name = name, scope = BudgetScope.valueOf(scope),
    categoryId = categoryId, limitCents = limitCents,
    sortOrder = sortOrder, isArchived = isArchived, createdAt = createdAt
)
