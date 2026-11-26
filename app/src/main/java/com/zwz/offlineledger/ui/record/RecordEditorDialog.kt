package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.AccountQuickCandidates
import com.zwz.offlineledger.core.AccountQuickItem
import com.zwz.offlineledger.core.CategoryQuickCandidates
import com.zwz.offlineledger.core.CategoryQuickItem
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.core.time.YearMonthMath
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.assets.AccountKindBadge
import com.zwz.offlineledger.ui.assets.accountBaseLabel
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.CategorySelectDialog
import com.zwz.offlineledger.ui.components.WrapRow
import com.zwz.offlineledger.ui.components.YearMonthPickerDialog
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 阶段 7 紧凑版记一笔：备注与收款方首屏可见、分类横向单行、
 * 纯数字键盘、日历可选任意日期。「更多」折叠低频开关。
 */
@Composable
fun RecordEditorDialog(
    recordId: Long?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    templateQuickPick: Boolean = true
) {
    val context = LocalContext.current
    val vm: RecordEditorViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            RecordEditorViewModel(
                ep.recordDao(), ep.accountDao(), ep.categoryDao(),
                ep.ledgerRepository(), LedgerPrefs(context)
            )
        }
    )

    LaunchedEffect(recordId) { vm.load(recordId) }

    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val counterparties by vm.counterparties.collectAsState()
    val suggestions by vm.counterpartySuggestions.collectAsState()
    var errorText by remember { mutableStateOf<String?>(null) }
    var showMorePanel by remember { mutableStateOf(false) }
    var showAllCategories by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val localDate = LedgerClock.fromEpochMilli(state.timestamp).localDate
    val todayLocalDate = LedgerClock.fromEpochMilli(System.currentTimeMillis()).localDate
    val zonedTime = java.time.Instant.ofEpochMilli(state.timestamp)
        .atZone(LedgerClock.zoneId)
    val timeLabel = "%02d:%02d".format(zonedTime.hour, zonedTime.minute)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // 第 77 批：记一笔是独立 Dialog Window，edge-to-edge 下单独避让，
            // MUST NOT 让标题被状态栏盖住、保存按钮被手势条压住。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                    Text(
                        text = if (recordId == null) "记一笔" else "编辑流水",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    TypeTabs(state.type) { vm.setType(it) }

                    Text(
                        text = if (state.amountExpr.isBlank()) "0.00" else state.amountExpr,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 日期时间本就是一行无标签小字，不算低频项，保留首屏（基线明文）。
                    DateTimeRow(
                        localDate = localDate,
                        timeLabel = timeLabel,
                        isToday = localDate == todayLocalDate,
                        onPick = { showDatePicker = true },
                        onPickTime = { showTimePicker = true },
                        onToday = { vm.setToday() }
                    )
                    FormDivider()

                    // 分类快捷栏是用户明确的「选择方便」优点，MUST NOT 收进面板。
                    if (state.type != RecordType.TRANSFER) {
                        FormRow("分类", onRowClick = { showAllCategories = true }) {
                            CategoryStrip(
                                categories = categories,
                                type = state.type,
                                selectedId = state.categoryId,
                                expandedRootId = state.expandedRootId,
                                onSelectRoot = { vm.selectRoot(it) },
                                onSelectChild = { id, root -> vm.selectCategory(id, root) }
                            )
                        }
                    }

                    AccountStrip(
                        type = state.type,
                        accounts = accounts,
                        fromId = state.fromAccountId,
                        toId = state.toAccountId,
                        onFrom = { vm.setFromAccount(it) },
                        onTo = { vm.setToAccount(it) }
                    )

                    // 第 77 批（Q3A）：无边框内联，保留 placeholder / 清空图标 /
                    // heightIn(min=40) / bodyMedium；联想 chips 移进「更多」面板。
                    if (state.type != RecordType.TRANSFER) {
                        val partyLabel = if (state.type == RecordType.INCOME)
                            "付款方 / 来源" else "收款方"
                        FormRow("对方") {
                            OutlinedTextField(
                                value = state.counterparty,
                                onValueChange = { vm.setCounterparty(it) },
                                placeholder = {
                                    Text(partyLabel, style = MaterialTheme.typography.bodyMedium)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                colors = borderlessFieldColors(),
                                trailingIcon = {
                                    if (state.counterparty.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable { vm.setCounterparty("") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "清空收款方",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            )
                        }
                    }

                    // 描述是明细主标题来源（COALESCE(description, counterparty)），
                    // MUST NOT 藏进面板；只去掉整圈描边降视觉重量。
                    FormRow("描述") {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = { vm.setDescription(it) },
                            placeholder = { Text("买了什么 / 这笔干嘛的（可选）") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            colors = borderlessFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        )
                    }

                    // 第 77 批（Q1A）：唯一低频入口，编辑态显示摘要，旧值绝不不可见。
                    MoreEntry(
                        state = state,
                        onClick = { showMorePanel = true }
                    )

                    errorText?.let {
                        Text(text = it, color = LedgerColors.ExpenseRed)
                    }
                }

                CalculatorKeypad { key -> vm.onKey(key) }

                Button(
                    onClick = {
                        scope.launch {
                            val err = vm.save()
                            if (err == null) onSaved() else errorText = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        LedgerDatePicker(
            timestamp = state.timestamp,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.setDate(it); showDatePicker = false }
        )
    }
    if (showTimePicker) {
        showTimePicker = false
        android.app.TimePickerDialog(
            context,
            { _, hour, minute -> vm.setTime(hour, minute) },
            zonedTime.hour, zonedTime.minute, true
        ).show()
    }
    if (showMorePanel) {
        MorePanel(
            state = state,
            vm = vm,
            counterparties = counterparties,
            suggestions = suggestions,
            isNew = recordId == null,
            onPickTag = {
                showMorePanel = false
                showTagPicker = true
            },
            onDismiss = { showMorePanel = false }
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            selected = state.tagNames,
            onConfirm = { vm.setTags(it); showTagPicker = false },
            onDismiss = { showTagPicker = false }
        )
    }

    if (showAllCategories) {
        AllCategoriesDialog(
            categories = categories,
            type = state.type,
            selectedId = state.categoryId,
            onSelect = { id, root -> vm.selectCategory(id, root) },
            onDismiss = { showAllCategories = false }
        )
    }
}

/** 第 5 批：三个凸起 Button 换成平铺 TabRow，少一层视觉重量。 */
@Composable
private fun TypeTabs(current: RecordType, onSelect: (RecordType) -> Unit) {
    val items = listOf(
        "支出" to RecordType.EXPENSE,
        "收入" to RecordType.INCOME,
        "转账" to RecordType.TRANSFER
    )
    TabRow(selectedTabIndex = items.indexOfFirst { it.second == current }.coerceAtLeast(0)) {
        items.forEach { (label, type) ->
            Tab(
                selected = current == type,
                onClick = { onSelect(type) },
                text = { Text(label) }
            )
        }
    }
}

/**
 * 第 52 批：默认快捷栏只列启用一级；点一级即按一级记并展开二级 chips，
 * 再点同一级收起。停用项不进快捷栏，由「分类 ›」全量选择器提供。
 * 当前选中项所属一级 / 二级 MUST 追加，编辑历史流水时不出现「一排没一个亮」。
 */
@Composable
private fun CategoryStrip(
    categories: List<CategoryEntity>,
    type: RecordType,
    selectedId: Long?,
    expandedRootId: Long?,
    onSelectRoot: (Long) -> Unit,
    onSelectChild: (Long, Long) -> Unit
) {
    val items = remember(categories) {
        categories.map {
            CategoryQuickItem(
                it.id, it.name, it.type.name, it.parentId, it.level,
                it.enabled, it.isArchived, it.sortOrder, it.colorHex
            )
        }
    }
    val roots = CategoryQuickCandidates.visibleRoots(items, type.name, selectedId)
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        roots.forEach { root ->
            val chosen = selectedId == root.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .background(
                        if (chosen) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectRoot(root.id) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // 第 61 批：记一笔分类快捷栏图标收紧，40/34 → 28/24；
                // 分类管理页的 5 列网格仍用 40dp，两处密度各按各的场景。
                CategoryIcon(root.name, root.colorHex, size = if (chosen) 28 else 24)
                Text(
                    text = root.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosen) LedgerColors.IncomeGreen else LedgerColors.Neutral
                )
            }
        }
    }

    val expanded = categories.firstOrNull { it.id == expandedRootId }
    if (expanded != null) {
        val children = CategoryQuickCandidates.visibleChildren(items, expanded.id, selectedId)
        WrapRow(
            spacing = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            children.forEach { child ->
                FilterChip(
                    selected = selectedId == child.id,
                    onClick = { onSelectChild(child.id, expanded.id) },
                    label = {
                        Text(
                            CategoryQuickCandidates.labelOf(child),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    leadingIcon = {},
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 第 56 批：「分类 ›」全量选择器改用**共享只读** CategorySelectDialog。
 *
 * 与筛选「分类」复用同一份版式（顶部搜索 + 左侧一级分组导航 + 右侧 5 列
 * 彩色图标格），无名称输入框、无「继续添加」/ 保存按钮、无写库路径。
 * 选中后立即回填并关闭；快捷栏中性圆 + 首字口径不变。
 */
@Composable
private fun AllCategoriesDialog(
    categories: List<CategoryEntity>,
    type: RecordType,
    selectedId: Long?,
    onSelect: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    CategorySelectDialog(
        categories = categories,
        type = type,
        selectedId = selectedId,
        allowAll = false,
        title = "全部分类",
        onSelect = { choice ->
            val id = choice.categoryId
            if (id != null) onSelect(id, choice.rootId ?: id)
        },
        onDismiss = onDismiss
    )
}

/**
 * 第 8 批：账户行整块可点，「账户 ›」标签即可打开全量选择，
 * 删掉原先占位置的「更多 ▾」chip。选中态由 FilterChip 底色表达，
 * 不再用文字圆点前缀表选中。全量弹层与搜索状态提升到本层，
 * 转账时「转出 ›」「转入 ›」各自打开对应选择。
 */
@Composable
private fun AccountStrip(
    type: RecordType,
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    onFrom: (Long) -> Unit,
    onTo: (Long) -> Unit
) {
    // 第 44 批：归档账户 MUST 仍可见（历史归属有效），只在选择器里标注，
    // MUST NOT 因为「不再常用」就从编辑态里过滤掉。
    val visible = accounts
    // 0 = 支出/转出，1 = 收入/转入；null = 关闭
    var pickerTarget by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }

    // 第 77 批（Q2A）：默认账户机制早已存在（LedgerPrefs + load 预填），
    // 这里只把「当前：X + 4 枚 chips」收敛成一枚显示当前值的按钮，
    // 全量搜索弹层一字未改，零新数据路径。
    FormRow("账户", onRowClick = { query = ""; pickerTarget = 0 }) {
        if (type == RecordType.TRANSFER) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountValueButton("转出", visible, fromId) { query = ""; pickerTarget = 0 }
                AccountValueButton("转入", visible, toId) { query = ""; pickerTarget = 1 }
            }
        } else {
            val chosenId = if (type == RecordType.EXPENSE) fromId else toId
            AccountValueButton("", visible, chosenId) { query = ""; pickerTarget = 0 }
        }
    }

    val target = pickerTarget
    if (target != null) {
        val onSelect: (Long) -> Unit = when {
            type == RecordType.TRANSFER -> if (target == 0) onFrom else onTo
            type == RecordType.EXPENSE -> onFrom
            else -> onTo
        }
        val title = when {
            type == RecordType.TRANSFER && target == 1 -> "转入账户"
            type == RecordType.TRANSFER -> "转出账户"
            else -> "全部账户"
        }
        AlertDialog(
            onDismissRequest = { pickerTarget = null },
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                "搜索账户名",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    )
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        // 第 61 批：搜索键与显示名都走 accountBaseLabel，
                        // 输入「1122」也能搜到对应卡；多张同名银行卡可区分。
                        accounts.filter {
                            accountBaseLabel(it).contains(query, ignoreCase = true)
                        }.forEach { a ->
                                TextButton(
                                    onClick = { onSelect(a.id); pickerTarget = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        accountBaseLabel(a) + " · " + a.currencyCode
                                    )
                                }
                            }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerTarget = null }) { Text("关闭") }
            }
        )
    }
}

/** 第 77 批：单枚账户按钮，带卡号后四位与 12dp 储/信角标。 */
@Composable
private fun AccountValueButton(
    tag: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onShowAll: () -> Unit
) {
    val a = accounts.firstOrNull { it.id == selectedId }
    val label = when {
        a != null -> accountBaseLabel(a)
        selectedId != null -> "原账户已删除，请重新指定"
        else -> "未选择账户"
    }
    OutlinedButton(onClick = onShowAll) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tag.isNotEmpty()) {
                Text(tag + " · ", style = MaterialTheme.typography.labelSmall)
            }
            a?.cardKind?.let {
                AccountKindBadge(it, size = 12)
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountQuickRow(
    tag: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onShowAll: () -> Unit
) {
    if (tag.isNotEmpty()) {
        Text(
            text = "$tag ›",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowAll)
                .padding(vertical = 6.dp)
        )
    }
    // 第 44 批：候选 = 常用前 N 个 + 当前选中项（冷门 / 归档也一定可见）。
    // 第 60 批：带上 cardKind 小标志；第 61 批：name 改传 accountBaseLabel，
    // 让「当前：」文案与 chip 都带卡号后四位，多张同名银行卡可区分。
    // labelOf / select / promoteById 全部按 id 工作，纯 JVM 契约零改动。
    val items = accounts.map {
        AccountQuickItem(it.id, accountBaseLabel(it), it.isArchived, it.cardKind)
    }
    val shown = AccountQuickCandidates.select(items, selectedId, QUICK_COUNT)
    val current = AccountQuickCandidates.currentOrNull(items, selectedId)
    // 明确写出「当前是谁」，避免一排 chip 没一个选中时被误读成默认账户。
    val currentText = when {
        current != null -> "当前：" + AccountQuickCandidates.labelOf(current)
        selectedId != null -> "原账户已删除，请重新指定"
        else -> "未选择账户"
    }
    Text(
        text = currentText,
        style = MaterialTheme.typography.labelSmall,
        color = if (selectedId != null && current == null)
            MaterialTheme.colorScheme.error else LedgerColors.Neutral,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { a ->
            FilterChip(
                selected = a.id == selectedId,
                onClick = { onSelect(a.id) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 小标志叠在 chip 标签前，12dp，不使用文字后缀挤占账户名。
                        a.cardKind?.let {
                            // 第 61 批：记一笔 chip 收窄到 10dp，减少横向占用。
                            AccountKindBadge(it, size = 10)
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            AccountQuickCandidates.labelOf(a),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = {},
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

/**
 * 第 7 批：无标签副行，日期与时间本身自解释。去掉 ‹ › 两个箭头，
 * 「今天」只在当前不是今天时出现，常态下这一行只有「11月16日 14:30」。
 */
@Composable
private fun DateTimeRow(
    localDate: Int,
    timeLabel: String,
    isToday: Boolean,
    onPick: () -> Unit,
    onPickTime: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPick) {
            Text(MonthRange.dateLabel(localDate), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onPickTime) {
            Text(timeLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (!isToday) {
            TextButton(onClick = onToday) {
                Text(
                    "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
        }
    }
}

/**
 * 日历回传 UTC 午夜毫秒，ViewModel 再换算回本地时区，避免差一天。
 * 第 41 批：保留官方 DatePicker 日视图，只补「跳转年月」入口；
 * 选完年月同时写 displayedMonthMillis 与 selectedDateMillis，
 * 1/31 跳到 2 月按 min(day, 月天数) 收敛，不静默丢日期、不崩。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerDatePicker(
    timestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val localDate = Instant.ofEpochMilli(timestamp)
        .atZone(LedgerClock.zoneId).toLocalDate()
    val utcMillis = localDate.atStartOfDay(ZoneOffset.UTC)
        .toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
    var jump by remember { mutableStateOf(false) }
    if (jump) {
        YearMonthPickerDialog(
            initial = localDate.year * 100 + localDate.monthValue,
            monthCounts = emptyMap(),
            onYearShown = {},
            onDismiss = { jump = false },
            onConfirm = { ym ->
                val day = YearMonthMath.clampDay(ym, localDate.dayOfMonth)
                val target = LocalDate.of(ym / 100, ym % 100, day)
                pickerState.selectedDateMillis = target
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                pickerState.displayedMonthMillis = YearMonthMath.firstDayUtcMillis(ym)
                jump = false
            }
        )
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row {
                    TextButton(onClick = { jump = true }) { Text("跳转年月") }
                    TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) }) {
                        Text("确定")
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        ) { DatePicker(state = pickerState) }
    }
}

/** 阶段 7：纯数字键盘，无加减乘除与括号。空字符串占位只做 Spacer。 */
@Composable
private fun CalculatorKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        "7", "8", "9", "⌫",
        "4", "5", "6", "C",
        "1", "2", "3", ".",
        "", "0", "", ""
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        keys.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        TextButton(
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f)
                        ) { Text(text = key, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}


/**
 * 第 6 批：统一表单网格。固定 52dp 标签列 + 一条细分割线，
 * 左侧边界完全一致；同区域只用 OutlinedTextField 一种输入容器，
 * 不再混用带框输入框 / 无边界 TextButton / AssistChip 直接铺在根 Column。
 */
private const val QUICK_COUNT = 4

@Composable
fun FormRow(
    label: String,
    onRowClick: (() -> Unit)? = null,
    centerLabel: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(onRowClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(vertical = 1.dp),
        verticalAlignment = if (centerLabel) Alignment.CenterVertically else Alignment.Top
    ) {
        Row(
            modifier = Modifier.width(52.dp)
                .then(if (centerLabel) Modifier else Modifier.padding(top = 12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.Neutral
            )
            if (onRowClick != null) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) { content() }
    }
    FormDivider()
}

@Composable
fun FormDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * 第 67 批：优惠 / 手续费入口。
 *
 * 金额真相源仍是 `state.amountExpr`（实付）；这里只收「附加金额」本身，
 * 原价 / 实际扣款全部由整数分推导，MUST NOT 引入第二个金额列。
 * 支出 / 收入只显示优惠；转账只显示手续费，并用 extraAsDiscount 切换
 「转账优惠」（落库为负 feeCents，与一羽 CSV 手册口径一致）。
 */
@Composable
private fun ExtraAmountSection(state: EditorState, vm: RecordEditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.extraExpr) {
        if (state.extraExpr.isNotBlank()) expanded = true
    }
    val isTransfer = state.type == RecordType.TRANSFER
    val label = if (isTransfer) {
        if (state.extraAsDiscount) "转账优惠" else "手续费"
    } else "优惠"
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = if (expanded) "▾ $label / 附加金额" else "▸ 添加$label",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            if (isTransfer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记为：", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.setExtraAsDiscount(false) }) {
                        Text(
                            "手续费",
                            color = if (!state.extraAsDiscount) MaterialTheme.colorScheme.primary
                            else LedgerColors.Neutral
                        )
                    }
                    TextButton(onClick = { vm.setExtraAsDiscount(true) }) {
                        Text(
                            "转账优惠",
                            color = if (state.extraAsDiscount) MaterialTheme.colorScheme.primary
                            else LedgerColors.Neutral
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.extraExpr,
                onValueChange = { vm.setExtra(it) },
                label = { Text("$label（元）") },
                placeholder = { Text("0.00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ExtraAmountSummary(state = state, label = label)
        }
    }
    FormDivider()
}

/** 三行金额只读预览；输入未成型时不渲染，避免展示一个错误的「约等于」。 */
@Composable
private fun ExtraAmountSummary(state: EditorState, label: String) {
    if (state.amountExpr.isBlank() || state.extraExpr.isBlank()) return
    val amount = try {
        Cents.fromYuan(state.amountExpr.trim()).value
    } catch (e: IllegalArgumentException) {
        return
    }
    val extra = try {
        Cents.fromYuan(state.extraExpr.trim()).value
    } catch (e: IllegalArgumentException) {
        return
    }
    val isTransfer = state.type == RecordType.TRANSFER
    val signedFee = if (isTransfer && state.extraAsDiscount) -extra else extra
    val lines: List<Pair<String, String>> = if (isTransfer) {
        listOf(
            "转出金额" to Cents(amount).toYuanString(),
            label to (if (signedFee < 0) "-" else "") + Cents(kotlin.math.abs(signedFee)).toYuanString(),
            "实际扣款" to Cents(amount + signedFee).toYuanString()
        )
    } else {
        listOf(
            "原始金额" to Cents(amount + extra).toYuanString(),
            "优惠" to Cents(extra).toYuanString(),
            "实际金额" to Cents(amount).toYuanString()
        )
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        lines.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    k,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.weight(1f)
                )
                Text(v, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (isTransfer) {
            Text(
                "手续费不计入收支，仅影响账户余额",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
    }
}

/** 第 77 批：只去整圈描边，不动 placeholder / 清空图标 / 行高。 */
@Composable
private fun borderlessFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent
)

/** 唯一低频入口；编辑态把备注 / 优惠 / 标签摘要带出来，MUST NOT 让旧值不可见。 */
@Composable
private fun MoreEntry(state: EditorState, onClick: () -> Unit) {
    val bits = mutableListOf<String>()
    if (state.tagNames.isNotEmpty()) bits += state.tagNames.size.toString() + " 个标签"
    if (state.extraExpr.isNotBlank()) bits += "优惠/手续费"
    if (state.note.isNotBlank()) {
        bits += "备注：" + (if (state.note.length > 10) state.note.take(10) + "…" else state.note)
    }
    val label = if (bits.isEmpty()) "更多 ›"
    else "更多 · " + bits.joinToString(" · ") + " ›"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("›", style = MaterialTheme.typography.labelMedium, color = LedgerColors.Neutral)
    }
}

/**
 * 第 77 批：唯一低频面板。用 Dialog + 底部对齐 Surface 模拟 sheet，
 * MUST NOT 引入 ExperimentalMaterial3Api 的 ModalBottomSheet（项目 T12 惯例）。
 */
@Composable
private fun MorePanel(
    state: EditorState,
    isNew: Boolean,
    vm: RecordEditorViewModel,
    counterparties: List<String>,
    suggestions: List<String>,
    onPickTag: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        "更多选项",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    FormDivider()

                    if (isNew) {
                        TemplateQuickPickBar(onTemplate = { vm.applyTemplate(it) })
                    }

                    FormRow("标签", onRowClick = onPickTag) {
                        Text(
                            text = if (state.tagNames.isEmpty()) "未设置 · 点此添加"
                            else state.tagNames.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ExtraAmountSection(state = state, vm = vm)
                    FormDivider()

                    FormRow("备注") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = state.note,
                                onValueChange = { vm.setNote(it) },
                                placeholder = {
                                    Text("可选", style = MaterialTheme.typography.bodyMedium)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            )
                            Text(
                                "不计入",
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral
                            )
                            Switch(
                                checked = state.excludeFromStats,
                                onCheckedChange = { vm.setExclude(it) }
                            )
                        }
                    }

                    if (state.type != RecordType.TRANSFER) {
                        val quick: List<String> = if (state.counterparty.isBlank())
                            counterparties.take(QUICK_COUNT) else suggestions.take(QUICK_COUNT)
                        if (quick.isNotEmpty()) {
                            Text(
                                "常用收款方",
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quick.forEach { name ->
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .clickable { vm.setCounterparty(name) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LedgerColors.Neutral
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("完成") }
                }
            }
        }
    }
}
