package com.zwz.offlineledger.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 所有金额的基础类型，单位「分」。
 *
 * 刻意不加 require(value >= 0)：流水 amount 恒正，但账户 balance 与
 * 校准 deltaCents 可为负，同一个类型要能表达两者。
 * 非负校验放在写流水的入口（阶段 1 实现）。
 */
@JvmInline
value class Cents(val value: Long) {

    /** 展示用：转回「元」的两位小数字符串。 */
    fun toYuanString(): String =
        BigDecimal.valueOf(value)
            .movePointLeft(2)
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString()

    operator fun plus(other: Cents): Cents = Cents(value + other.value)
    operator fun minus(other: Cents): Cents = Cents(value - other.value)

    companion object {
        private val YUAN_SCALE = 2

        /**
         * 用户输入（元）→ 整数分。
         * 用 longValueExact 而非 toLong：溢出时抛异常，不静默截断成错误金额。
         */
        fun fromYuan(input: String): Cents {
            require(input.isNotBlank()) { "金额不能为空" }
            val decimal = try {
                BigDecimal(input.trim()).setScale(YUAN_SCALE, RoundingMode.HALF_UP)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("金额格式非法：$input", e)
            }
            return try {
                Cents(decimal.movePointRight(YUAN_SCALE).longValueExact())
            } catch (e: ArithmeticException) {
                throw IllegalArgumentException("金额超出可表示范围：$input", e)
            }
        }
    }
}
