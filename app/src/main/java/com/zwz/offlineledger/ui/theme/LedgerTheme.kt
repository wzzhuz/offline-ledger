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
 * 第 78 批：中性色与表面色从常量升为可观察状态，由 LedgerTheme 在一次
 * SideEffect 里同步当前 ColorScheme，让 Canvas draw scope 与普通函数也能读到。
 *
 * 第 79 批（真机反馈）：把「页面底 / 卡片面 / 次级底」拆成三个独立语义。
 * 上一批把统计卡容器改成读 scheme.surface，而统计页底来自 Scaffold 的
 * scheme.background —— 动态浅色下二者是同一档近白，卡片边界消失。
 * 这正是第 70 批记过的「边界糊」根因，本批恢复「中性微灰底 + 纯白卡」。
 */
object LedgerColors {
    private val RED = Color(0xFFC62828)
    private val GREEN = Color(0xFF2E7D32)
    // 深色底上原深红 / 深绿对比不足，改用同语义的柔和珊瑚红 / 柔和绿。
    private val DARK_RED = Color(0xFFF0A6A0)
    private val DARK_GREEN = Color(0xFF9CCFA6)

    // 卡片 / 顶栏 / 弹层表面：刻意固定，不随系统动态色漂。
    private val CARD_LIGHT = Color(0xFFFFFFFF)
    private val CARD_DARK = Color(0xFF1E2025)

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

    /** 页面 / 全屏底。浅色中性微灰，深色深灰蓝，避免纯黑纯白。 */
    private val _background = mutableStateOf(Color(0xFFF2F3F7))
    var Background: Color
        get() = _background.value
        private set(value) { _background.value = value }

    /** 卡片、顶栏、sheet、弹层容器色。浅色纯白，深色 #1E2025。 */
    private val _cardSurface = mutableStateOf(CARD_LIGHT)
    var CardSurface: Color
        get() = _cardSurface.value
        private set(value) { _cardSurface.value = value }

    /** 卡片 / 顶栏上的文字与图标色。 */
    private val _onSurface = mutableStateOf(Color(0xFF1F2430))
    var OnSurface: Color
        get() = _onSurface.value
        private set(value) { _onSurface.value = value }

    /** 兼容旧引用：等价 colorScheme.surface，不再用于卡片容器。 */
    private val _surface = mutableStateOf(CARD_LIGHT)
    var Surface: Color
        get() = _surface.value
        private set(value) { _surface.value = value }

    /** 次级底 / 进度条轨道 / 分组底色。 */
    private val _surfaceVariant = mutableStateOf(Color(0xFFE9EBF1))
    var SurfaceVariant: Color
        get() = _surfaceVariant.value
        private set(value) { _surfaceVariant.value = value }

    /** 比卡片略高一级的容器色（折叠区 / 拖拽态）。 */
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
        _background.value = scheme.background
        _cardSurface.value = if (darkTheme) CARD_DARK else CARD_LIGHT
        _onSurface.value = scheme.onSurface
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
 * primary 刻意保持与浅色同一支蓝、onPrimary 保持纯白 —— 小面积主色点缀
 * （FAB / 进度条）上的白图标无需逐个改色，深色下依然可读。
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
 * 第 79 批：动态浅色只借用强调色（primary / secondary / tertiary 及其容器），
 * 中性槽位一律套回第 70 批静态值。
 *
 * 原因：M3 动态浅色的 background 与 surface 都是「带一点壁纸色调的近白」，
 * 两者几乎同色；卡片若读 scheme.surface，就会和 Scaffold 的底融成一片。
 * 记账 App 的底与卡不该随壁纸漂，强调色随壁纸即可。
 */
private fun dynamicWithNeutralSurface(context: android.content.Context): ColorScheme {
    val dyn = dynamicLightColorScheme(context)
    // 只借用动态强调色；中性槽位与 LightColorScheme 逐值一致（单一真相源见上方静态板）。
    // 刻意用 lightColorScheme(...) 显式构造而非 copy(...)：参数名与静态板共用同一套，
    // 避免依赖 ColorScheme.copy 在不同 M3 版本下的参数集合。
    return lightColorScheme(
        primary = dyn.primary,
        onPrimary = dyn.onPrimary,
        primaryContainer = dyn.primaryContainer,
        onPrimaryContainer = dyn.onPrimaryContainer,
        secondary = dyn.secondary,
        onSecondary = dyn.onSecondary,
        secondaryContainer = dyn.secondaryContainer,
        onSecondaryContainer = dyn.onSecondaryContainer,
        tertiary = dyn.tertiary,
        onTertiary = dyn.onTertiary,
        tertiaryContainer = dyn.tertiaryContainer,
        onTertiaryContainer = dyn.onTertiaryContainer,
        error = dyn.error,
        onError = dyn.onError,
        errorContainer = dyn.errorContainer,
        onErrorContainer = dyn.onErrorContainer,
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
}

/**
 * 第 77 批（Q4B）：API 31+ 浅色启用系统动态色，API ≤ 30 回退静态板。
 * dynamicColor = false 一行即可完整回到第 70 批「浅灰底 / 纯白卡 / 清新蓝」。
 *
 * 第 78 批：新增 darkTheme（默认跟随系统）。深色一律走本项目静态深色板，
 * 不走系统动态深色 —— 动态深色的 primary 是浅色调，会让小面积主色点缀上的
 * 白图标失去对比；静态板才能同时保证「冷静克制」与「语义色可读」。
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
        useDynamic -> dynamicWithNeutralSurface(LocalContext.current)
        else -> LightColorScheme
    }
    SideEffect { LedgerColors.syncFrom(scheme, darkTheme) }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
