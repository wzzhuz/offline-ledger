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
import com.zwz.offlineledger.ui.assets.AccountIconBox
import com.zwz.offlineledger.ui.assets.AccountIconCatalog
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

                    // 第 81 批：转账态转出 / 转入是必需主字段，不是低频项，
                    // 两张卡常驻首屏；支出 / 收入态账户仍在 chip（默认账户兜底）。
                    if (state.type == RecordType.TRANSFER) {
                        TransferAccountCards(
                            accounts = accounts,
                            fromId = state.fromAccountId,
                            toId = state.toAccountId,
                            onPick = { accountTarget = it; activeSheet = EntrySheet.ACCOUNT }
                        )
                    }

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
                                        ClearFieldIcon("清空收款方") { vm.setCounterparty("") }
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
                            trailingIcon = {
                                if (state.description.isNotEmpty()) {
                                    ClearFieldIcon("清空描述") { vm.setDescription("") }
                                }
                            },
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
            lockTarget = state.type == RecordType.TRANSFER,
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
 * 第 82 批：chips 改**非等宽**，按信息量分配宽度，窄项收尾。
 *
 * 使用者真机反馈：5 枚 `weight(1f)` 等分时每枚约 64dp，
 * 「备注·xxx·不计入」被截成只剩「备注」、账户名也只剩前面部分。
 * 根因是第 80 批把编辑态摘要塞进了 chip 的 label —— chip 是入口，不是展示位。
 * 本批：模板固定「模板」（选中后 MUST NOT 跟着改名）、标签只显示「标签 / #N」、
 * 备注固定「备注」、优惠固定「优惠 / 手续费」，有值一律用**右上角小圆点**提示，
 * 完整值进各自 sheet 查看修改；账户给最大宽度显示完整名称。
 *
 * 顺序（收支 5 枚）：账户 → 备注 → 优惠 → 标签 → 模板；
 * 转账 4 枚：备注 → 手续费 → 标签 → 模板（账户已常驻首屏两卡）。
 * 左宽右窄、由主到次、两个最窄的收在右端，避免窄项夹在中间把一排切碎。
 * 每枚仍 ≥40dp 点击区、`labelMedium`、`RoundedCornerShape(12.dp)`。
 */
private data class EntryChip(
    val label: String,
    val weight: Float,
    val hasValue: Boolean = false,
    val onClick: () -> Unit
)

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
    val chosenId = if (state.type == RecordType.EXPENSE)
        state.fromAccountId else state.toAccountId
    val accountLabel = accounts.firstOrNull { it.id == chosenId }?.name ?: "无账户"
    val tagLabel = if (state.tagNames.isEmpty()) "标签" else "#" + state.tagNames.size
    val extraLabel = if (isTransfer) "手续费" else "优惠"
    val noteMarked = state.note.isNotBlank() || state.excludeFromStats
    val chips: List<EntryChip> = if (isTransfer) {
        listOf(
            EntryChip("备注", 2.0f, noteMarked, onNote),
            EntryChip(extraLabel, 1.6f, state.extraExpr.isNotBlank(), onExtra),
            EntryChip(tagLabel, 0.9f, state.tagNames.isNotEmpty(), onTag),
            EntryChip("模板", 0.9f, false, onTemplate)
        )
    } else {
        listOf(
            EntryChip(accountLabel, 2.2f, false, onAccount),
            EntryChip("备注", 1.7f, noteMarked, onNote),
            EntryChip(extraLabel, 1.3f, state.extraExpr.isNotBlank(), onExtra),
            EntryChip(tagLabel, 0.9f, state.tagNames.isNotEmpty(), onTag),
            EntryChip("模板", 0.9f, false, onTemplate)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chips.forEach { chip ->
            Box(
                modifier = Modifier
                    .weight(chip.weight)
                    .heightIn(min = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LedgerColors.SurfaceVariant)
                        .clickable(onClick = chip.onClick)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = LedgerColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (chip.hasValue) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 5.dp, end = 5.dp)
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LedgerColors.Primary)
                    )
                }
            }
        }
    }
}

