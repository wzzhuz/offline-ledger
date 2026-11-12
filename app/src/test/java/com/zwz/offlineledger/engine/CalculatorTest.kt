package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CalculatorTest {

    @Test
    fun `operator precedence`() {
        assertEquals(BigDecimal("18.50"), Calculator.evaluate("12.5+3×2"))
        assertEquals(BigDecimal("18.50"), Calculator.evaluate("12.5+3*2"))
        assertEquals(BigDecimal("31.00"), Calculator.evaluate("(12.5+3)×2"))
    }

    @Test
    fun `division is rounded not infinite`() {
        assertEquals(BigDecimal("3.33"), Calculator.evaluate("10÷3"))
        assertEquals(BigDecimal("0.01"), Calculator.evaluate("0.03÷3"))
    }

    @Test
    fun `unary minus and decimals`() {
        assertEquals(BigDecimal("-3.50"), Calculator.evaluate("-3.5"))
        assertEquals(BigDecimal("0.01"), Calculator.evaluate("0.01"))
        assertEquals(BigDecimal("1.00"), Calculator.evaluate("0.1+0.2+0.7"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `divide by zero is rejected`() {
        Calculator.evaluate("1÷0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `illegal expression is rejected`() {
        Calculator.evaluate("1++")
    }

    @Test
    fun `keypad append and simplify`() {
        assertEquals("12.5+3×2", Calculator.append(Calculator.append("12.5+3", "×"), "2"))
        assertEquals("12.", Calculator.append("12.5", "⌫"))
        assertEquals("", Calculator.append("999", "C"))
        assertEquals("18.5", Calculator.simplify("12.5+3×2"))
    }

    @Test
    fun `no floating point in amount path`() {
        // BigDecimal 的 toPlainString 不含科学计数法，交给 Cents.fromYuan 可精确转分
        val cents = com.zwz.offlineledger.core.money.Cents
            .fromYuan(Calculator.evaluate("12.5+3×2").toPlainString())
        assertEquals(1850L, cents.value)
    }
}
