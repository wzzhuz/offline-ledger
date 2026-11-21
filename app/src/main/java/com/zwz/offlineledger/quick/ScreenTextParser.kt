package com.zwz.offlineledger.quick

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 第 49 批：把节点文本或 OCR 文本块解析成一条候选流水。
 * 纯 JVM、确定性、无第三方依赖；金额一律走 BigDecimal 转分，MUST NOT 用浮点。
 */
object ScreenTextParser {

    private val AMOUNT = Regex("(?:[¥￥]|RMB|CNY)?\\s*(-?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|-?\\d+(?:\\.\\d{1,2})?)")
    private val DATE = Regex("(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})")
    private val TIME = Regex("(\\d{1,2}):(\\d{2})")
    private val AMOUNT_HINT = listOf("金额", "实付", "付款", "收款", "合计", "总额", "¥", "￥", "RMB", "CNY")
    /** 第 50 批：账单页里常见的「不是这笔金额」的数字，默认排除。 */
    private val AMOUNT_WEAK = listOf(
        "余额", "可用余额", "账户余额", "优惠", "红包", "积分", "手续费",
        "找零", "原价", "累计", "订单号", "流水号"
    )
    /** 强标签：即使同一行出现弱相关词，也优先按标签取。 */
    private val AMOUNT_STRONG = listOf(
        "实付", "付款金额", "支付金额", "收款金额", "退款金额", "金额", "合计", "总额"
    )
    private val INCOME_WORD = listOf("退款", "收入", "收款", "入账", "转入", "已退", "返还")
    private val EXPENSE_WORD = listOf("支出", "付款", "支付", "消费", "缴费", "已付", "转出")
    private val MERCHANT_HINT = listOf("收款方", "商户", "商家", "交易对方", "收款人", "付款方", "店铺名称", "门店")
    private val DESC_HINT = listOf("商品", "商品说明", "说明", "摘要", "订单标题", "交易摘要", "备注")
    private val NOISE = listOf(
        "交易成功", "支付成功", "已完成", "订单号", "流水号", "交易单号",
        "付款方式", "支付方式", "账单详情",
        // 第 50 批：账单页标题，不能被 guessMerchant 当成交易对方。
        "交易详情", "订单详情", "支付详情", "交易记录", "收支明细",
        "我的账单", "账单", "详情页", "返回", "更多"
    )

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 解析失败返回 null，由上层给「未识别 + 手动记账」，MUST NOT 猜一个金额。 */
    fun parse(lines: List<String>, now: Long): ParsedBill? = parseWithTrace(lines, now)?.bill

    /**
     * 第 50 批：解析同时产出「每个字段来自哪一块、用了哪条规则」，
     * 以及全部金额候选（含被排除的），供确认页改选与脱敏 fixture 单测锁死。
     */
    fun parseWithTrace(lines: List<String>, now: Long): ParseTrace? {
        val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return null
        val options = amountCandidates(clean)
        val picked = options.firstOrNull { it.rejected == null } ?: return null
        if (picked.amountCents <= 0L) return null
        val amountCents = picked.amountCents
        val joined = clean.joinToString(" ")
        val income = isIncome(joined)
        val counterparty = afterLabel(clean, MERCHANT_HINT) ?: guessMerchant(clean)
        val description = afterLabel(clean, DESC_HINT) ?: guessDescription(clean, counterparty)
        val dateText = DATE.find(joined)?.value ?: ""
        val timestamp = parseTime(joined, now)
        val fingerprint = fingerprint(amountCents, income, counterparty, description, dateText)
        val bill = ParsedBill(
            amountCents = amountCents,
            isIncome = income,
            counterparty = counterparty,
            description = description,
            note = "",
            timestamp = timestamp,
            fingerprint = fingerprint,
            timeText = dateText
        )
        val fields = listOf(
            FieldTrace("金额", picked.blockIndex, picked.rawText, picked.rule),
            FieldTrace(
                "交易对方",
                clean.indexOfFirst { it.contains(counterparty) && counterparty.isNotBlank() },
                counterparty,
                if (clean.any { MERCHANT_HINT.any { h -> it.contains(h) } }) "标签提取" else "兜底猜测"
            ),
            FieldTrace(
                "描述",
                clean.indexOfFirst { it.contains(description) && description.isNotBlank() },
                description,
                if (clean.any { DESC_HINT.any { h -> it.contains(h) } }) "标签提取" else "兜底猜测"
            )
        )
        return ParseTrace(bill, fields, options)
    }