/**
 * 第 82 批：账户 sheet 接回项目既有的 `AccountIconBox`（品牌 / 自定义 44dp + 放大角标），
 * **删掉余额行**（使用者明确「显示余额没必要，不需要抄一羽」）；
 * 卡号后四位与卡种降为次要小字，不用记、一眼可分储/信。
 * 搜索仍走 `accountBaseLabel`（含卡号后缀），沿用既有 setFromAccount / setToAccount。
 * MUST NOT 在 sheet 内新做长按拖拽排序（踩坑清单 §3.1）。
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
    lockTarget: Boolean = false,
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
    val sheetTitle = when {
        type == RecordType.TRANSFER && lockTarget && initialTarget == 0 -> "选择转出账户"
        type == RecordType.TRANSFER && lockTarget -> "选择转入账户"
        else -> "选择账户"
    }
    BottomPickerSheet(title = sheetTitle, onDismiss = onDismiss) {
        if (type == RecordType.TRANSFER && !lockTarget) {
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
            trailingIcon = {
                if (query.isNotEmpty()) {
                    ClearFieldIcon("清空账户搜索") { query = "" }
                }
            },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountIconBox(
                                name = a.name,
                                cardKind = a.cardKind,
                                iconKey = AccountIconCatalog.iconKeyFor(a.name),
                                iconRef = a.iconRef,
                                size = 44
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    a.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LedgerColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    accountSecondaryLabel(a),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
            trailingIcon = {
                if (state.note.isNotEmpty()) {
                    ClearFieldIcon("清空备注") { onNote("") }
                }
            },
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

/**
 * 第 81 批：四处输入框共用的一键清空出口。
 * 36dp 容器 + 18dp Icon，MUST NOT 把 heightIn(min=40.dp) 的输入行撑到 48dp。
 * 空内容时由调用方不渲染，MUST NOT 常驻一个空叉号。
 */
@Composable
private fun ClearFieldIcon(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 第 81 批：转账态转出 / 转入账户常驻首屏两张中性卡片。
 * 借一羽的「两张大白卡」观感，MUST NOT 用阴影 / 描边 / 卡片堆叠（§7.41）。
 * 不做 ⇅ 互换（新增手势，使用者明确否决）；点哪张卡就把 sheet 锁到哪个 target，
 * 故 sheet 内 MUST NOT 再出现转出 / 转入分段（避免双入口）。
 * 图标容器用 40dp 圆角矩形 + 主色 12% 底，零新增图标依赖。
 */
@Composable
private fun TransferAccountCards(
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    onPick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TransferAccountCard("转出账户", accounts, fromId) { onPick(0) }
        TransferAccountCard("转入账户", accounts, toId) { onPick(1) }
    }
    FormDivider()
}

@Composable
private fun TransferAccountCard(
    tag: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onClick: () -> Unit
) {
    val a = accounts.firstOrNull { it.id == selectedId }
    val label = when {
        a != null -> accountBaseLabel(a)
        selectedId != null -> "原账户已删除，请重新指定"
        else -> "选择账户"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LedgerColors.CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconBox(
            name = a?.name ?: tag,
            cardKind = a?.cardKind,
            iconKey = a?.let { AccountIconCatalog.iconKeyFor(it.name) },
            iconRef = a?.iconRef,
            size = 44
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            a?.let {
                Text(
                    accountSecondaryLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text("›", style = MaterialTheme.typography.labelMedium, color = LedgerColors.Neutral)
    }
}

/**
 * 第 82 批：账户次要信息唯一组合点 —— 卡号尾号 / 卡种 / 币种。
 * 余额刻意不进这里（使用者明确「显示余额没必要」），
 * 卡种用文字而非 12dp 小角标，保证「储蓄卡 / 信用卡」一眼可分。
 */
private fun accountSecondaryLabel(a: AccountEntity): String = buildList {
    a.cardLast4?.takeIf { it.length == 4 }?.let { add("尾号 $it") }
    when (a.cardKind) {
        "DEBIT" -> add("储蓄卡")
        "CREDIT" -> add("信用卡")
    }
    add(a.currencyCode)
}.joinToString(" · ")
