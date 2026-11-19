package com.zwz.offlineledger.backup.csv

import com.zwz.offlineledger.core.time.LedgerClock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Firefly III 6.6.x transactions.csv 解析器。纯 JVM、零第三方依赖。
 *
 * 表头按列名别名映射，顺序无关；复用 CsvCodec 的引号状态机。
 * 真实样本（6.6.6，1916 行 / 53 列）证实：Withdrawal 金额为负、
 * Deposit 与 Transfer 为正、Opening balance 带符号，故一律 abs() 落流水、
 * 期初余额保留符号。样本每 split 独立一行且各有 journal_id，
 * 所以按行导入而非合并，明细不丢。
 */
object FireflyCsvCodec {

    private val ALIASES: Map<String, List<String>> = mapOf(
        "journal_id" to listOf("journal_id", "transaction_journal_id"),
        "group_id" to listOf("group_id"),
        "group_title" to listOf("group_title"),
        "type" to listOf("type"),
        "amount" to listOf("amount"),
        "currency_code" to listOf("currency_code"),
        "foreign_currency_code" to listOf("foreign_currency_code"),
        "foreign_amount" to listOf("foreign_amount"),
        "description" to listOf("description"),
        "date" to listOf("date"),
        "source_name" to listOf("source_name"),
        "source_type" to listOf("source_type"),
        "destination_name" to listOf("destination_name"),
        "destination_type" to listOf("destination_type"),
        "category" to listOf("category"),
        "tags" to listOf("tags"),
        "notes" to listOf("notes"),
        "budget" to listOf("budget")
    )

    private val REQUIRED = listOf(
        "journal_id", "type", "amount", "date", "source_name", "destination_name"
    )

    private val KNOWN_TYPES = setOf(
        "Withdrawal", "Deposit", "Transfer", "Opening balance", "Reconciliation"
    )

    /** 全量解析；任一行非法都返回带行号的错误，调用方不得进入写入事务。 */
    fun parse(bytes: ByteArray): FireflyParseResult {
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if (text.isBlank()) {
            return FireflyParseResult(emptyList(), listOf("文件为空"), emptyList())
        }
        val raw = CsvCodec.parseRows(text, ',').filter { row -> row.any { it.isNotBlank() } }
        if (raw.size < 2) {
            return FireflyParseResult(emptyList(), listOf("文件无有效数据行"), emptyList())
        }
        val header = raw.first().map { it.trim() }
        val idx = HashMap<String, Int>()
        ALIASES.forEach { (key, names) ->
            idx[key] = names.firstNotNullOfOrNull { n ->
                header.indexOf(n).takeIf { i -> i >= 0 }
            } ?: -1
        }
        val missing = REQUIRED.filter { (idx[it] ?: -1) < 0 }
        if (missing.isNotEmpty()) {
            return FireflyParseResult(
                emptyList(),
                listOf("不是 Firefly III 交易导出，缺少列：" + missing.joinToString(", ")),
                emptyList()
            )
        }

        fun get(row: List<String>, key: String): String {
            val k = idx[key] ?: -1
            return if (k >= 0 && k < row.size) row[k].trim() else ""
        }

        val rows = mutableListOf<FireflyRow>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        raw.drop(1).forEachIndexed { i, r ->
            val lineNo = i + 2
            val lineErrors = mutableListOf<String>()
            val t = get(r, "type")
            val jid = get(r, "journal_id")
            val amount = get(r, "amount")
            val date = get(r, "date")
            if (t !in KNOWN_TYPES) lineErrors += "未知交易类型：" + t
            if (jid.isBlank()) lineErrors += "journal_id 为空"
            try {
                BigDecimal(amount)
            } catch (e: Exception) {
                lineErrors += "金额格式非法：" + amount
            }
            try {
                parseDate(date)
            } catch (e: Exception) {
                lineErrors += "日期格式非法：" + date
            }
            if (get(r, "source_name").isBlank() || get(r, "destination_name").isBlank()) {
                lineErrors += "账户名为空"
            }
            if (lineErrors.isEmpty()) {
                rows += FireflyRow(
                    lineNo = lineNo,
                    journalId = jid,
                    groupId = get(r, "group_id"),
                    groupTitle = get(r, "group_title"),
                    type = t,
                    amountRaw = amount,
                    currencyCode = get(r, "currency_code"),
                    foreignCurrencyCode = get(r, "foreign_currency_code"),
                    foreignAmount = get(r, "foreign_amount"),
                    description = get(r, "description"),
                    dateRaw = date,
                    sourceName = get(r, "source_name"),
                    sourceType = get(r, "source_type"),
                    destinationName = get(r, "destination_name"),
                    destinationType = get(r, "destination_type"),
                    categoryRaw = get(r, "category"),
                    tags = get(r, "tags"),
                    notes = get(r, "notes"),
                    budget = get(r, "budget")
                )
                val fc = get(r, "foreign_currency_code")
                if (fc.isNotBlank() && fc != get(r, "currency_code")) {
                    warnings += "第 $lineNo 行含外币 $fc，按主币种落账，不做折算"
                }
            } else {
                errors += "第 $lineNo 行：" + lineErrors.joinToString("、")
            }
        }
        if (rows.isEmpty() && errors.isEmpty()) errors += "没有可导入的数据行"
        return FireflyParseResult(rows, errors, warnings)
    }

