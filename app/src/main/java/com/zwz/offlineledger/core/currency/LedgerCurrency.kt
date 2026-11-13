package com.zwz.offlineledger.core.currency

/**
 * 使用者的账本默认币种。真机体验反馈：使用者在中国大陆，应为 CNY。
 * 早期版本按「用户在香港」的错误假设写死 HKD，本批次修正。
 * 默认币种可在设置页修改；该值只影响新建账户，不自动改已有数据。
 */
object LedgerCurrency {
    const val DEFAULT: String = "CNY"

    /** 设置页下拉的可选项；不含汇率折算，币种只是标签。 */
    val SUPPORTED: List<String> = listOf(
        "CNY", "HKD", "USD", "EUR", "JPY", "GBP", "TWD", "SGD", "AUD", "CAD"
    )
}
