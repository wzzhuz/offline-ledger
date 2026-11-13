package com.zwz.offlineledger.backup.csv

import com.zwz.offlineledger.core.money.Cents
import java.time.LocalDate

/**
 * CSV / TSV 明文编解码，纯 JVM、零第三方依赖。
 *
 * 表头固定 8 列，顺序可不同（按列名映射）；UTF-8 with BOM 保证 Excel 中文不乱码。
 * 金额只在展示层用 BigDecimal 换算，`Long` 分始终是真相源。
 */
object CsvCodec {

    val HEADER: List<String> = listOf(
        "date", "type", "amount", "account",
        "categoryRoot", "category", "note", "currencyCode"
    )

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val DATE_RE = Regex("\\d{4}-\\d{2}-\\d{2}")
    // 用 ASCII 码而非字符字面量，避免静态检查把字符里的引号误判成未闭合字符串。
    private const val QUOTE_CODE = 0x22

    fun export(rows: List<CsvRow>): ByteArray {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        rows.forEach { r ->
            val cells = listOf(
                r.date, r.type, r.amountYuan, r.accountName,
                r.categoryRoot, r.category, r.note, r.currencyCode
            )
            sb.append(cells.joinToString(",") { escape(it) }).append("\r\n")
        }
        return BOM + sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * 先解析全部行再统一校验。任一行非法都返回带行号的错误，
     * 调用方 MUST 在 errors 为空时才进入写入事务（否则会留半套数据）。
     */
    fun parseImport(bytes: ByteArray): CsvParseResult {
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if (text.isBlank()) return CsvParseResult(emptyList(), listOf("文件为空"))
        val firstLine = text.substringBefore('\n')
        val delim = if (firstLine.contains('\t')) '\t' else ','
        val raw = parseRows(text, delim).filter { row -> row.any { it.isNotBlank() } }
        if (raw.isEmpty()) return CsvParseResult(emptyList(), listOf("文件为空或无有效数据行"))

        val header = raw.first()
        val idx = HEADER.associateWith { name -> header.indexOf(name.trim()) }
        val missing = idx.filter { it.value < 0 }.keys.toList()
        if (missing.isNotEmpty()) {
            return CsvParseResult(emptyList(), listOf("表头缺少列：" + missing.joinToString(", ")))
        }

        val rows = mutableListOf<CsvRow>()
        val errors = mutableListOf<String>()
        raw.drop(1).forEachIndexed { i, r ->
            val lineNo = i + 2
            fun get(name: String): String {
                val k = idx.getValue(name)
                return if (k >= 0 && k < r.size) r[k].trim() else ""
            }
            val date = get("date")
            val type = get("type")
            val amount = get("amount")
            val account = get("account")
            val root = get("categoryRoot")
            val child = get("category")
            val note = get("note")
            val cc = get("currencyCode")

            val lineErrors = mutableListOf<String>()
            if (!DATE_RE.matches(date) || !validDate(date)) lineErrors += "日期非法（应为 YYYY-MM-DD）"
            if (type != "EXPENSE" && type != "INCOME") {
                lineErrors += "类型只支持 EXPENSE / INCOME（转账与报销不进导入模板）"
            }
            val cents = try {
                Cents.fromYuan(amount).value
            } catch (e: IllegalArgumentException) {
                lineErrors += "金额格式非法"
                null
            }
            if (cents != null && cents <= 0L) lineErrors += "金额必须大于 0"
            if (account.isBlank()) lineErrors += "账户名为空"
            if (root.isBlank()) lineErrors += "一级分类为空"

            if (lineErrors.isEmpty()) {
                rows += CsvRow(date, type, amount, account, root, child, note, cc)
            } else {
                errors += "第 $lineNo 行：" + lineErrors.joinToString("、")
            }
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            errors += "没有可导入的数据行"
        }
        return CsvParseResult(rows, errors)
    }

    private fun validDate(s: String): Boolean = try {
        LocalDate.parse(s)
        true
    } catch (e: Exception) {
        false
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it.code == QUOTE_CODE || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s

    /** 逐字符状态机：正确处理引号内的逗号、双引号转义与换行。 */
    private fun parseRows(text: String, delim: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row += field.toString()
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows += row.toList()
            row.clear()
        }

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch.code == QUOTE_CODE) {
                    if (i + 1 < text.length && text[i + 1].code == QUOTE_CODE) {
                        field.append(QUOTE_CODE.toChar())
                        i += 2
                        continue
                    }
                    inQuotes = false
                    i++
                    continue
                }
                field.append(ch)
                i++
                continue
            }
            when (ch) {
                QUOTE_CODE.toChar() -> { inQuotes = true; i++ }
                delim -> { endField(); i++ }
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow(); i++
                }
                '\n' -> { endRow(); i++ }
                else -> { field.append(ch); i++ }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
