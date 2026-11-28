package com.zwz.offlineledger.core.prefs

/**
 * 第 94 批：虚拟账本从「礼金专用 UI」改成「通用容器 + 场景模板」。
 *
 * 纯 JVM、零 Android 依赖、零新增依赖，可直接跑 JVM 单测。
 * 模板只决定**录入按钮文案、预填分类、汇总词**，MUST NOT 改变：
 * 记账仍落主账本、不带录入归属、台账记录不计入收支、零 bookId。
 *
 * 分类 id 指向预置清单里的停用项也可：保存校验只要求「分类存在 + 收支类型一致」，
 * 不要求 enabled。这样礼金两条停用分类无需改清单 / 改单测 / 卸载重装即可自动预填。
 */
enum class VirtualBookTemplate(
    val id: String,
    val label: String,
    /** null = 该模板不提供这个方向的录入按钮（专项支出只给一个「记支出」）。 */
    val incomeAction: String?,
    val expenseAction: String?,
    /** 礼金模板内置的预填分类；其余模板用 VirtualBook.defaultCategoryId。 */
    val incomeCategoryId: Long?,
    val expenseCategoryId: Long?,
    val incomeWord: String,
    val expenseWord: String,
    val netWord: String,
    val segmentLabel: String,
    val hint: String
) {
    GIFT(
        id = "gift",
        label = "人情往来",
        incomeAction = "记收礼",
        expenseAction = "记还礼",
        incomeCategoryId = 34_004L, // 其他收入 / 红包收入
        expenseCategoryId = 9_001L, // 人情社交 / 礼金
        incomeWord = "收",
        expenseWord = "还",
        netWord = "待还",
        segmentLabel = "往来汇总",
        hint = "收礼 / 还礼按人汇总；分类自动带入礼金与红包收入，不用每笔再选。"
    ),
    PROJECT(
        id = "project",
        label = "专项支出",
        incomeAction = null,
        expenseAction = "记支出",
        incomeCategoryId = null,
        expenseCategoryId = null,
        incomeWord = "收入",
        expenseWord = "支出",
        netWord = "净支出",
        segmentLabel = "汇总",
        hint = "装修 / 旅行等专项核算；在下方设一次默认分类，之后每笔自动带入。"
    ),
    CUSTOM(
        id = "custom",
        label = "自定义",
        incomeAction = "记收入",
        expenseAction = "记支出",
        incomeCategoryId = null,
        expenseCategoryId = null,
        incomeWord = "收入",
        expenseWord = "支出",
        netWord = "净额",
        segmentLabel = "汇总",
        hint = "自定义双向台账；可在下方设默认分类，留空则在记一笔里选一次。"
    );

    /** 该模板下某个方向应该预填的分类 id；专项/自定义用视图自己的默认分类。 */
    fun categoryFor(typeIncome: Boolean, defaultCategoryId: Long?): Long? =
        if (typeIncome) incomeCategoryId ?: defaultCategoryId
        else expenseCategoryId ?: defaultCategoryId

    /** 设置页选默认分类时的收支类型过滤：null = 不限制。 */
    val categoryTypeFilter: String? get() = when (this) {
        GIFT -> null      // 礼金不需要用户选分类
        PROJECT -> "EXPENSE"
        CUSTOM -> null
    }

    companion object {
        /** 旧数据（第 91～93 批只有 5 段）统一按礼金模板解码，行为不变。 */
        const val LEGACY_ID: String = "gift"

        fun fromId(raw: String?): VirtualBookTemplate =
            entries.firstOrNull { it.id == raw } ?: GIFT
    }
}
