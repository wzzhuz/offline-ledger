package com.zwz.offlineledger.core.intake

/**
 * 第 86 批：文本诊断入口的**只读**分析器。
 *
 * 刻意不依赖任何 Android 类，便于纯 JVM 单测；Intent 抽取留在 Activity 侧。
 * 本批 MUST NOT 落库、MUST NOT 猜测金额，只把原文与命中情况摊开给人工检查。
 * 「达到门槛」是**后续批自动落库**的只读预演标签，本批不触发任何写操作。
 */
data class TextIntakePreview(
    val source: String,
    val rawText: String,
    val truncated: Boolean,
    val originalCharCount: Int,
    val amountCandidates: List<String>,
    val hasDirection: Boolean,
    val hasTime: Boolean,
    val hasCounterparty: Boolean,
    val readyForAutoCommit: Boolean,
)

object TextIntakeInspector {

    const val MAX_CHARS = 20_000

    private val AMOUNT = Regex("-?\\d[\\d,]*\\.\\d{2}")
    private val TIME = Regex("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}|\\d{1,2}:\\d{2}(?::\\d{2})?")
    private val DIRECTION = Regex("支出|收入|付款|收款|转账|支付成功|交易成功|已支付|付款成功|消费")
    private val COUNTERPARTY_LABEL = Regex("(?:商家|商户|收款方|交易对方|付款方|收款人|门店)\\s*[：:]?\\s*([^\\n\\r]{2,40})")
    private val MERCHANT_HINT = Regex("有限公司|有限责任公司|旗舰店|专营店|便利店|超市|酒店|餐厅|咖啡|药房|商店|工作室|服务部|经营部")

    /** @param source "process_text" | "send" | "empty" | "none" */
    fun from(source: String, text: String?): TextIntakePreview {
        val raw = text.orEmpty()
        val original = raw.length
        val truncated = original > MAX_CHARS
        val shown = if (truncated) raw.take(MAX_CHARS) else raw

        // 全量列出，不去重成一个「答案」；多个候选本身就是该降级手填的信号。
        val amounts = AMOUNT.findAll(shown).map { it.value }.distinct().toList()
        val hasDirection = DIRECTION.containsMatchIn(shown) || amounts.any { it.startsWith("-") }
        val hasTime = TIME.containsMatchIn(shown)
        val hasCounterparty = COUNTERPARTY_LABEL.containsMatchIn(shown) || MERCHANT_HINT.containsMatchIn(shown)

        val ready = !truncated && amounts.size == 1 &&
            hasDirection && hasTime && hasCounterparty

        return TextIntakePreview(
            source = source,
            rawText = shown,
            truncated = truncated,
            originalCharCount = original,
            amountCandidates = amounts,
            hasDirection = hasDirection,
            hasTime = hasTime,
            hasCounterparty = hasCounterparty,
            readyForAutoCommit = ready,
        )
    }
}
