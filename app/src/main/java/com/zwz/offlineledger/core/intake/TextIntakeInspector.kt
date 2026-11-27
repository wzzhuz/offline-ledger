package com.zwz.offlineledger.core.intake

import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.engine.ExtraAmountRules
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 第 87 批：支付宝账单详情原文的只读解析器。
 *
 * - 纯 JVM，无 Android / Room 依赖，便于粘贴试解析与 JVM 单测。
 * - 主金额靠「状态锚点前最近独立金额行」选，不靠金额候选唯一。
 * - 优惠靠「原价 = 实付 + 优惠」整数分算术校验，不靠候选数量。
 * - 严格充分条件不满足时 readyForAutoCommit=false；调用方 MUST 降级草稿。
 */
object TextIntakeInspector {

    const val MAX_CHARS = 20_000

    private val AMOUNT_TEXT = Regex("-?\\d[\\d,]*\\.\\d{2}")
    private val MONEY_LINE = Regex("^-?\\d+\\.\\d{2}$")
    private val MONEY_PARSE = Regex("^(\\d+)\\.(\\d{2})$")
    private val DATE_TIME_LINE = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")
    private val ACCOUNT_LINE = Regex("^(.+?(?:信用卡|储蓄卡|借记卡))\\((\\d{4})\\)$")
    private val LONG_ORDER = Regex("^\\d{20,}$")
    private val MASK_LINE = Regex("^\\d{4} \\*{6}.*$")
    private val POINTS_LINE = Regex("^已领取\\d+积分$")
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val PAGE_MARKERS = setOf(
        "账单管理", "收单机构", "商家订单号", "支付奖励", "账单分类", "付款方式",
    )

