package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType

/**
 * 第 30 批：预制账户行全部删除（首次启动零账户，改由快捷模板创建）。
 * 分类清单的**唯一可替换来源**是 assets/default_categories.json；
 * 本对象保留同构 fallback 与纯 JVM 结构校验，SeedCatalogTest 断言两者一致。
 * enabled=false 只在录入/筛选隐藏，统计、历史、CSV 导出仍读全量。
 */
object SeedData {

    data class CategorySeed(
        val id: Long, val name: String, val type: RecordType,
        val parentId: Long?, val level: Int,
        val iconName: String, val colorHex: String, val sortOrder: Int,
        val enabled: Boolean = true
    )

    data class AccountSeed(
        val id: Long, val name: String, val type: AccountType,
        val creditLimitCents: Long?, val sortOrder: Int
    )

    private fun c(id: Long, name: String, type: RecordType, parent: Long?,
                  level: Int, order: Int, on: Boolean) =
        CategorySeed(id, name, type, parent, level, "", "#888888", order, on)

    val categories: List<CategorySeed> = listOf(
        c(1, "三餐", RecordType.EXPENSE, null, 0, 1, true),
        c(2, "出行", RecordType.EXPENSE, null, 0, 2, true),
        c(3, "养车", RecordType.EXPENSE, null, 0, 3, false),
        c(4, "居住", RecordType.EXPENSE, null, 0, 4, true),
        c(5, "宠物", RecordType.EXPENSE, null, 0, 5, false),
        c(6, "日用购物", RecordType.EXPENSE, null, 0, 6, true),
        c(7, "医疗", RecordType.EXPENSE, null, 0, 7, true),
        c(8, "教育娱乐", RecordType.EXPENSE, null, 0, 8, false),
        c(9, "人情", RecordType.EXPENSE, null, 0, 9, false),
        c(10, "其他", RecordType.EXPENSE, null, 0, 10, true),
        c(11, "工资", RecordType.INCOME, null, 0, 11, true),
        c(12, "奖金", RecordType.INCOME, null, 0, 12, true),
        c(13, "兼职", RecordType.INCOME, null, 0, 13, true),
        c(14, "理财收益", RecordType.INCOME, null, 0, 14, true),
        c(15, "其他收入", RecordType.INCOME, null, 0, 15, true),
        c(101, "早餐", RecordType.EXPENSE, 1L, 1, 1, true),
        c(102, "外卖", RecordType.EXPENSE, 1L, 1, 2, true),
        c(103, "聚餐", RecordType.EXPENSE, 1L, 1, 3, true),
        c(104, "买菜做饭", RecordType.EXPENSE, 1L, 1, 4, true),
        c(201, "地铁公交", RecordType.EXPENSE, 2L, 1, 1, true),
        c(202, "打车", RecordType.EXPENSE, 2L, 1, 2, true),
        c(203, "火车飞机", RecordType.EXPENSE, 2L, 1, 3, true),
        c(204, "共享单车", RecordType.EXPENSE, 2L, 1, 4, true),
        c(301, "加油充电", RecordType.EXPENSE, 3L, 1, 1, true),
        c(302, "停车", RecordType.EXPENSE, 3L, 1, 2, true),
        c(303, "保险", RecordType.EXPENSE, 3L, 1, 3, true),
        c(304, "保养维修", RecordType.EXPENSE, 3L, 1, 4, true),
        c(305, "高速过路", RecordType.EXPENSE, 3L, 1, 5, true),
        c(306, "洗车美容", RecordType.EXPENSE, 3L, 1, 6, true),
        c(401, "房租", RecordType.EXPENSE, 4L, 1, 1, true),
        c(402, "水费", RecordType.EXPENSE, 4L, 1, 2, true),
        c(403, "电费", RecordType.EXPENSE, 4L, 1, 3, true),
        c(404, "燃气", RecordType.EXPENSE, 4L, 1, 4, true),
        c(405, "物业", RecordType.EXPENSE, 4L, 1, 5, true),
        c(406, "宽带", RecordType.EXPENSE, 4L, 1, 6, true),
        c(501, "主粮零食", RecordType.EXPENSE, 5L, 1, 1, true),
        c(502, "用品猫砂", RecordType.EXPENSE, 5L, 1, 2, true),
        c(503, "医疗疫苗", RecordType.EXPENSE, 5L, 1, 3, true),
        c(504, "洗护美容", RecordType.EXPENSE, 5L, 1, 4, true),
        c(505, "寄养", RecordType.EXPENSE, 5L, 1, 5, true),
        c(601, "日用百货", RecordType.EXPENSE, 6L, 1, 1, true),
        c(602, "服饰", RecordType.EXPENSE, 6L, 1, 2, true),
        c(603, "数码", RecordType.EXPENSE, 6L, 1, 3, true),
        c(604, "家居", RecordType.EXPENSE, 6L, 1, 4, true),
        c(701, "门诊", RecordType.EXPENSE, 7L, 1, 1, true),
        c(702, "药品", RecordType.EXPENSE, 7L, 1, 2, true),
        c(703, "体检", RecordType.EXPENSE, 7L, 1, 3, true),
        c(704, "牙科", RecordType.EXPENSE, 7L, 1, 4, true),
        c(801, "课程书籍", RecordType.EXPENSE, 8L, 1, 1, true),
        c(802, "影音会员", RecordType.EXPENSE, 8L, 1, 2, true),
        c(803, "旅行", RecordType.EXPENSE, 8L, 1, 3, true),
        c(804, "运动健身", RecordType.EXPENSE, 8L, 1, 4, true),
        c(901, "礼金", RecordType.EXPENSE, 9L, 1, 1, true),
        c(902, "红包", RecordType.EXPENSE, 9L, 1, 2, true),
        c(903, "请客", RecordType.EXPENSE, 9L, 1, 3, true),
        c(904, "赡养·代缴水电", RecordType.EXPENSE, 9L, 1, 4, true),
        c(1001, "未分类", RecordType.EXPENSE, 10L, 1, 1, true),
        c(1101, "月薪", RecordType.INCOME, 11L, 1, 1, true),
        c(1201, "绩效奖金", RecordType.INCOME, 12L, 1, 1, true),
        c(1301, "劳务报酬", RecordType.INCOME, 13L, 1, 1, true),
        c(1401, "利息", RecordType.INCOME, 14L, 1, 1, true),
        c(1402, "分红", RecordType.INCOME, 14L, 1, 2, true),
        c(1501, "其他", RecordType.INCOME, 15L, 1, 1, true)
    )

