package com.zwz.offlineledger.ui.detail

import com.zwz.offlineledger.data.display.CategoryDisplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.paging.RecordFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 明细组合筛选。
 *
 * 第 11 批：删掉阶段 2 的 OptionRow(chunked(3)) 纵向列布局——它会把选项
 * 横向压窄、让面板高度随数量增长，10+ 账户时长名称显示不全。现改为
 * 「固定标签列 + 值 + ›」统一网格，长列表走带搜索的单选弹层。
 * 输入框沿用记一笔第 7/8/10 批已验证写法：placeholder、bodyMedium、
 * heightIn(min=40)，不用固定 height、不用浮动 label。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    initial: RecordFilter,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    counterparties: List<String>,
    onApply: (FilterValues) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var fromDate by remember { mutableStateOf(initial.fromDate) }
    var toDate by remember { mutableStateOf(initial.toDate) }
    // 修掉原实现「金额区间不从 initial 回填 → 重新打开点应用即静默清空」的 bug。
    var minText by remember {
        mutableStateOf(initial.minAmountCents?.let { Cents(it).toYuanString() } ?: "")
    }
    var maxText by remember {
        mutableStateOf(initial.maxAmountCents?.let { Cents(it).toYuanString() } ?: "")
    }
    var noteText by remember { mutableStateOf(initial.noteQuery ?: "") }
    var partyText by remember { mutableStateOf(initial.counterpartyQuery ?: "") }
    var type by remember { mutableStateOf(initial.type) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var rootId by remember { mutableStateOf(initial.rootCategoryId) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var currencyCode by remember { mutableStateOf(initial.currencyCode) }
    // 第 27 批：分类条件的只读展示名。旧状态没带名字时按 id 反查，
    // 二级拼成「一级 / 二级」，保证 chip 与筛选面板一定显示具体值。
    var displayName by remember {
        mutableStateOf(initial.categoryDisplayName ?: run {
            val cid = initial.rootCategoryId ?: initial.categoryId
            val c = cid?.let { id -> categories.firstOrNull { it.id == id } }
            if (c == null) null else {
                val parent = c.parentId?.let { pid ->
                    categories.firstOrNull { it.id == pid }?.name
                }
                CategoryDisplay.resolve(parent, c.name)
            }
        })
    }

    var picker by remember { mutableStateOf<String?>(null) }
    var dateTarget by remember { mutableStateOf<Int?>(null) }

    val typeOptions = listOf<Pair<String, String?>>(
        "全部" to null, "支出" to "EXPENSE",
        "收入" to "INCOME", "转账" to "TRANSFER"
    )
    val accountOptions = listOf<Pair<String, String?>>("全部账户" to null) +
        accounts.filter { !it.isArchived }.map { it.name to it.id.toString() }
    val rootOptions = listOf<Pair<String, String?>>("全部一级" to null) +
        categories.filter { it.level == 0 && !it.isArchived }
            .map { it.name to it.id.toString() }
    val childOptions = listOf<Pair<String, String?>>("全部二级" to null) +
        categories.filter {
            it.level == 1 && it.parentId == rootId && !it.isArchived
        }.map { it.name to it.id.toString() }
    val currencyOptions = listOf<Pair<String, String?>>("全部币种" to null) +
        accounts.filter { !it.isArchived }
            .map { it.currencyCode }.distinct().sorted()
            .map { it to it }
    // 第 27 批：一级 + 二级平铺在同一份全量单选列表里，带搜索；
    // value 用 r:/c: 前缀区分层级，选中时同时还原 rootId 与 parentId。
    val categoryOptions = listOf<Pair<String, String?>>("全部分类" to null) +
        categories.filter { it.level == 0 && !it.isArchived }
            .flatMap { root ->
                listOf(root.name to ("r:" + root.id)) +
                    categories.filter { it.parentId == root.id && !it.isArchived }
                        .map { c -> CategoryDisplay.resolve(root.name, c.name) to ("c:" + c.id) }
            }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("组合筛选", style = MaterialTheme.typography.titleMedium)
                    if (initial.activeCount() > 0) {
                        Text(
                            "已设 ${initial.activeCount()} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ThinLine()

                // ≤4 项用 chip 单行铺开；更多则降级为整行弹层，绝不横滚。
                ChipRow("类型", typeOptions, type) { type = it }
                if (currencyOptions.size <= 4) {
                    ChipRow("币种", currencyOptions, currencyCode) { currencyCode = it }
                } else {
                    PickRow(
                        "币种",
                        displayLabel(currencyOptions, currencyCode)
                    ) { picker = "currency" }
                }

                DateRangeRow(
                    fromDate = fromDate,
                    toDate = toDate,
                    onFrom = { dateTarget = 1 },
                    onTo = { dateTarget = 2 },
                    onClear = { fromDate = null; toDate = null }
                )

                PickRow("账户", displayLabel(accountOptions, accountId?.toString())) {
                    picker = "account"
                }
                // 第 27 批：合并成一行「分类」，始终显示已选的具体名称，
                // 点开是带搜索的全量单选弹层（一级 + 「一级 / 二级」）。
                PickRow("分类", displayName ?: "全部分类") { picker = "category" }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        value = minText,
                        onChange = { minText = it },
                        hint = "最小（元）",
                        numeric = true,
                        modifier = Modifier.weight(1f)
                    )
                    CompactField(
                        value = maxText,
                        onChange = { maxText = it },
                        hint = "最大（元）",
                        numeric = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                ThinLine()

                CompactField(
                    value = partyText,
                    onChange = { partyText = it },
                    hint = "交易对方（含匹配）",
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = clearIcon(partyText) { partyText = "" }
                )
                val suggestions = remember(partyText, counterparties) {
                    if (partyText.isBlank()) emptyList()
                    else counterparties
                        .filter { it.contains(partyText, ignoreCase = true) && it != partyText }
                        .take(4)
                }
                suggestions.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clickable { partyText = name },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 52.dp)
                        )
                    }
                }

                CompactField(
                    value = noteText,
                    onChange = { noteText = it },
                    hint = "备注包含",
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = clearIcon(noteText) { noteText = "" }
                )
                ThinLine()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onClear(); onDismiss() }) { Text("清空筛选") }
                    Row {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = {
                            onApply(
                                FilterValues(
                                    fromDate = fromDate,
                                    toDate = toDate,
                                    accountId = accountId,
                                    type = type,
                                    rootCategoryId = rootId,
                                    categoryId = categoryId,
                                    minAmountCents = parseCents(minText),
                                    maxAmountCents = parseCents(maxText),
                                    noteQuery = noteText.takeIf { it.isNotBlank() },
                                    currencyCode = currencyCode,
                                    counterpartyQuery = partyText.takeIf { it.isNotBlank() },
                                    categoryDisplayName = displayName,
                                    counterpartyDisplayName =
                                        partyText.takeIf { it.isNotBlank() },
                                    accountDisplayName = null
                                )
                            )
                            onDismiss()
                        }) { Text("应用") }
                    }
                }
            }
        }
    }

    // 日期用 UTC 锚点往返，与设备时区解耦，避免 M3 DatePicker 串一天。
    dateTarget?.let { target ->
        val initialMillis = when (target) {
            1 -> fromDate?.toUtcMillis()
            else -> toDate?.toUtcMillis()
        } ?: System.currentTimeMillis()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        val d = ms.toDateInt()
                        if (target == 1) fromDate = d else toDate = d
                    }
                    dateTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text("取消") }
            }
        ) { DatePicker(state = dateState) }
    }

    // 全量单选弹层：高度固定，不随账户 / 分类数量增长，长名称完整可见。
    picker?.let { kind ->
        val spec = when (kind) {
            "account" -> PickerData("账户", accountOptions, accountId?.toString()) { v ->
                accountId = v?.toLongOrNull()
            }
            "root" -> PickerData("一级分类", rootOptions, rootId?.toString()) { v ->
                rootId = v?.toLongOrNull()
                categoryId = null
            }
            "child" -> PickerData("二级分类", childOptions, categoryId?.toString()) { v ->
                categoryId = v?.toLongOrNull()
            }
            "category" -> PickerData(
                "分类",
                categoryOptions,
                when {
                    categoryId != null -> "c:" + categoryId
                    rootId != null -> "r:" + rootId
                    else -> null
                }
            ) { v ->
                if (v == null) {
                    rootId = null; categoryId = null; displayName = null
                } else if (v.startsWith("r:")) {
                    rootId = v.removePrefix("r:").toLongOrNull()
                    categoryId = null
                    displayName = categoryOptions.firstOrNull { it.second == v }?.first
                } else {
                    val cid = v.removePrefix("c:").toLongOrNull()
                    categoryId = cid
                    rootId = cid?.let { id ->
                        categories.firstOrNull { it.id == id }?.parentId
                    }
                    displayName = categoryOptions.firstOrNull { it.second == v }?.first
                }
            }
            else -> PickerData("币种", currencyOptions, currencyCode) { v ->
                currencyCode = v
            }
        }
        var search by remember(kind) { mutableStateOf("") }
        val shown = spec.options.filter { it.first.contains(search, ignoreCase = true) }
        Dialog(onDismissRequest = { picker = null }) {
            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(spec.title, style = MaterialTheme.typography.titleMedium)
                    CompactField(
                        value = search,
                        onChange = { search = it },
                        hint = "搜索名称",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = clearIcon(search) { search = "" }
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown) { (name, value) ->
                            val chosen = value == spec.selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .background(
                                        if (chosen) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { spec.onSelect(value); picker = null }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    TextButton(onClick = { picker = null }) { Text("取消") }
                }
            }
        }
    }
}