    /**
     * 金额候选排序：强标签行 > 货币符号 > 两位小数 > 普通数字；
     * 日期 / 时间、弱相关（余额 / 优惠 / 订单号）、负号一律标 rejected，
     * 默认不取，但保留在列表里让用户一眼看出「为什么没选它」。
     */
    fun amountCandidates(lines: List<String>): List<AmountCandidate> {
        val out = mutableListOf<AmountCandidate>()
        lines.forEachIndexed { index, line ->
            val hasDate = DATE.containsMatchIn(line) || TIME.containsMatchIn(line)
            val strong = AMOUNT_STRONG.any { line.contains(it) }
            val weak = AMOUNT_WEAK.any { line.contains(it) }
            val currency = listOf("¥", "￥", "RMB", "CNY").any { line.contains(it) }
            for (m in AMOUNT.findAll(line)) {
                val raw = m.groupValues[1].replace(",", "")
                if (raw.length > 9) continue
                val d = raw.toBigDecimalOrNull() ?: continue
                if (d <= BigDecimal.ZERO) continue
                if (d.scale() <= 0 && d.toLong() > 1000000L) continue
                val cents = d.movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP).toLong()
                val rejected = when {
                    raw.startsWith("-") -> "负号金额（不自动取）"
                    hasDate && !strong -> "日期 / 时间"
                    weak && !strong -> "弱相关金额（余额 / 优惠 / 单号）"
                    else -> null
                }
                val rule = when {
                    strong -> "强标签：" + AMOUNT_STRONG.first { line.contains(it) }
                    currency -> "货币符号 / 金额字面量"
                    d.scale() == 2 -> "两位小数"
                    else -> "普通数字"
                }
                out.add(AmountCandidate(index, raw, cents, rule, rejected))
            }
        }
        return out.sortedWith(
            compareBy<AmountCandidate> { it.rejected != null }
                .thenByDescending { it.rule.startsWith("强标签") }
                .thenByDescending { it.rule.startsWith("货币符号") }
                .thenByDescending { it.rawText.contains('.') }
                .thenBy { it.blockIndex }
        )
    }

    private fun isIncome(joined: String): Boolean {
        val inc = INCOME_WORD.any { joined.contains(it) }
        val exp = EXPENSE_WORD.any { joined.contains(it) }
        return inc && !exp
    }

    private fun afterLabel(lines: List<String>, labels: List<String>): String? {
        for (line in lines) {
            for (label in labels) {
                val i = line.indexOf(label)
                if (i < 0) continue
                val rest = line.substring(i + label.length)
                    .trimStart(':', '：', ' ', '\t', '-', '—')
                    .trim()
                if (rest.length >= 2 && rest.length <= 60) return rest
            }
        }
        return null
    }

    private fun guessMerchant(lines: List<String>): String {
        for (line in lines) {
            if (line.length !in 2..30) continue
            if (line.any { it.isDigit() }) continue
            if (NOISE.any { line.contains(it) }) continue
            if (AMOUNT_HINT.any { line.contains(it) }) continue
            if (AMOUNT_STRONG.any { line.contains(it) }) continue
            if (AMOUNT_WEAK.any { line.contains(it) }) continue
            if (MERCHANT_HINT.any { line.contains(it) }) continue
            return line
        }
        return ""
    }

    private fun guessDescription(lines: List<String>, merchant: String): String {
        for (line in lines) {
            if (line == merchant) continue
            if (line.length !in 2..60) continue
            if (NOISE.any { line.contains(it) }) continue
            if (line.any { it.isDigit() }) continue
            if (AMOUNT_HINT.any { line.contains(it) }) continue
            if (AMOUNT_STRONG.any { line.contains(it) }) continue
            if (AMOUNT_WEAK.any { line.contains(it) }) continue
            if (MERCHANT_HINT.any { line.contains(it) }) continue
            return line
        }
        return ""
    }

    private fun parseTime(joined: String, now: Long): Long {
        val d = DATE.find(joined) ?: return now
        return try {
            val y = d.groupValues[1].toInt()
            val mo = d.groupValues[2].toInt().coerceIn(1, 12)
            val day = d.groupValues[3].toInt().coerceIn(1, 31)
            val t = TIME.find(joined)
            val h = t?.groupValues?.get(1)?.toInt()?.coerceIn(0, 23) ?: 12
            val mi = t?.groupValues?.get(2)?.toInt()?.coerceIn(0, 59) ?: 0
            LocalDateTime.of(y, mo, day, h, mi).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            now
        }
    }

    /** 连点两次磁贴 MUST 得到同一指纹：只用解析出的结构化字段，绝不含 now。 */
    fun fingerprint(
        amountCents: Long,
        income: Boolean,
        counterparty: String,
        description: String,
        timeText: String
    ): String {
        val raw = listOf(
            amountCents.toString(),
            income.toString(),
            counterparty.trim(),
            description.trim(),
            timeText.trim()
        ).joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * 第 50 批：确认页改选金额。MUST 保留原 fingerprint，
     * 否则「连点两次磁贴只入账一次」的幂等会被人工纠偏破坏。
     */
    fun withAmount(bill: ParsedBill, amountCents: Long): ParsedBill =
        bill.copy(amountCents = amountCents)

    fun withCounterparty(bill: ParsedBill, value: String): ParsedBill =
        bill.copy(counterparty = value)

    fun withDescription(bill: ParsedBill, value: String): ParsedBill =
        bill.copy(description = value)

}
