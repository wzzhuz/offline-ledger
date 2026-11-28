package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType

/**
 * 第 30 批：预制账户行全部删除（首次启动零账户，改由快捷模板创建）。
 * 分类清单的**唯一可替换来源**是 assets/default_categories.json；
 * 本对象保留同构 fallback 与纯 JVM 结构校验，SeedCatalogTest 断言两者一致。
 * enabled=false 只在录入/筛选隐藏，统计、历史、CSV 导出仍读全量。
 * 第 51 批：ID 规则 = 一级 1..99 / 二级 父ID×1000+序号；第 90 批清单 v3（114 行）；AUTOINCREMENT 手工分类
 * 从 max+1 起且**永不回填空洞**。isPresetCategoryId 供删除策略判定（纯 JVM、零 Context）。
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
        c(1, "餐饮", RecordType.EXPENSE, null, 0, 1, true),
        c(1001, "正餐", RecordType.EXPENSE, 1L, 1, 1, true),
        c(1002, "外卖", RecordType.EXPENSE, 1L, 1, 2, true),
        c(1003, "聚餐", RecordType.EXPENSE, 1L, 1, 3, true),
        c(1004, "买菜做饭", RecordType.EXPENSE, 1L, 1, 4, true),
        c(1005, "零食", RecordType.EXPENSE, 1L, 1, 5, false),
        c(1006, "水果", RecordType.EXPENSE, 1L, 1, 6, false),
        c(1007, "饮品奶茶", RecordType.EXPENSE, 1L, 1, 7, false),
        c(1008, "咖啡", RecordType.EXPENSE, 1L, 1, 8, false),
        c(1009, "夜宵", RecordType.EXPENSE, 1L, 1, 9, false),
        c(1010, "烟酒", RecordType.EXPENSE, 1L, 1, 10, false),
        c(2, "交通", RecordType.EXPENSE, null, 0, 2, true),
        c(2001, "公共交通", RecordType.EXPENSE, 2L, 1, 1, true),
        c(2002, "打车", RecordType.EXPENSE, 2L, 1, 2, true),
        c(2003, "长途交通", RecordType.EXPENSE, 2L, 1, 3, true),
        c(2004, "共享单车", RecordType.EXPENSE, 2L, 1, 4, true),
        c(2005, "租车", RecordType.EXPENSE, 2L, 1, 5, false),
        c(2006, "代驾", RecordType.EXPENSE, 2L, 1, 6, false),
        c(2007, "轮渡船票", RecordType.EXPENSE, 2L, 1, 7, false),
        c(3, "养车", RecordType.EXPENSE, null, 0, 3, true),
        c(3001, "加油充电", RecordType.EXPENSE, 3L, 1, 1, true),
        c(3002, "停车", RecordType.EXPENSE, 3L, 1, 2, true),
        c(3003, "保险", RecordType.EXPENSE, 3L, 1, 3, true),
        c(3004, "保养维修", RecordType.EXPENSE, 3L, 1, 4, true),
        c(3005, "高速过路", RecordType.EXPENSE, 3L, 1, 5, true),
        c(3006, "洗车美容", RecordType.EXPENSE, 3L, 1, 6, true),
        c(3007, "年检上牌", RecordType.EXPENSE, 3L, 1, 7, false),
        c(3008, "违章罚款", RecordType.EXPENSE, 3L, 1, 8, false),
        c(3009, "车载用品", RecordType.EXPENSE, 3L, 1, 9, false),
        c(4, "居住", RecordType.EXPENSE, null, 0, 4, true),
        c(4001, "房租", RecordType.EXPENSE, 4L, 1, 1, true),
        c(4002, "水费", RecordType.EXPENSE, 4L, 1, 2, true),
        c(4003, "电费", RecordType.EXPENSE, 4L, 1, 3, true),
        c(4004, "燃气", RecordType.EXPENSE, 4L, 1, 4, true),
        c(4005, "物业", RecordType.EXPENSE, 4L, 1, 5, true),
        c(4006, "宽带", RecordType.EXPENSE, 4L, 1, 6, true),
        c(4008, "取暖费", RecordType.EXPENSE, 4L, 1, 7, false),
        c(4010, "房屋维修", RecordType.EXPENSE, 4L, 1, 8, false),
        c(11, "日常", RecordType.EXPENSE, null, 0, 5, true),
        c(11001, "理发美容", RecordType.EXPENSE, 11L, 1, 1, true),
        c(11002, "话费", RecordType.EXPENSE, 11L, 1, 2, true),
        c(11003, "快递邮寄", RecordType.EXPENSE, 11L, 1, 3, true),
        c(11004, "家政保洁", RecordType.EXPENSE, 11L, 1, 4, true),
        c(11005, "洗衣护理", RecordType.EXPENSE, 11L, 1, 5, false),
        c(11006, "上门维修", RecordType.EXPENSE, 11L, 1, 6, false),
        c(11007, "生活杂费", RecordType.EXPENSE, 11L, 1, 7, false),
        c(11008, "绿植鲜花", RecordType.EXPENSE, 11L, 1, 8, false),
        c(5, "购物", RecordType.EXPENSE, null, 0, 6, true),
        c(5001, "日用百货", RecordType.EXPENSE, 5L, 1, 1, true),
        c(5002, "服饰", RecordType.EXPENSE, 5L, 1, 2, true),
        c(5003, "数码", RecordType.EXPENSE, 5L, 1, 3, true),
        c(5004, "家居家纺", RecordType.EXPENSE, 5L, 1, 4, true),
        c(5005, "美妆护肤", RecordType.EXPENSE, 5L, 1, 5, false),
        c(5006, "箱包配饰", RecordType.EXPENSE, 5L, 1, 6, false),
        c(5007, "母婴用品", RecordType.EXPENSE, 5L, 1, 7, false),
        c(5008, "运动装备", RecordType.EXPENSE, 5L, 1, 8, false),
        c(5009, "家具家电", RecordType.EXPENSE, 5L, 1, 9, true),
        c(6, "医疗", RecordType.EXPENSE, null, 0, 7, true),
        c(6001, "门诊", RecordType.EXPENSE, 6L, 1, 1, true),
        c(6002, "药品", RecordType.EXPENSE, 6L, 1, 2, true),
        c(6003, "体检", RecordType.EXPENSE, 6L, 1, 3, true),
        c(6004, "牙科", RecordType.EXPENSE, 6L, 1, 4, true),
        c(6005, "住院", RecordType.EXPENSE, 6L, 1, 5, false),
        c(6006, "疫苗", RecordType.EXPENSE, 6L, 1, 6, false),
        c(6007, "眼镜助听", RecordType.EXPENSE, 6L, 1, 7, false),
        c(6008, "保健补剂", RecordType.EXPENSE, 6L, 1, 8, false),
        c(6009, "商业保险", RecordType.EXPENSE, 6L, 1, 9, false),
        c(7, "学习娱乐", RecordType.EXPENSE, null, 0, 8, false),
        c(7001, "课程书籍", RecordType.EXPENSE, 7L, 1, 1, false),
        c(7002, "影音会员", RecordType.EXPENSE, 7L, 1, 2, false),
        c(7003, "旅行", RecordType.EXPENSE, 7L, 1, 3, false),
        c(7004, "运动健身", RecordType.EXPENSE, 7L, 1, 4, false),
        c(7005, "游戏", RecordType.EXPENSE, 7L, 1, 5, false),
        c(7006, "演出展览", RecordType.EXPENSE, 7L, 1, 6, false),
        c(7007, "软件订阅", RecordType.EXPENSE, 7L, 1, 7, false),
        c(7008, "考证报名", RecordType.EXPENSE, 7L, 1, 8, false),
        c(8, "宠物", RecordType.EXPENSE, null, 0, 9, true),
        c(8001, "主粮零食", RecordType.EXPENSE, 8L, 1, 1, true),
        c(8002, "用品猫砂", RecordType.EXPENSE, 8L, 1, 2, true),
        c(8003, "医疗疫苗", RecordType.EXPENSE, 8L, 1, 3, true),
        c(8004, "洗护美容", RecordType.EXPENSE, 8L, 1, 4, true),
        c(8005, "寄养", RecordType.EXPENSE, 8L, 1, 5, true),
        c(8006, "宠物保险", RecordType.EXPENSE, 8L, 1, 6, true),
        c(8007, "宠物训练", RecordType.EXPENSE, 8L, 1, 7, true),
        c(9, "人情社交", RecordType.EXPENSE, null, 0, 10, false),
        c(9001, "礼金", RecordType.EXPENSE, 9L, 1, 1, false),
        c(9002, "红包", RecordType.EXPENSE, 9L, 1, 2, false),
        c(9003, "请客", RecordType.EXPENSE, 9L, 1, 3, false),
        c(9004, "赡养·代缴水电", RecordType.EXPENSE, 9L, 1, 4, false),
        c(9005, "礼物", RecordType.EXPENSE, 9L, 1, 5, false),
        c(9006, "捐赠公益", RecordType.EXPENSE, 9L, 1, 6, false),
        c(10, "其他", RecordType.EXPENSE, null, 0, 11, true),
        c(10001, "未分类", RecordType.EXPENSE, 10L, 1, 1, true),
        c(10002, "银行手续费", RecordType.EXPENSE, 10L, 1, 2, false),
        c(10003, "杂项待归类", RecordType.EXPENSE, 10L, 1, 3, false),
        c(30, "工资", RecordType.INCOME, null, 0, 12, true),
        c(30001, "月薪", RecordType.INCOME, 30L, 1, 1, true),
        c(30002, "加班补贴", RecordType.INCOME, 30L, 1, 2, false),
        c(30003, "年终奖", RecordType.INCOME, 30L, 1, 3, false),
        c(31, "奖金", RecordType.INCOME, null, 0, 13, true),
        c(31001, "绩效奖金", RecordType.INCOME, 31L, 1, 1, true),
        c(31002, "项目奖金", RecordType.INCOME, 31L, 1, 2, false),
        c(32, "兼职", RecordType.INCOME, null, 0, 14, true),
        c(32001, "劳务报酬", RecordType.INCOME, 32L, 1, 1, true),
        c(32002, "稿费", RecordType.INCOME, 32L, 1, 2, false),
        c(33, "理财收益", RecordType.INCOME, null, 0, 15, true),
        c(33001, "利息", RecordType.INCOME, 33L, 1, 1, true),
        c(33002, "分红", RecordType.INCOME, 33L, 1, 2, true),
        c(33003, "基金收益", RecordType.INCOME, 33L, 1, 3, false),
        c(34, "其他收入", RecordType.INCOME, null, 0, 16, true),
        c(34001, "其他", RecordType.INCOME, 34L, 1, 1, true),
        c(34002, "报销回款", RecordType.INCOME, 34L, 1, 2, false),
        c(34003, "退款", RecordType.INCOME, 34L, 1, 3, false),
        c(34004, "红包收入", RecordType.INCOME, 34L, 1, 4, false)
    )

    /** 预制 ID 集合：由 SeedCatalogTest 保证与随包 JSON 逐行一致，无需再读 assets。 */
    private val presetIds: Set<Long> = categories.map { it.id }.toSet()

    fun isPresetCategoryId(id: Long): Boolean = id in presetIds

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
            // 第 60 批：列清单补 quickOrder / cardKind；全新库与备份恢复走 Entity
            // 创建路径，缺列会直接 SQL 报错。虽不注册迁移，这两列仍 SHALL 对齐。
            out += "INSERT INTO accounts (id, name, type, initialBalanceCents, balanceCents, creditLimitCents, includeInTotal, isArchived, sortOrder, currencyCode, lastReconciledAt, lastReconciledBalanceCents, cardLast4, quickOrder, cardKind) " +
                "VALUES (${x.id}, '${x.name}', '${x.type.name}', 0, 0, $limit, 1, 0, " +
                // accounts 目前是 emptyList()（零账户首启），此处直接写默认常量，
                // MUST NOT 引用 AccountSeed 上不存在的字段导致编译失败。
                "${x.sortOrder}, 'CNY', NULL, NULL, NULL, 0, NULL)"
        }
        return out
    }
}
