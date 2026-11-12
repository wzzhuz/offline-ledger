package com.zwz.offlineledger.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 记一笔内置计算器的表达式求值。
 *
 * 全程 BigDecimal，禁止 Float / Double 参与金额计算。
 * 除法用 DIV_SCALE 位中间精度防止无限小数，最终统一 setScale(2, HALF_UP)。
 */
object Calculator {

    private const val DIV_SCALE = 8
    private val ZERO = BigDecimal.ZERO

    fun evaluate(expression: String): BigDecimal {
        val normalized = expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace('–', '-')
            .replace(",", "")
            .trim()
        require(normalized.isNotEmpty()) { "表达式为空" }
        val lexer = Lexer(normalized)
        val value = parseExpression(lexer)
        require(lexer.atEnd()) { "表达式非法：$expression" }
        return value.setScale(2, RoundingMode.HALF_UP)
    }

    /** 键盘输入；"=" 在此处只做追加，求值由 ViewModel 在保存 / 按等号时调用。 */
    fun append(current: String, key: String): String = when (key) {
        "C" -> ""
        "⌫" -> if (current.isEmpty()) "" else current.dropLast(1)
        else -> current + key
    }

    /** 把当前表达式化简为结果字符串，非法时返回原表达式。 */
    fun simplify(current: String): String = try {
        evaluate(current).stripTrailingZeros().toPlainString()
    } catch (e: IllegalArgumentException) {
        current
    }

    private fun parseExpression(l: Lexer): BigDecimal {
        var acc = parseTerm(l)
        while (true) {
            when (l.peek()) {
                '+' -> { l.take(); acc = acc.add(parseTerm(l)) }
                '-' -> { l.take(); acc = acc.subtract(parseTerm(l)) }
                else -> return acc
            }
        }
    }

    private fun parseTerm(l: Lexer): BigDecimal {
        var acc = parseFactor(l)
        while (true) {
            when (l.peek()) {
                '*' -> { l.take(); acc = acc.multiply(parseFactor(l)) }
                '/' -> {
                    l.take()
                    val divisor = parseFactor(l)
                    require(divisor.compareTo(ZERO) != 0) { "除数不能为 0" }
                    acc = acc.divide(divisor, DIV_SCALE, RoundingMode.HALF_UP)
                }
                else -> return acc
            }
        }
    }

    private fun parseFactor(l: Lexer): BigDecimal {
        val c = l.peek() ?: throw IllegalArgumentException("表达式不完整")
        return when (c) {
            '+' -> { l.take(); parseFactor(l) }
            '-' -> { l.take(); parseFactor(l).negate() }
            '(' -> {
                l.take()
                val v = parseExpression(l)
                require(l.peek() == ')') { "缺少右括号" }
                l.take()
                v
            }
            else -> {
                require(c.isDigit() || c == '.') { "非法字符：$c" }
                l.readNumber()
            }
        }
    }

    private class Lexer(text: String) {
        private val chars = text.toCharArray()
        private var i = 0

        private fun skipSpaces() {
            while (i < chars.size && chars[i].isWhitespace()) i++
        }

        fun peek(): Char? {
            skipSpaces()
            return chars.getOrNull(i)
        }

        fun take(): Char {
            skipSpaces()
            return chars[i++]
        }

        fun atEnd(): Boolean {
            skipSpaces()
            return i >= chars.size
        }

        fun readNumber(): BigDecimal {
            val start = i
            var dots = 0
            while (i < chars.size && (chars[i].isDigit() || chars[i] == '.')) {
                if (chars[i] == '.') {
                    dots++
                    require(dots <= 1) { "小数点重复" }
                }
                i++
            }
            require(i > start) { "缺少数字" }
            return BigDecimal(chars.concatToString(start, i))
        }
    }
}
