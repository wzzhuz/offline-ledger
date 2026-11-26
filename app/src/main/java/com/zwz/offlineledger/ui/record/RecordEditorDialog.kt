package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
    var activeSheet by remember { mutableStateOf<EntrySheet?>(null) }
    var accountTarget by remember { mutableStateOf(0) }
    var partyFocused by remember { mutableStateOf(false) }
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
                                    .onFocusChanged { partyFocused = it.isFocused }
                            )
                        }
                    }

                    // 第 80 批：联想与常用回到输入框正下方，只在聚焦 / 有内容时出现；
                    // 空输入给常用、有输入给模糊联想，仍共用一条互斥替换，ViewModel 零改动。
                    if (state.type != RecordType.TRANSFER &&
                        (partyFocused || state.counterparty.isNotBlank())
                    ) {
                        val quick: List<String> = if (state.counterparty.isBlank())
                            counterparties.take(QUICK_COUNT)
                        else suggestions.take(QUICK_COUNT)
                        if (quick.isNotEmpty()) {
                            CounterpartySuggestionRow(quick) { vm.setCounterparty(it) }
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

                    errorText?.let {
                        Text(text = it, color = LedgerColors.ExpenseRed)
                    }
                }

                // 第 80 批：低频项由键盘上方单行 5 枚 chips 直达，撤掉「更多」这一层。
                // chips 与键盘同属固定底部区，不进滚动表单，不挤压首屏。
                EntryChipBar(
                    state = state,
                    accounts = accounts,
                    onAccount = { accountTarget = 0; activeSheet = EntrySheet.ACCOUNT },
                    onTemplate = { activeSheet = EntrySheet.TEMPLATE },
                    onNote = { activeSheet = EntrySheet.NOTE },
                    onTag = { showTagPicker = true },
                    onExtra = { activeSheet = EntrySheet.EXTRA }
                )
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
    // 第 80 批：每个 chip 直接开自己的底部 sheet，不再经过统一的 MorePanel。
    when (activeSheet) {
        EntrySheet.ACCOUNT -> AccountPickerSheet(
            type = state.type,
            accounts = accounts,
            fromId = state.fromAccountId,
            toId = state.toAccountId,
            initialTarget = accountTarget,
            onFrom = { vm.setFromAccount(it) },
            onTo = { vm.setToAccount(it) },
            onDismiss = { activeSheet = null }
        )
        EntrySheet.TEMPLATE -> if (recordId == null) {
            TemplatePickerSheet(
                onTemplate = { vm.applyTemplate(it); activeSheet = null },
                onDismiss = { activeSheet = null }
            )
        }
        EntrySheet.NOTE -> NoteSheet(
            state = state,
            onNote = { vm.setNote(it) },
            onExclude = { vm.setExclude(it) },
            onDismiss = { activeSheet = null }
        )
        EntrySheet.EXTRA -> BottomPickerSheet(
            title = if (state.type == RecordType.TRANSFER) "手续费 / 转账优惠" else "优惠金额",
            onDismiss = { activeSheet = null }
        ) {
            ExtraAmountSection(state = state, vm = vm, expandedInitial = true)
        }
        null -> Unit
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
private fun ExtraAmountSection(
    state: EditorState,
    vm: RecordEditorViewModel,
    expandedInitial: Boolean = false
) {
    var expanded by remember { mutableStateOf(expandedInitial) }
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

/** 第 80 批：键盘上方 chip 的目标；标签直接复用既有 TagPickerDialog，不进枚举。 */
private enum class EntrySheet { ACCOUNT, TEMPLATE, NOTE, EXTRA }

/**
 * 一羽式底部 sheet：沿用项目既有 `Dialog` + `Alignment.BottomCenter` + `Surface`，
 * MUST NOT 引入 ExperimentalMaterial3Api 的 ModalBottomSheet（T12 惯例）。
 * 同包 TemplateQuickPick.kt 复用此壳，故不加 private。
 */
@Composable
fun BottomPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
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
                color = LedgerColors.CardSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(LedgerColors.SurfaceVariant)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    content()
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("完成") }
                }
            }
        }
    }
}

/**
 * 键盘上方单行 5 枚 chips：账户 / 模板 / 备注 / 标签 / 优惠或手续费。
 * 「不计入收支」开关并入备注 sheet（它本就与备注同一行），不占第 6 枚。
 * 每枚点击区 ≥ 40dp（项目为这条回摆过三次），字号 labelMedium。
 * 编辑态在 label 上带出旧值摘要，MUST NOT 让旧内容不可见。
 */
