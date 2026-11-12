package com.zwz.offlineledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 中文记账语境：红 = 支出 / 赤字，绿 = 收入。 */
object LedgerColors {
    val ExpenseRed = Color(0xFFC62828)
    val IncomeGreen = Color(0xFF2E7D32)
    val Neutral = Color(0xFF616161)
    val Primary = Color(0xFF1B5E20)
}

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LedgerColors.Primary,
            secondary = LedgerColors.IncomeGreen
        ),
        content = content
    )
}
