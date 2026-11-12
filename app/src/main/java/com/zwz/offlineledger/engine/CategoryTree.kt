package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType

/** 两级分类解析：流水冗余 rootCategoryId，让一级/二级统计各一条 GROUP BY。 */
object CategoryTree {

    fun resolveRootId(categoryId: Long, categories: Map<Long, LedgerCategory>): Long {
        val c = categories[categoryId]
            ?: throw IllegalArgumentException("分类不存在：$categoryId")
        require(c.level == 0 || c.level == 1) { "最多两级分类，level=${c.level}" }
        return if (c.level == 0) c.id else (c.parentId ?: c.id)
    }

    /** 二级分类继承父级 type，且不允许 TRANSFER 分类。 */
    fun validate(categories: List<LedgerCategory>) {
        val byId = categories.associateBy { it.id }
        for (c in categories) {
            require(c.type != RecordType.TRANSFER) { "分类不能是 TRANSFER：${c.name}" }
            if (c.level == 1) {
                val parent = c.parentId?.let { byId[it] }
                    ?: throw IllegalArgumentException("二级分类缺少父级：${c.name}")
                require(parent.level == 0) { "父级必须是一级分类：${c.name}" }
                require(parent.type == c.type) { "二级分类类型必须与父级一致：${c.name}" }
            } else {
                require(c.level == 0) { "不支持三级及以上分类：${c.name}" }
            }
        }
    }

    fun sumByRoot(records: List<Pair<Long, Long>>): Map<Long, Long> =
        records.groupBy { it.first }
            .mapValues { (_, list) -> list.sumOf { it.second } }
}
