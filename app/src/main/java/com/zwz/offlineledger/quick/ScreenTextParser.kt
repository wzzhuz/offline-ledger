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
    private val INCOME_WORD = listOf("退款", "收入", "收款", "入账", "转入", "已退", "返还")
    private val EXPENSE_WORD = listOf("支出", "付款", "支付", "消费", "缴费", "已付", "转出")
    private val MERCHANT_HINT = listOf("收款方", "商户", "商家", "交易对方", "收款人", "付款方", "店铺名称", "门店")
    private val DESC_HINT = listOf("商品", "商品说明", "说明", "摘要", "订单标题", "交易摘要", "备注")
    private val NOISE = listOf("交易成功", "支付成功", "已完成", "订单号", "流水号", "交易单号", "付款方式", "支付方式", "账单详情")

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 解析失败返回 null，由上层给「未识别 + 手动记账」，MUST NOT 猜一个金额。 */
    fun parse(lines: List<String>, now: Long): ParsedBill? {
        val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return null
        val amountCents = findAmount(clean) ?: return null
        if (amountCents <= 0L) return null
        val joined = clean.joinToString(" ")
        val income = isIncome(joined)
        val counterparty = afterLabel(clean, MERCHANT_HINT) ?: guessMerchant(clean)
        val description = afterLabel(clean, DESC_HINT) ?: guessDescription(clean, counterparty)
        val dateText = DATE.find(joined)?.value ?: ""
        val timestamp = parseTime(joined, now)
        val fingerprint = fingerprint(amountCents, income, counterparty, description, dateText)
        return ParsedBill(
            amountCents = amountCents,
            isIncome = income,
            counterparty = counterparty,
            description = description,
            note = "",
            timestamp = timestamp,
            fingerprint = fingerprint,
            timeText = dateText
        )
    }

    private fun findAmount(lines: List<String>): Long? {
        val ordered = lines.sortedByDescending { l -> AMOUNT_HINT.count { l.contains(it) } }
        for (line in ordered) {
            for (m in AMOUNT.findAll(line)) {
                val raw = m.groupValues[1].replace(",", "")
                if (raw.startsWith("-")) continue
                if (raw.length > 9) continue
                val d = raw.toBigDecimalOrNull() ?: continue
                if (d <= BigDecimal.ZERO) continue
                if (d.scale() <= 0 && d.toLong() > 1000000L) continue
                return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
            }
        }
        return null
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
}