    /** 预制账户已删除：零账户首启由资产页快捷模板创建。 */
    val accounts: List<AccountSeed> = emptyList()

    fun asLedgerCategories(): List<LedgerCategory> =
        categories.map { LedgerCategory(it.id, it.name, it.type, it.parentId, it.level) }

    /** 用内嵌字面量而非 bindArgs：预置数据由本文件定义，无外部输入，不存在注入面。 */
    fun allInsertStatements(): List<String> {
        val out = mutableListOf<String>()
        for (x in categories) {
            val parent = x.parentId?.toString() ?: "NULL"
            out += "INSERT INTO categories (id, name, type, parentId, level, iconName, colorHex, sortOrder, isArchived, enabled) " +
                "VALUES (${x.id}, '${x.name}', '${x.type.name}', $parent, ${x.level}, " +
                "'${x.iconName}', '${x.colorHex}', ${x.sortOrder}, 0, ${if (x.enabled) 1 else 0})"
        }
        for (x in accounts) {
            val limit = x.creditLimitCents?.toString() ?: "NULL"
            out += "INSERT INTO accounts (id, name, type, initialBalanceCents, balanceCents, creditLimitCents, includeInTotal, isArchived, sortOrder, currencyCode, lastReconciledAt, lastReconciledBalanceCents, cardLast4) " +
                "VALUES (${x.id}, '${x.name}', '${x.type.name}', 0, 0, $limit, 1, 0, " +
                "${x.sortOrder}, 'CNY', NULL, NULL, NULL)"
        }
        return out
    }
}
