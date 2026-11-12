package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupAdjustment
import com.zwz.offlineledger.backup.dto.BackupCategory
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.RecordType

fun RecordEntity.toTrash(deletedAt: Long): TrashEntryEntity = TrashEntryEntity(
    originalId = id, type = type, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats, deletedAt = deletedAt
)

fun TrashEntryEntity.toRecord(): RecordEntity = RecordEntity(
    id = originalId, type = type, amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats
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
    note = note, excludeFromStats = excludeFromStats
)

fun BackupRecord.toEntity(): RecordEntity = RecordEntity(
    id = id, type = RecordType.valueOf(type), amountCents = amountCents,
    categoryId = categoryId, rootCategoryId = rootCategoryId,
    fromAccountId = fromAccountId, toAccountId = toAccountId,
    timestamp = timestamp, localDate = localDate, yearMonth = yearMonth,
    note = note, excludeFromStats = excludeFromStats
)

fun BalanceAdjustmentEntity.toDto(): BackupAdjustment = BackupAdjustment(
    id = id, accountId = accountId, deltaCents = deltaCents,
    memo = memo, source = source, createdAt = createdAt, isRevoked = isRevoked
)

fun BackupAdjustment.toEntity(): BalanceAdjustmentEntity = BalanceAdjustmentEntity(
    id = id, accountId = accountId, deltaCents = deltaCents,
    memo = memo, source = source, createdAt = createdAt, isRevoked = isRevoked
)