@Composable
private fun EntryChipBar(
    state: EditorState,
    accounts: List<AccountEntity>,
    onAccount: () -> Unit,
    onTemplate: () -> Unit,
    onNote: () -> Unit,
    onTag: () -> Unit,
    onExtra: () -> Unit
) {
    val isTransfer = state.type == RecordType.TRANSFER
    val accountLabel = if (isTransfer) {
        "转出/转入"
    } else {
        val chosenId = if (state.type == RecordType.EXPENSE)
            state.fromAccountId else state.toAccountId
        accounts.firstOrNull { it.id == chosenId }?.name ?: "无账户"
    }
    val noteLabel = buildString {
        append(if (state.note.isBlank()) "备注" else "备注·" + state.note.take(3))
        if (state.excludeFromStats) append("·不计入")
    }
    val tagLabel = if (state.tagNames.isEmpty()) "标签" else "#" + state.tagNames.size
    val extraLabel = if (isTransfer) "手续费" else "优惠"
    val extraChip = if (state.extraExpr.isBlank()) extraLabel
    else extraLabel + "·" + state.extraExpr.take(4)
    val chips: List<Pair<String, () -> Unit>> = listOf(
        accountLabel to onAccount,
        "模板" to onTemplate,
        noteLabel to onNote,
        tagLabel to onTag,
        extraChip to onExtra
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chips.forEach { (label, onClick) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LedgerColors.SurfaceVariant)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = LedgerColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 账户 sheet：一羽式两列网格 + 余额 + 搜索（含卡号后缀），转账态顶部给转出/转入分段。
 * 沿用 vm 既有 setFromAccount / setToAccount，零新数据路径。
 * MUST NOT 在 sheet 内新做长按拖拽排序（踩坑清单 §3.1）；排序仍走既有独立页。
 */
@Composable
private fun AccountPickerSheet(
    type: RecordType,
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    initialTarget: Int,
    onFrom: (Long) -> Unit,
    onTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(initialTarget) }
    val shown = accounts.filter {
        accountBaseLabel(it).contains(query, ignoreCase = true)
    }
    val selectedId = when {
        type == RecordType.TRANSFER && target == 0 -> fromId
        type == RecordType.TRANSFER -> toId
        type == RecordType.EXPENSE -> fromId
        else -> toId
    }
    BottomPickerSheet(title = "选择账户", onDismiss = onDismiss) {
        if (type == RecordType.TRANSFER) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = target == 0,
                    onClick = { target = 0 },
                    label = { Text("转出账户") }
                )
                FilterChip(
                    selected = target == 1,
                    onClick = { target = 1 },
                    label = { Text("转入账户") }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索账户名 / 卡号后四位") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        )
        Spacer(Modifier.height(8.dp))
        shown.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { a ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (a.id == selectedId) LedgerColors.Primary.copy(alpha = 0.12f)
                                else LedgerColors.SurfaceVariant
                            )
                            .clickable {
                                when {
                                    type == RecordType.TRANSFER && target == 0 -> onFrom(a.id)
                                    type == RecordType.TRANSFER -> onTo(a.id)
                                    type == RecordType.EXPENSE -> onFrom(a.id)
                                    else -> onTo(a.id)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                a.cardKind?.let {
                                    AccountKindBadge(it, size = 12)
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    accountBaseLabel(a),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                Cents(a.balanceCents).toYuanString() + " " + a.currencyCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 备注 + 不计入收支：开关本就与备注同一行，一并搬进 chip 的 sheet。 */
@Composable
private fun NoteSheet(
    state: EditorState,
    onNote: (String) -> Unit,
    onExclude: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    BottomPickerSheet(title = "备注与不计入", onDismiss = onDismiss) {
        OutlinedTextField(
            value = state.note,
            onValueChange = onNote,
            placeholder = { Text("可选", style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        )
        FormDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "不计入收支",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = state.excludeFromStats, onCheckedChange = onExclude)
        }
    }
}

/**
 * 收款方联想 / 常用：紧贴输入框下方一条 40dp 内联条，点一下填入。
 * MUST NOT 常驻首屏占空间，MUST NOT 塞进 chip 弹层（脱离输入上下文）。
 */
@Composable
private fun CounterpartySuggestionRow(
    quick: List<String>,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quick.forEach { name ->
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { onPick(name) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = LedgerColors.Neutral
                )
            }
        }
    }
}