    fun from(
        source: String,
        text: String?,
        rules: TextIntakeRuleSet = TextIntakeRules.DEFAULT,
    ): TextIntakePreview {
        val raw = text.orEmpty()
        val original = raw.length
        val truncated = original > MAX_CHARS
        val shown = if (truncated) raw.take(MAX_CHARS) else raw
        val lines = shown.split(Regex("\\r?\\n")).map(::cleanLine)
        val clean = lines.filter { it.isNotEmpty() }

        val amountCandidates = AMOUNT_TEXT.findAll(shown)
            .map { it.value }.distinct().toList()

        val statusIdx = clean.indexOfFirst { it in rules.statusWords }
        val statusWord = clean.getOrNull(statusIdx).orEmpty()
        var mainIdx = -1
        if (statusIdx > 0) {
            for (i in statusIdx - 1 downTo 0) {
                val l = clean[i]
                if (MONEY_LINE.matches(l) && l.startsWith("-")) {
                    mainIdx = i
                    break
                }
            }
        }
        val mainSignedYuan = clean.getOrNull(mainIdx).orEmpty()
        val mainYuan = mainSignedYuan.removePrefix("-")
        val amountCents = yuanToCentsAbs(mainYuan)

        val dtText = findValueAfterLabel(clean, "支付时间", 5) { DATE_TIME_LINE.matches(it) }
        val localDate = dtText.takeIf { it.isNotBlank() }
            ?.substring(0, 10)?.replace("-", "")?.toIntOrNull()
        val timestamp = parseTimestamp(dtText)

        val accountRaw = findValueAfterLabel(clean, "付款方式", 5) { ACCOUNT_LINE.matches(it) }
        val accountMatch = ACCOUNT_LINE.matchEntire(accountRaw)
        val accountName = accountMatch?.groupValues?.get(1)
            ?.removeSuffix("信用卡")?.removeSuffix("储蓄卡")?.removeSuffix("借记卡")?.trim()
            .orEmpty()
        val cardLast4 = accountMatch?.groupValues?.get(2).orEmpty()

        val sourceId = findOrderId(clean)
        val billCategory = findMeaningfulAfterLabel(clean, "账单分类", 6, rules)
        val categoryRoot = rules.categoryMap[billCategory]

        val counterparty = findCounterparty(clean, mainIdx, rules)
        val pageMarkers = PAGE_MARKERS.count { marker -> clean.any { it == marker } }

        var originalYuan = ""
        var discountYuan = ""
        var discountLabel = ""
        if (statusIdx >= 0) {
            val originalIdx = (statusIdx + 1 until clean.size).firstOrNull { clean[it] == "订单金额" }
            if (originalIdx != null) {
                originalYuan = findValueAfterLabel(clean, "订单金额", 4) { MONEY_LINE.matches(it) && !it.startsWith("-") }
            }
            val discountIdx = (statusIdx + 1 until clean.size).firstOrNull { i ->
                val l = clean[i]
                l != "订单金额" && rules.discountWords.any { w -> l.contains(w) }
            }
            if (discountIdx != null) {
                discountLabel = clean[discountIdx]
                discountYuan = findFirstMoneyAfter(clean, discountIdx + 1, 4)
            }
        }

        val originalCents = yuanToCentsAbs(originalYuan)
        var discountCents = yuanToCentsAbs(discountYuan.removePrefix("-"))
        val warnings = mutableListOf<String>()
        val missing = mutableListOf<String>()

        var hasDiscountBlock = originalYuan.isNotBlank() || discountYuan.isNotBlank() || discountLabel.isNotBlank()
        var arithmeticOk = true
        if (hasDiscountBlock) {
            if (originalYuan.isBlank() || discountYuan.isBlank() || amountCents == null || originalCents == null || discountCents == null) {
                arithmeticOk = false
                warnings += "优惠信息不完整，未抽取优惠金额"
            } else if (originalCents != amountCents + discountCents) {
                arithmeticOk = false
                warnings += "原价 / 优惠 / 实付算术不成立"
            }
            if (!arithmeticOk) discountCents = null
        } else {
            discountCents = null
        }

        if (amountCandidates.size > 1) {
            warnings += "存在多个金额候选，已由状态锚点选主金额"
        }
        if (billCategory.isNotBlank() && categoryRoot == null) {
            warnings += "账单分类「$billCategory」未映射，分类留空"
        }
        if (pageMarkers < 2) {
            missing += "页面特征不足，不外推为支付宝账单详情"
        }
        if (truncated) missing += "原文超过 20000 字符"
        if (mainIdx < 0 || mainSignedYuan.isBlank() || amountCents == null || amountCents <= 0L) {
            missing += "未找到负号主金额"
        }
        if (statusWord.isBlank()) missing += "未找到状态锚点"
        if (localDate == null || timestamp <= 0L) missing += "未找到合法支付时间"
        if (counterparty.isBlank()) missing += "未找到收款方线索"
        if (accountName.isBlank() || cardLast4.isBlank()) missing += "未找到账户名与尾号"
        if (sourceId.isBlank()) missing += "未找到订单号"
        if (!arithmeticOk) missing += "优惠算术未通过"
        if (amountCents != null) {
            val err = ExtraAmountRules.validate(
                RecordType.EXPENSE,
                amountCents,
                discountCents,
                null,
            )
            if (err != null) missing += err
        }

        val ready = missing.isEmpty()
        val draft = TextIntakeCommitDraft(
            importSource = "alipay",
            sourceId = sourceId,
            amountCents = amountCents ?: 0L,
            amountYuan = mainYuan,
            discountCents = discountCents,
            discountYuan = discountYuan,
            originalYuan = originalYuan,
            localDate = localDate ?: 0,
            timestamp = timestamp,
            accountName = accountName,
            cardLast4 = cardLast4,
            categoryRoot = categoryRoot,
            category = "",
            billCategory = billCategory,
            counterparty = counterparty,
            description = "",
            note = "",
            statusWord = statusWord,
            discountLabel = discountLabel,
            pageMarkers = pageMarkers,
            warnings = warnings + missing,
        )

        return TextIntakePreview(
            source = source,
            rawText = shown,
            truncated = truncated,
            originalCharCount = original,
            amountCandidates = amountCandidates,
            hasDirection = mainIdx >= 0,
            hasTime = localDate != null && timestamp > 0L,
            hasCounterparty = counterparty.isNotBlank(),
            readyForAutoCommit = ready,
            missing = missing,
            warnings = warnings,
            commitDraft = draft,
        )
    }

