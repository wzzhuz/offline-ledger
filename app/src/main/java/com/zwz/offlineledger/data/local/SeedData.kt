package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType

/**
 * 预置数据定义在纯 Kotlin 对象里（不引用 SupportSQLiteDatabase），
 * 因此 SeedDataTest 可以在纯 JVM 下验证结构，不需要模拟器。
 */
object SeedData {

    data class CategorySeed(
        val id: Long, val name: String, val type: RecordType,
        val parentId: Long?, val level: Int,
        val iconName: String, val colorHex: String, val sortOrder: Int
    )

    data class AccountSeed(
        val id: Long, val name: String, val type: AccountType,
        val creditLimitCents: Long?, val sortOrder: Int
    )

    private fun c(id: Long, name: String, type: RecordType, parent: Long?,
                  level: Int, icon: String, color: String, order: Int) =
        CategorySeed(id, name, type, parent, level, icon, color, order)

    val categories: List<CategorySeed> = listOf(
        c(1, "餐饮", RecordType.EXPENSE, null, 0, "Restaurant", "#FF7043", 1),
        c(2, "交通", RecordType.EXPENSE, null, 0, "DirectionsBus", "#42A5F5", 2),
        c(3, "购物", RecordType.EXPENSE, null, 0, "ShoppingBag", "#AB47BC", 3),
        c(4, "居住", RecordType.EXPENSE, null, 0, "Home", "#26A69A", 4),
        c(5, "娱乐", RecordType.EXPENSE, null, 0, "Movie", "#EC407A", 5),
        c(6, "医疗", RecordType.EXPENSE, null, 0, "LocalHospital", "#EF5350", 6),
        c(7, "其他支出", RecordType.EXPENSE, null, 0, "MoreHoriz", "#78909C", 7),
        c(8, "工资", RecordType.INCOME, null, 0, "Payments", "#66BB6A", 8),
        c(9, "理财", RecordType.INCOME, null, 0, "TrendingUp", "#9CCC65", 9),
        c(10, "其他收入", RecordType.INCOME, null, 0, "MoreHoriz", "#78909C", 10),

        c(101, "食材", RecordType.EXPENSE, 1L, 1, "ShoppingCart", "#FF8A65", 1),
        c(102, "正餐", RecordType.EXPENSE, 1L, 1, "Restaurant", "#FF8A65", 2),
        c(103, "外卖", RecordType.EXPENSE, 1L, 1, "DeliveryDining", "#FF8A65", 3),
        c(104, "水果", RecordType.EXPENSE, 1L, 1, "Eco", "#FF8A65", 4),
        c(105, "饮品", RecordType.EXPENSE, 1L, 1, "LocalCafe", "#FF8A65", 5),
        c(201, "公共交通", RecordType.EXPENSE, 2L, 1, "Subway", "#64B5F6", 1),
        c(202, "打车", RecordType.EXPENSE, 2L, 1, "LocalTaxi", "#64B5F6", 2),
        c(203, "加油", RecordType.EXPENSE, 2L, 1, "LocalGasStation", "#64B5F6", 3),
        c(204, "停车", RecordType.EXPENSE, 2L, 1, "LocalParking", "#64B5F6", 4),
        c(301, "日用品", RecordType.EXPENSE, 3L, 1, "Soap", "#BA68C8", 1),
        c(302, "服饰", RecordType.EXPENSE, 3L, 1, "Checkroom", "#BA68C8", 2),
        c(303, "数码", RecordType.EXPENSE, 3L, 1, "Devices", "#BA68C8", 3),
        c(401, "房租", RecordType.EXPENSE, 4L, 1, "Apartment", "#4DB6AC", 1),
        c(402, "水电", RecordType.EXPENSE, 4L, 1, "Bolt", "#4DB6AC", 2),
        c(403, "物业", RecordType.EXPENSE, 4L, 1, "Domain", "#4DB6AC", 3),
        c(801, "月薪", RecordType.INCOME, 8L, 1, "Badge", "#81C784", 1),
        c(802, "奖金", RecordType.INCOME, 8L, 1, "CardGiftcard", "#81C784", 2),
        c(901, "利息", RecordType.INCOME, 9L, 1, "Savings", "#AED581", 1),
        c(902, "分红", RecordType.INCOME, 9L, 1, "PieChart", "#AED581", 2)
    )

    val accounts: List<AccountSeed> = listOf(
        AccountSeed(1, "微信零钱", AccountType.ASSET, null, 1),
        AccountSeed(2, "支付宝", AccountType.ASSET, null, 2),
        AccountSeed(3, "招行储蓄卡", AccountType.ASSET, null, 3),
        AccountSeed(4, "招行信用卡", AccountType.LIABILITY, 2_000_000L, 4)
    )

    fun asLedgerCategories(): List<LedgerCategory> =
        categories.map { LedgerCategory(it.id, it.name, it.type, it.parentId, it.level) }

    /** 用内嵌字面量而非 bindArgs：预置数据由本文件定义，无外部输入，不存在注入面。 */
    fun allInsertStatements(): List<String> {
        val out = mutableListOf<String>()
        for (x in categories) {
            val parent = x.parentId?.toString() ?: "NULL"
            out += "INSERT INTO categories (id, name, type, parentId, level, iconName, colorHex, sortOrder, isArchived) " +
                "VALUES (${x.id}, '${x.name}', '${x.type.name}', $parent, ${x.level}, " +
                "'${x.iconName}', '${x.colorHex}', ${x.sortOrder}, 0)"
        }
        for (x in accounts) {
            val limit = x.creditLimitCents?.toString() ?: "NULL"
            out += "INSERT INTO accounts (id, name, type, initialBalanceCents, balanceCents, creditLimitCents, includeInTotal, isArchived, sortOrder, currencyCode, lastReconciledAt, lastReconciledBalanceCents) " +
                "VALUES (${x.id}, '${x.name}', '${x.type.name}', 0, 0, $limit, 1, 0, " +
                "${x.sortOrder}, 'HKD', NULL, NULL)"
        }
        return out
    }
}