    /** 纯 JVM 映射：复式 -> 单式，不接触数据库。 */
    fun buildPlan(parsed: FireflyParseResult): FireflyPlan {
        val entries = mutableListOf<FireflyEntry>()
        val accountNames = LinkedHashSet<String>()
        val categoryKeys = LinkedHashSet<FireflyCategoryKey>()
        val warnings = parsed.warnings.toMutableList()

        parsed.rows.forEach { r ->
            val bd = BigDecimal(r.amountRaw)
            val absYuan = bd.abs().setScale(2, RoundingMode.HALF_UP).toPlainString()
            val ts = parseDate(r.dateRaw)
            val ld = LedgerClock.fromEpochMilli(ts)

            var kind = FireflyKind.SKIPPED
            var account = r.sourceName
            var toAccount: String? = null
            var counterparty = ""
            var catType = ""
            var skipReason = ""
            when (r.type) {
                "Withdrawal" -> {
                    kind = FireflyKind.EXPENSE
                    account = r.sourceName
                    counterparty = r.destinationName
                    catType = "EXPENSE"
                }
                "Deposit" -> {
                    kind = FireflyKind.INCOME
                    account = r.destinationName
                    counterparty = r.sourceName
                    catType = "INCOME"
                }
                "Transfer" -> {
                    kind = FireflyKind.TRANSFER
                    account = r.sourceName
                    toAccount = r.destinationName
                }
                "Opening balance" -> {
                    kind = FireflyKind.OPENING
                    account = if (r.sourceType == "Initial balance account") {
                        r.destinationName
                    } else r.sourceName
                }
                else -> {
                    skipReason = "Reconciliation 或未知类型不进流水"
                }
            }

            var categoryKey = ""
            if ((kind == FireflyKind.EXPENSE || kind == FireflyKind.INCOME) &&
                r.categoryRaw.isNotBlank()
            ) {
                val root = r.categoryRaw.substringBefore('-').trim()
                val child = r.categoryRaw.substringAfter('-', "").trim()
                val ck = FireflyCategoryKey(catType, root, child)
                categoryKeys += ck
                categoryKey = ck.key()
            }

            // 第 26 批：备注逐字保留 Firefly III `notes` 原文，不再拼接
            // description / 分组 / 标签 / 预算 / FF3分类，也不再写 ff3: 前缀。
            // 来源完全由 (importSource, sourceId) 字段承载；无对应字段的元数据
            // 不塞进备注，在导入结果与规格里统一说明未迁移。
            val note = r.notes

            val amountYuan = if (kind == FireflyKind.OPENING) {
                bd.setScale(2, RoundingMode.HALF_UP).toPlainString()
            } else absYuan

            entries += FireflyEntry(
                lineNo = r.lineNo,
                journalId = r.journalId,
                kind = kind,
                accountName = account,
                toAccountName = toAccount,
                counterparty = counterparty,
                categoryKey = categoryKey,
                categoryRaw = r.categoryRaw,
                amountYuan = amountYuan,
                currencyCode = r.currencyCode.ifBlank { "CNY" },
                epochMilli = ts,
                localDate = ld.localDate,
                yearMonth = ld.yearMonth,
                note = note,
                description = r.description,
                skipReason = skipReason
            )
            if (kind != FireflyKind.SKIPPED) {
                accountNames += account
                if (toAccount != null) accountNames += toAccount
            }
        }
        return FireflyPlan(entries, accountNames.toList(), categoryKeys.toList(), warnings)
    }

    /** 账户类型建议，可在预览页逐个改。 */
    fun inferAccountType(name: String): String = when {
        listOf("信用卡", "花呗", "月付", "白条", "债-").any { name.contains(it) } -> "LIABILITY"
        listOf("加油卡", "充值卡", "公交卡", "一卡通", "储值").any { name.contains(it) } -> "PREPAID"
        listOf("证券", "基金", "余额宝", "零钱通", "小金库", "理财").any { name.contains(it) } -> "INVESTMENT"
        else -> "ASSET"
    }

    /** ISO 8601 带偏移；无偏移时按设备时区 Asia/Shanghai 解释。 */
    private fun parseDate(raw: String): Long {
        val odt = try {
            OffsetDateTime.parse(raw)
        } catch (e: Exception) {
            LocalDateTime.parse(raw).atZone(LedgerClock.zoneId).toOffsetDateTime()
        }
        return odt.toInstant().toEpochMilli()
    }
}
