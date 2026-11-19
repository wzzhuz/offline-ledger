package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.core.time.YearMonthMath

/**
 * 第 41 批：明细页月份条 / 记一笔 / 筛选面板起止日期三处共用的年月选择浮层。
 * 刻意只做「选择入口」，不是一整套日期选择器：官方 DatePicker 负责渲染日、
 * 校验与无障碍，这里只补一个一次点到任意年月的静态网格。
 * 纯 Column + chunked，不用 LazyVerticalGrid / FlowRow，避免实验性 API。
 */
@Composable
fun YearMonthPickerDialog(
    initial: Int,
    monthCounts: Map<Int, Int>,
    onYearShown: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var year by rememberSaveable { mutableStateOf(initial / 100) }
    var selected by rememberSaveable { mutableStateOf(initial) }
    var showYears by rememberSaveable { mutableStateOf(false) }
    val today = remember { MonthRange.currentYearMonth() }
    LaunchedEffect(year) { onYearShown(year) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showYears = !showYears }) {
                        Text(
                            text = year.toString() + " 年 " + if (showYears) "⌃" else "▾",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        year = today / 100
                        selected = today
                    }) { Text("回到本月") }
                }
                if (showYears) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        YearMonthMath.yearsAround(today / 100, 12).chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { y ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .clickable { year = y; showYears = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = y.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (y == year) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    YearMonthMath.monthsOf(year).chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { ym ->
                                val n = monthCounts[ym] ?: 0
                                val chosen = ym == selected
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .padding(2.dp)
                                        .background(
                                            if (chosen) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selected = ym
                                            onConfirm(ym)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = (ym % 100).toString() + "月",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = if (n > 0) n.toString() + " 条" else "—",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}
