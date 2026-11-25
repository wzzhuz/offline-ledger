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
            // 第 70 批：按一羽图三把「浅蓝灰」改成**中性浅灰**，
            // 让纯白卡片真正跳出来、卡片之间边界清晰。
            // 第 69 批的 F4F6FB 太浅且偏蓝，与白卡对比不足；
            // 这里与项目既有 CategoryManagerDialog / AssetsScreen 的
            // F2F3F7 / F5F6FA 同系，保持全 App 一套灰。
            // 只改色值，不动布局 / 手势 / 数据；红绿语义与反转开关不受影响。
            background = Color(0xFFF2F3F7),
            onBackground = Color(0xFF1F2430),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1F2430),
            surfaceVariant = Color(0xFFE9EBF1),
            onSurfaceVariant = Color(0xFF6B7280),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFAFAFC),
            surfaceContainerHighest = Color(0xFFF5F6F9),
            outlineVariant = Color(0xFFE2E4EB)
        ),
        content = content
    )
}
