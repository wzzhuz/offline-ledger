package com.zwz.offlineledger.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.data.paging.RecordFilter
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 阶段 2 只做基础组合筛选；对账锚点与金额反查属阶段 3，不提前加入。 */
@Composable
fun FilterSheet(
    initial: RecordFilter,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onApply: (FilterValues) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var fromText by remember { mutableStateOf(initial.fromDate?.toString() ?: "") }
    var toText by remember { mutableStateOf(initial.toDate?.toString() ?: "") }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf(initial.noteQuery ?: "") }
    var type by remember { mutableStateOf(initial.type) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var rootId by remember { mutableStateOf(initial.rootCategoryId) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var currencyCode by remember { mutableStateOf(initial.currencyCode) }

    val roots = categories.filter { it.level == 0 && !it.isArchived }
    val children = categories.filter {
        it.level == 1 && it.parentId == rootId && !it.isArchived
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "组合筛选", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "时间 / 账户 / 类型 / 一级·二级分类 / 金额区间 / 备注全文 / 币种",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it },
                        label = { Text("起始 YYYYMMDD") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it },
                        label = { Text("结束 YYYYMMDD") },
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionTitle("类型")
                OptionRow(
                    options = listOf<Pair<String, String?>>(
                        "全部" to null, "支出" to "EXPENSE",
                        "收入" to "INCOME", "转账" to "TRANSFER"
                    ),
                    selected = type,
                    onSelect = { type = it }
                )

                SectionTitle("账户")
                OptionRow(
                    options = listOf<Pair<String, String?>>("全部" to null) +
                        accounts.filter { !it.isArchived }.map { it.name to it.id.toString() },
                    selected = accountId?.toString(),
                    onSelect = { accountId = it?.toLongOrNull() }
                )

                SectionTitle("一级分类")
                OptionRow(
                    options = listOf<Pair<String, String?>>("全部" to null) +
                        roots.map { it.name to it.id.toString() },
                    selected = rootId?.toString(),
                    onSelect = {
                        rootId = it?.toLongOrNull()
                        categoryId = null
                    }
                )

                if (rootId != null) {
                    SectionTitle("二级分类")
                    OptionRow(
                        options = listOf<Pair<String, String?>>("全部" to null) +
                            children.map { it.name to it.id.toString() },
                        selected = categoryId?.toString(),
                        onSelect = { categoryId = it?.toLongOrNull() }
                    )
                }

                SectionTitle("币种")
                OptionRow(
                    options = listOf<Pair<String, String?>>("全部币种" to null) +
                        accounts.filter { !it.isArchived }
                            .map { it.currencyCode }.distinct().sorted()
                            .map { it to it },
                    selected = currencyCode,
                    onSelect = { currencyCode = it }
                )

                SectionTitle("金额区间（元）")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("最小") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("最大") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注包含") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onClear(); onDismiss() }) { Text("清空筛选") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = {
                        onApply(
                            FilterValues(
                                fromDate = fromText.toIntOrNull(),
                                toDate = toText.toIntOrNull(),
                                accountId = accountId,
                                type = type,
                                rootCategoryId = rootId,
                                categoryId = categoryId,
                                minAmountCents = parseCents(minText),
                                maxAmountCents = parseCents(maxText),
                                noteQuery = noteText.takeIf { it.isNotBlank() },
                                currencyCode = currencyCode
                            )
                        )
                        onDismiss()
                    }) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp)
    )
}

/** options: (显示文字, 值)；null 值表示「全部」。 */
@Composable
private fun OptionRow(
    options: List<Pair<String, String?>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.chunked(3).forEach { group ->
            Column(modifier = Modifier.weight(1f)) {
                group.forEach { (label, value) ->
                    TextButton(onClick = { onSelect(value) }) {
                        Text(
                            text = if (value == selected) "● $label" else label,
                            color = if (value == selected) LedgerColors.IncomeGreen
                            else LedgerColors.Neutral
                        )
                    }
                }
            }
        }
    }
}

private fun parseCents(text: String): Long? {
    if (text.isBlank()) return null
    val d = text.toBigDecimalOrNull() ?: return null
    return try {
        Cents.fromYuan(d.toPlainString()).value
    } catch (e: IllegalArgumentException) {
        null
    }
}
