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
import androidx.compose.material3.OutlinedTextField
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
    var showMore by remember { mutableStateOf(false) }
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                    if (recordId == null && templateQuickPick) {
                        TemplateQuickPickBar(onTemplate = { vm.applyTemplate(it) })
                    }
                    TypeTabs(state.type) { vm.setType(it) }

                    Text(
                        text = if (state.amountExpr.isBlank()) "0.00" else state.amountExpr,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 第 67 批：优惠 / 手续费折叠入口。默认收起，编辑态有值自动展开。
                    ExtraAmountSection(state = state, vm = vm)
                    // 第 7 批：日期时间是自解释的元数据，不再占一行网格标签，
                    // 也不再与金额挤同一行（长金额会挤压右侧）。金额保住视觉锚点。
                    DateTimeRow(
                        localDate = localDate,
                        timeLabel = timeLabel,
                        isToday = localDate == todayLocalDate,
                        onPick = { showDatePicker = true },
                        onPickTime = { showTimePicker = true },
                        onToday = { vm.setToday() }
                    )
                    FormDivider()

                    // 第 75 批：标签一行，转账也能挂（转账本无分类）。
                    FormRow("标签", onRowClick = { showTagPicker = true }) {
                        Text(
                            text = if (state.tagNames.isEmpty()) "未设置 · 点此添加"
                            else state.tagNames.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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

                    // 第 7 批：交易对方仍仅支出 / 收入显示；备注移到「更多」后
                    // 转账也能用（还信用卡、转生活费等），只是不显示对方。
                    if (state.type != RecordType.TRANSFER) {
                        val partyLabel = if (state.type == RecordType.INCOME)
                            "付款方 / 来源" else "收款方"
                        FormRow("对方") {
                            OutlinedTextField(
                                value = state.counterparty,
                                onValueChange = { vm.setCounterparty(it) },
                                placeholder = {
                                    Text(
                                        partyLabel,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                // 第 10 批：heightIn(min=40.dp) 只降官方 56dp 下限、
                                // 不设上限，内容自撑 → 不裁 placeholder，也与其他行齐。
                                // 固定 height(48dp) 会裁切（第 8 批已证伪），勿重试。
                                // 仅在有内容时出现，一键清空，不必用输入法逐字删除。
                                trailingIcon = {
                                    if (state.counterparty.isNotEmpty()) {
                                        // 用 36dp 紧凑点击区而非 48dp IconButton，
                                        // 否则清空图标本身会把输入框重新撑高。
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
                            // 第 6 批：空输入列最近常用，输入后改联想；快捷区上限可数。
                            val quick: List<String> = if (state.counterparty.isBlank())
                                counterparties.take(QUICK_COUNT) else suggestions.take(QUICK_COUNT)
                            if (quick.isNotEmpty()) {
                                // 第 10 批：第 9 批压成 28dp 裸 Text 是降噪过头，
                                // 点击区太小易误点。改 40dp Box + labelMedium。
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    quick.forEach { p ->
                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = 40.dp)
                                                .clickable { vm.setCounterparty(p) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = p,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = LedgerColors.Neutral
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 第 43 批：描述独立于对方与备注；placeholder-only、无 label 边框。
                    // 有值时明细列表主标题用它，空着则回退到交易对方。
                    FormRow("描述") {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = { vm.setDescription(it) },
                            placeholder = { Text("买了什么 / 这笔干嘛的（可选）") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        )
                    }

                    // 第 7 批：备注是低频项，默认收进「更多」；编辑已有备注的流水
                    // 在按钮上显示摘要，点开即可改，旧内容绝不会看不见。
                    val noteSummary = if (state.note.length > 18)
                        state.note.take(18) + "…" else state.note
                    // 第 8 批：TextButton 默认 48dp，改成紧凑可点行省出高度；
                    // 行尾 ⌄/⌃ 让「可展开」可见。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMore = !showMore }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                showMore -> "收起"
                                noteSummary.isBlank() -> "更多（备注 · 不计入收支）"
                                else -> "备注：$noteSummary · 更多"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (showMore) "⌃" else "⌄",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
                    }
                    if (showMore) {
                        // 备注与「不计入收支」合并进同一个 FormRow 的一行：
                        // 左边界仍对齐，展开只增加一行，无需上滑。
                        FormRow("备注") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = state.note,
                                    onValueChange = { vm.setNote(it) },
                                    placeholder = {
                                        Text(
                                            "可选",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                )
                                Text(
                                    text = "不计入",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                                Switch(
                                    checked = state.excludeFromStats,
                                    onCheckedChange = { vm.setExclude(it) }
                                )
                            }
                        }
                    }

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

    FormRow("账户", onRowClick = { query = ""; pickerTarget = 0 }) {
        if (type == RecordType.TRANSFER) {
            AccountQuickRow(
                tag = "转出",
                accounts = visible,
                selectedId = fromId,
                onSelect = onFrom,
                onShowAll = { query = ""; pickerTarget = 0 }
            )
            AccountQuickRow(
                tag = "转入",
                accounts = visible,
                selectedId = toId,
                onSelect = onTo,
                onShowAll = { query = ""; pickerTarget = 1 }
            )
        } else {
            val chosenId = if (type == RecordType.EXPENSE) fromId else toId
            val onSelect = if (type == RecordType.EXPENSE) onFrom else onTo
            AccountQuickRow(
                tag = "",
                accounts = visible,
                selectedId = chosenId,
                onSelect = onSelect,
                onShowAll = { query = ""; pickerTarget = 0 }
            )
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
