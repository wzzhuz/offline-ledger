package com.zwz.offlineledger.backup.csv

/**
 * 与 Room Entity 完全分离的 CSV 行模型，全部字段为「导出后人类可读」的字符串。
 * 导出与导入互为镜像；纯 JVM，无 Context / Room 依赖。
 */
data class CsvRow(
    val date: String,
    val type: String,
    val amountYuan: String,
    val accountName: String,
    val categoryRoot: String,
    val category: String,
    val note: String,
    val currencyCode: String,
    /** 阶段 7：收款方。带默认值，旧 8 列 CSV 仍能解析成空串。 */
    val counterparty: String = ""
) {
    /**
     * 导入去重键 = 账户 + 金额 + 日期 + 备注。
     * **刻意不含 currencyCode**：币种一律从账户继承，
     * 文件里写错币种不该让同一笔账被当成新流水重复导入。
     */
    fun duplicateKey(): String = "$accountName|$amountYuan|$date|$note"
}

/** 全量解析结果：errors 非空时调用方 MUST NOT 进入写入事务。 */
data class CsvParseResult(
    val rows: List<CsvRow>,
    val errors: List<String>
) {
    val ok: Boolean get() = errors.isEmpty()
}

/** 事务化导入的执行结果。 */
data class CsvImportOutcome(val written: Int, val skipped: Int)
