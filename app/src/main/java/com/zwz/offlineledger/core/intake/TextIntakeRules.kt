package com.zwz.offlineledger.core.intake

/** 人读的规则覆盖层：只解析四种「加一行词」指令，不解析正则或代码。 */
object TextIntakeRules {

    const val MAX_PATCH_CHARS = 4_000

    private val DEFAULT_NOISE = setOf(
        "账单管理", "我的消费图鉴", "贴纸", "支付奖励", "收单机构", "清算机构",
        "标签", "请选择", "为您推荐", "三餐", "买软件", "下馆子", "代付",
        "计入收支", "备注", "添加", "AA收款", "查看往来记录", "对此订单有疑问",
        "申请电子回单", "联系小程序客服", "查看购物详情", "查看扣款顺序",
        "自动扣款 免密支付", "管理自动扣款", "省团生活", "进入小程序", "服务详情",
        "使用记账本，查看自定义分类、标签统计", "商家可扫码退款或查询交易",
        "已解锁\"家居数码\"贴纸，去兑换好礼", "已解锁\"吃顿饭\"贴纸，去兑换好礼",
        "已解锁\"休闲娱乐\"贴纸，去兑换好礼", "去评价", "查看购物详情"
    )

    val DEFAULT = TextIntakeRuleSet(
        statusWords = setOf("交易成功", "自动扣款成功", "支付成功", "付款成功"),
        discountWords = setOf("红包", "立减", "券", "满减", "折扣", "优惠", "减免"),
        noiseLabels = DEFAULT_NOISE,
        categoryMap = mapOf("餐饮美食" to "餐饮"),
    )

    private val ADD_SPLIT = Regex("[,，、\\s]+")
    private val STATUS_ADD = Regex("^状态词\\s*\\+=\\s*(.+)$")
    private val DISCOUNT_ADD = Regex("^优惠词\\s*\\+=\\s*(.+)$")
    private val NOISE_ADD = Regex("^噪声\\s*\\+=\\s*(.+)$")
    private val CATEGORY_MAP = Regex("^分类\\s+(.+?)\\s*=\\s*(.+)$")

    fun withPatch(patch: String): TextIntakeRulePatchResult {
        val safe = patch.take(MAX_PATCH_CHARS)
        var status = DEFAULT.statusWords
        var discount = DEFAULT.discountWords
        var noise = DEFAULT.noiseLabels
        var category = DEFAULT.categoryMap
        val invalid = mutableListOf<String>()
        safe.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val s = STATUS_ADD.matchEntire(line)
            val d = DISCOUNT_ADD.matchEntire(line)
            val n = NOISE_ADD.matchEntire(line)
            val c = CATEGORY_MAP.matchEntire(line)
            when {
                s != null -> status = status + splitAdd(s.groupValues[1])
                d != null -> discount = discount + splitAdd(d.groupValues[1])
                n != null -> noise = noise + splitAdd(n.groupValues[1])
                c != null -> {
                    val from = c.groupValues[1].trim()
                    val to = c.groupValues[2].trim()
                    if (from.isNotBlank() && to.isNotBlank()) category = category + (from to to)
                    else invalid += line
                }
                else -> invalid += line
            }
        }
        return TextIntakeRulePatchResult(
            TextIntakeRuleSet(status, discount, noise, category),
            invalid,
        )
    }

    private fun splitAdd(value: String): List<String> =
        ADD_SPLIT.split(value.trim()).map { it.trim() }.filter { it.isNotBlank() }
}
