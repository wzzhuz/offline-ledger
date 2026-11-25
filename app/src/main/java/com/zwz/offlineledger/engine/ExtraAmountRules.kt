package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.RecordType

/**
 * 第 67 批：附加金额（优惠 / 手续费）唯一校验入口，纯 JVM。
 *
 * 口径来自一羽记账官方 CSV 手册：
 * - 「优惠」= 支出类型附加金额，必须为正数，且不能超过账单金额；
 * - 「手续费」= 转账类型附加金额，填写负数金额表示转账优惠。
 * 本 App 的 `amountCents` 恒为实际金额（实付），原价由整数加法推导。
 */
object ExtraAmountRules {

    /** @return null 表示合法；否则返回可直接展示的错误文案。 */
    fun validate(
        type: RecordType,
        amountCents: Long,
        discountCents: Long?,
        feeCents: Long?
    ): String? {
        if (discountCents != null && feeCents != null) {
            return "一笔流水不能同时有优惠与手续费"
        }
        if (type == RecordType.TRANSFER && discountCents != null) {
            return "优惠不适用于转账，请改用转账优惠（负手续费）"
        }
        if (type != RecordType.TRANSFER && feeCents != null) {
            return "手续费仅适用于转账"
        }
        if (type == RecordType.SETTLEMENT &&
            (discountCents != null || feeCents != null)
        ) {
            return "报销 / 退款不支持附加金额"
        }
        discountCents?.let { d ->
            if (d <= 0L) return "优惠金额必须大于 0"
            if (d > amountCents) return "优惠金额不能超过账单金额"
        }
        feeCents?.let { f ->
            if (f == 0L) return "手续费 / 转账优惠不能为 0"
            if (kotlin.math.abs(f) >= amountCents) {
                return "手续费 / 转账优惠不能大于等于转账金额"
            }
        }
        return null
    }

    /** 原价 = 实付 + 优惠；无优惠时返回 null（UI 不显示划线原价）。 */
    fun originalCents(amountCents: Long, discountCents: Long?): Long? =
        discountCents?.takeIf { it > 0L }?.let { amountCents + it }

    /** 转账实际扣款 = 金额 + 手续费（负 fee 即少扣）。 */
    fun transferDebitCents(amountCents: Long, feeCents: Long?): Long =
        amountCents + (feeCents ?: 0L)
}