    /** 严格两位小数，返回正整数分；不允许 Float / Double 参与。 */
    fun yuanToCentsAbs(raw: String): Long? {
        var t = raw.trim().replace(",", "")
        if (t.startsWith("-")) t = t.removePrefix("-")
        val m = MONEY_PARSE.matchEntire(t) ?: return null
        val yuan = m.groupValues[1].toLongOrNull() ?: return null
        val cents = m.groupValues[2].toLongOrNull() ?: return null
        if (yuan > Long.MAX_VALUE / 100L) return null
        return yuan * 100L + cents
    }

    private fun parseTimestamp(value: String): Long =
        if (!DATE_TIME_LINE.matches(value)) 0L else try {
            LocalDateTime.parse(value, DT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }

    private fun cleanLine(line: String): String {
        var t = line.trim()
        if (t == "logo" || t == "arrow" || t == "extend-icon") return ""
        t = t.removeSuffix("arrow").removeSuffix("extend-icon").trim()
        return t
    }

    private fun isNoise(line: String, rules: TextIntakeRuleSet): Boolean {
        if (line.isBlank()) return true
        if (line in rules.noiseLabels) return true
        if (POINTS_LINE.matches(line)) return true
        if (line.startsWith("已解锁") && line.contains("贴纸")) return true
        if (MASK_LINE.matches(line)) return true
        if (line == "logo" || line == "arrow" || line == "extend-icon") return true
        return false
    }

    private fun findValueAfterLabel(
        lines: List<String>,
        label: String,
        window: Int,
        accept: (String) -> Boolean,
    ): String {
        val idx = lines.indexOfFirst { it == label }
        if (idx < 0) return ""
        val end = minOf(lines.size, idx + 1 + window)
        for (i in idx + 1 until end) {
            if (accept(lines[i])) return lines[i]
        }
        return ""
    }

    private fun findFirstMoneyAfter(lines: List<String>, start: Int, window: Int): String {
        val end = minOf(lines.size, start + window)
        for (i in start until end) {
            if (MONEY_LINE.matches(lines[i])) return lines[i]
        }
        return ""
    }

    private fun findOrderId(lines: List<String>): String {
        val idx = lines.indexOfFirst { it == "订单号" }
        if (idx < 0) return ""
        val end = minOf(lines.size, idx + 8)
        for (i in idx + 1 until end) {
            if (LONG_ORDER.matches(lines[i])) return lines[i]
        }
        return ""
    }

    private fun findMeaningfulAfterLabel(
        lines: List<String>,
        label: String,
        window: Int,
        rules: TextIntakeRuleSet,
    ): String {
        val idx = lines.indexOfFirst { it == label }
        if (idx < 0) return ""
        val end = minOf(lines.size, idx + 1 + window)
        for (i in idx + 1 until end) {
            val l = lines[i]
            if (isNoise(l, rules) || MONEY_LINE.matches(l) || DATE_TIME_LINE.matches(l) || LONG_ORDER.matches(l)) continue
            return l
        }
        return ""
    }

    private fun findCounterparty(
        lines: List<String>,
        mainIdx: Int,
        rules: TextIntakeRuleSet,
    ): String {
        val fullIdx = lines.indexOfFirst { it == "收款方全称" }
        if (fullIdx >= 0) {
            val v = findMeaningfulAfterLabel(lines, "收款方全称", 4, rules)
            if (v.isNotBlank()) return v
        }
        val detailIdx = lines.indexOfFirst { it == "交易详情" }
        if (detailIdx >= 0) {
            val v = findMeaningfulAfterLabel(lines, "交易详情", 4, rules)
            if (v.isNotBlank()) return v
        }
        if (mainIdx > 0) {
            for (i in mainIdx - 1 downTo 0) {
                val l = lines[i]
                if (isNoise(l, rules) || MONEY_LINE.matches(l) || DATE_TIME_LINE.matches(l)) continue
                if (l in rules.statusWords || l in PAGE_MARKERS) continue
                if (l.startsWith("-")) continue
                return l
            }
        }
        return ""
    }
}
