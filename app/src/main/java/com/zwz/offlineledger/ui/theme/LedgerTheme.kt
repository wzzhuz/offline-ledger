package com.zwz.offlineledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 中文记账语境：红 = 支出 / 赤字，绿 = 收入。
 *
 * 阶段 5 的「配色反转」开关只交换这两个语义色：
 * 用 mutableStateOf 承载后，所有现有 LedgerColors.ExpenseRed / IncomeGreen
 * 引用点在 composition / draw phase 读取时会订阅并重组 / 重绘，
 * 不需要 CompositionLocal 重写 80+ 处引用。
 * Neutral / Primary 是中性色与品牌色，保持常量、不参与反转。
 */
object LedgerColors {
    private val RED = Color(0xFFC62828)
    private val GREEN = Color(0xFF2E7D32)

    private val _expenseRed = mutableStateOf(RED)
    var ExpenseRed: Color
        get() = _expenseRed.value
        private set(value) { _expenseRed.value = value }

    private val _incomeGreen = mutableStateOf(GREEN)
    var IncomeGreen: Color
        get() = _incomeGreen.value
        private set(value) { _incomeGreen.value = value }

    val Neutral = Color(0xFF616161)
    /**
     * 第 69 批（Q5B）：与 `CategoryHeaderBlue` 同系的清新蓝。
     * 原先深绿 `0xFF1B5E20` 让 M3 默认灰色卡面更显沉闷；
     * 全局改浅色体系后 primary 同步，保持单一真相源。
     * ExpenseRed / IncomeGreen 与反转开关一字不动。
     */
    val Primary = Color(0xFF5B7CFA)

    /** MainActivity.onCreate 调一次；偏好变化时也应再调。 */
    fun applyReverseColors(reversed: Boolean) {
        if (reversed) {
            ExpenseRed = GREEN
            IncomeGreen = RED
        } else {
            ExpenseRed = RED
            IncomeGreen = GREEN
        }
    }

    fun isReversed(): Boolean = ExpenseRed == GREEN
}

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LedgerColors.Primary,
            onPrimary = Color.White,
            secondary = LedgerColors.IncomeGreen,
            // 第 69 批：一羽式清新浅色体系。页面浅蓝灰底 + 纯白卡片，
            // 不再落 M3 默认 surfaceContainerLow 的灰面。只改色值，
            // 不动任何布局 / 手势 / 数据；红绿语义色与反转开关不受影响。
            background = Color(0xFFF4F6FB),
            onBackground = Color(0xFF1F2430),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1F2430),
            surfaceVariant = Color(0xFFEEF1F8),
            onSurfaceVariant = Color(0xFF6B7280),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFFBFBFE),
            surfaceContainerHigh = Color(0xFFF6F8FD),
            surfaceContainerHighest = Color(0xFFF1F4FA),
            outlineVariant = Color(0xFFDDE2EE)
        ),
        content = content
    )
}
