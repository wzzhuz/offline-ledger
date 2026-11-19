package com.zwz.offlineledger.backup.csv

/**
 * 第 35 批：自有 CSV 重建的「缺项判定」，纯 JVM、零 Room 依赖。
 *
 * 旧路径 importRecords 遇到第一个不存在的账户就抛
 * IllegalArgumentException("账户不存在或已归档")，整批回滚；
 * 而自有 CSV 不带账户表 / 分类树，清库或重装后必然失败。
 * 这里改成一次性收集全部缺失项，只跳过受影响的行。
 *
 * 归档账户与停用分类由调用方一并放进已知集合：它们只是「不再新选」，
 * 历史归属仍然有效，MUST NOT 当成缺失。
 */
data class CsvRebuildResolution(
    val ready: List<CsvRow>,
    val missingAccounts: List<String>,
    val missingCategories: List<String>,
    val skippedMissing: Int
)

object CsvRebuildResolver {
    /** categoryPaths 元素形如 "居住" 或 "居住/水费"，含归档与停用分类。 */
    fun resolve(
        rows: List<CsvRow>,
        accountNames: Set<String>,
        categoryPaths: Set<String>
    ): CsvRebuildResolution {
        val ready = mutableListOf<CsvRow>()
        val missAccounts = linkedSetOf<String>()
        val missCategories = linkedSetOf<String>()
        var skipped = 0
        rows.forEach { row ->
            val accountOk = row.accountName in accountNames
            val path = if (row.category.isBlank()) row.categoryRoot
                       else row.categoryRoot + "/" + row.category
            val categoryOk = path in categoryPaths
            if (!accountOk) missAccounts += row.accountName
            if (!categoryOk) missCategories += path
            if (accountOk && categoryOk) ready += row else skipped++
        }
        return CsvRebuildResolution(
            ready, missAccounts.toList(), missCategories.toList(), skipped
        )
    }
}
