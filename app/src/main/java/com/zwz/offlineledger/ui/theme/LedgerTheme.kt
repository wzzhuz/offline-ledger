package com.zwz.offlineledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 中文记账语境：红 = 支出 / 赤字，绿 = 收入。
 *
 * 「配色反转」开关只交换这两个语义色：用 mutableStateOf 承载后，所有现有
 * ExpenseRed / IncomeGreen 引用点在 composition / draw phase 读取时会订阅并
 * 重组 / 重绘，不需要 CompositionLocal 重写 80+ 处引用。
 *
 * 第 78 批：Neutral / Primary / Surface 等中性色与表面色也从常量升为可观察状态，
 * 由 LedgerTheme 在一次 SideEffect 里同步当前 ColorScheme。这样三件事同时成立：
 *   ① 深色模式下硬编码的浅底 / 白卡不再刺眼；
 *   ② 分类管理蓝底顶栏的 CategoryHeaderBlue 与 colorScheme.primary 不再两套蓝；
 *   ③ Canvas draw scope、普通函数等非 Composable 上下文仍能直接读到当前颜色。
 * 红绿语义与反转开关不受影响，反转状态在深浅切换后仍保留。
 */
object LedgerColors {
    private val RED = Color(0xFFC62828)
    private val GREEN = Color(0xFF2E7D32)
    // 深色底上原深红 / 深绿对比不足，改用同语义的柔和珊瑚红 / 柔和绿。
    private val DARK_RED = Color(0xFFF0A6A0)
    private val DARK_GREEN = Color(0xFF9CCFA6)

    private val _expenseRed = mutableStateOf(RED)
    var ExpenseRed: Color
        get() = _expenseRed.value
        private set(value) { _expenseRed.value = value }

    private val _incomeGreen = mutableStateOf(GREEN)
    var IncomeGreen: Color
        get() = _incomeGreen.value
        private set(value) { _incomeGreen.value = value }

    private val _neutral = mutableStateOf(Color(0xFF6B7280))
    var Neutral: Color
        get() = _neutral.value
        private set(value) { _neutral.value = value }

    private val _primary = mutableStateOf(Color(0xFF5B7CFA))
    var Primary: Color
        get() = _primary.value
        private set(value) { _primary.value = value }

    private val _onPrimary = mutableStateOf(Color.White)
    var OnPrimary: Color
        get() = _onPrimary.value
        private set(value) { _onPrimary.value = value }

    /** 卡片 / 弹层背景。浅色纯白，深色为 #1E2025，避免纯黑纯白。 */
    private val _surface = mutableStateOf(Color(0xFFFFFFFF))
    var Surface: Color
        get() = _surface.value
        private set(value) { _surface.value = value }

    /** 次级底 / 进度条轨道 / 分组底色。 */
    private val _surfaceVariant = mutableStateOf(Color(0xFFE9EBF1))
    var SurfaceVariant: Color
        get() = _surfaceVariant.value
        private set(value) { _surfaceVariant.value = value }

    /** 比 Surface 略高一级的容器色（折叠区 / 拖拽态）。 */
    private val _surfaceHigh = mutableStateOf(Color(0xFFFAFAFC))
    var SurfaceHigh: Color
        get() = _surfaceHigh.value
        private set(value) { _surfaceHigh.value = value }

    private val _outlineVariant = mutableStateOf(Color(0xFFE2E4EB))
    var OutlineVariant: Color
        get() = _outlineVariant.value
        private set(value) { _outlineVariant.value = value }

    private var dark = false
    private var reversed = false

    /** MainActivity.onCreate 调一次；偏好变化时也应再调。 */
    fun applyReverseColors(flag: Boolean) {
        reversed = flag
        applySemantic()
    }

    fun isReversed(): Boolean = reversed

    private fun applySemantic() {
        val expense = if (dark) DARK_RED else RED
        val income = if (dark) DARK_GREEN else GREEN
        _expenseRed.value = if (reversed) income else expense
        _incomeGreen.value = if (reversed) expense else income
    }

    /** LedgerTheme 每次成功组合后调用；写相同值不触发重组，不会形成循环。 */
    internal fun syncFrom(scheme: ColorScheme, darkTheme: Boolean) {
        dark = darkTheme
        _neutral.value = scheme.onSurfaceVariant
        _primary.value = scheme.primary
        _onPrimary.value = scheme.onPrimary
        _surface.value = scheme.surface
        _surfaceVariant.value = scheme.surfaceVariant
        _surfaceHigh.value = scheme.surfaceContainerHigh
        _outlineVariant.value = scheme.outlineVariant
        applySemantic()
    }
}

/** 第 70 批「浅灰底 / 纯白卡 / 清新蓝」静态板，也是 API ≤ 30 的回退路径。 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B7CFA),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    // 中性浅灰底让纯白卡片真正跳出来、卡片之间边界清晰。
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
)

/**
 * 第 78 批深色静态板：深灰蓝底 + 略亮卡片，避免纯黑纯白。
 * primary 刻意保持与浅色同一支蓝、onPrimary 保持纯白 —— 资产页 / 分类管理
 * 蓝底顶栏上的白字无需逐个改色，深色下依然可读。
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5B7CFA),
    onPrimary = Color.White,
    secondary = Color(0xFF9CCFA6),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1E2025),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF2A2E36),
    onSurfaceVariant = Color(0xFFA9B0BC),
    surfaceContainerLowest = Color(0xFF15171B),
    surfaceContainerLow = Color(0xFF1E2025),
    surfaceContainer = Color(0xFF22252B),
    surfaceContainerHigh = Color(0xFF2A2E36),
    surfaceContainerHighest = Color(0xFF333842),
    outlineVariant = Color(0xFF3A3F49)
)

/**
 * 第 77 批（Q4B）：API 31+ 浅色启用系统动态色，API ≤ 30 回退静态板。
 * dynamicColor = false 一行即可完整回到第 70 批「浅灰底 / 纯白卡 / 清新蓝」。
 *
 * 第 78 批：新增 darkTheme（默认跟随系统）。深色一律走本项目静态深色板，
 * 不走系统动态深色 —— 动态深色的 primary 是浅色调，会让蓝底顶栏上的
 * 白字失去对比；静态板才能同时保证「冷静克制」与「语义色可读」。
 */
@Composable
fun LedgerTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        darkTheme -> DarkColorScheme
        useDynamic -> dynamicLightColorScheme(LocalContext.current)
        else -> LightColorScheme
    }
    SideEffect { LedgerColors.syncFrom(scheme, darkTheme) }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