private data class PickerData(
    val title: String,
    val options: List<Pair<String, String?>>,
    val selected: String?,
    val onSelect: (String?) -> Unit
)

private fun displayLabel(options: List<Pair<String, String?>>, value: String?): String =
    options.firstOrNull { it.second == value }?.first ?: "全部"

/** 有内容时才给一键清空，避免用输入法逐字删（与记一笔第 8 批一致）。 */
private fun clearIcon(
    text: String,
    onClear: () -> Unit
): (@Composable () -> Unit)? =
    if (text.isEmpty()) null else {
        {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "清空")
            }
        }
    }

@Composable
private fun ThinLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<String, String?>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        options.forEach { (text, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = {
                    Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                },
                // weight 平分可用宽度：4 个 chip 在 360dp 竖屏绝不溢出被裁。
                modifier = Modifier.weight(1f).padding(end = 4.dp).heightIn(min = 32.dp)
            )
        }
    }
    ThinLine()
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ThinLine()
}

@Composable
private fun DateRangeRow(
    fromDate: Int?,
    toDate: Int?,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "时间",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        DateCell(Modifier.weight(1f), fromDate, "起始", onFrom)
        Text("—", style = MaterialTheme.typography.bodyMedium)
        DateCell(Modifier.weight(1f), toDate, "结束", onTo)
        if (fromDate != null || toDate != null) {
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
    ThinLine()
}

@Composable
private fun DateCell(
    modifier: Modifier,
    date: Int?,
    hint: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.heightIn(min = 40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = date?.formatDate() ?: hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        // placeholder 默认是 bodyLarge，必须显式与输入文字同字号
        placeholder = {
            Text(hint, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        trailingIcon = trailingIcon,
        // 只降官方 56dp 下限、不设上限 → 不裁 placeholder，也与其他行齐
        modifier = modifier.heightIn(min = 40.dp)
    )
}

/** UTC 午夜锚点，保证 M3 DatePicker 的 millis 与 YYYYMMDD 往返是同一天。 */
private fun Int.toUtcMillis(): Long =
    LocalDate.of(this / 10000, (this / 100) % 100, this % 100)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun Long.toDateInt(): Int {
    val d = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
}

private fun Int.formatDate(): String =
    "%04d-%02d-%02d".format(this / 10000, (this / 100) % 100, this % 100)

private fun parseCents(text: String): Long? {
    if (text.isBlank()) return null
    val d = text.toBigDecimalOrNull() ?: return null
    return try {
        Cents.fromYuan(d.toPlainString()).value
    } catch (e: IllegalArgumentException) {
        null
    }
}
