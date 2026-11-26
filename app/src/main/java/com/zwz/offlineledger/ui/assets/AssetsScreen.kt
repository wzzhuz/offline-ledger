package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.AccountTemplates
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.displayName
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.rememberKeyedReorder
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.reconcile.ReconcileDialog
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 模板清单数量随 Q5A 扩容自动跟随，MUST NOT 再硬编码 12。 */
private val AccountTemplatesCount: Int get() = AccountTemplates.ALL.size

/**
 * 第 59 批：资产账户页借鉴一羽记账的布局与密度。
 *
 * 借的是：蓝底顶部身份区、分组小竖条、图标 + 名称 + 金额右对齐的紧凑行、
 * 信用负债「无欠款 / 可用」副文案、底部拇指区主操作、聚焦的模板选择 sheet。
 * 不借的是：跨币种单一净资产总数、分组白卡 / 阴影、在线 IconFont 图标、
 * 备注 / 隐藏 / 默认使用等业务字段（见 design.md 七条基线兼容表）。
 *
 * 第 60 批拆两套排序（`sortOrder` / `quickOrder`）、模板正规化、`version = 6`；
 * 第 61 批补卡种编辑与记一笔卡号显示。头部注释随批同步，
 * MUST NOT 继续写「数据侧零改动 / version = 5 / 可覆盖安装」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(refreshTick: Int = 0, onOpenQuickSort: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: AssetsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            AssetsViewModel(ep.accountDao(), ep.ledgerRepository(), LedgerPrefs(context))
        }
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    /** 第 25 批：资产页专用刷新标志，与明细页 / 首屏加载三分离，避免双转圈。 */
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var reconcileId by remember { mutableStateOf<Long?>(null) }
    var editAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<AccountEntity?>(null) }

    // 第 20 批：资产页 LazyColumn + reorderable 3.1.0，本批原样保留。
    val listState = rememberLazyListState()
    val visible = state.accounts.filter { state.showArchived || !it.isArchived }
    val activeIds = visible.filter { !it.isArchived }.map { it.id }
    val accountOrder = rememberKeyedReorder(
        ids = activeIds,
        prefix = "acc",
        onCommit = { ids -> vm.commitAccountOrderAll(ids) }
    )
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        accountOrder.move(from.key, to.key)
    }

    LaunchedEffect(refreshTick) { vm.reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                vm.reload()
                scope.launch {
                    delay(420)
                    isRefreshing = false
                    snackbarHostState.showSnackbar("已刷新资产 · 账户与余额已重算")
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                item(key = "assets-header") {
                    AssetHeader(
                        state = state,
                        onToggleArchived = { vm.toggleShowArchived() },
                        onAdd = { addError = null; showAdd = true },
                        onOpenQuickSort = onOpenQuickSort
                    )
                }
                AccountType.entries.forEach { type ->
                    val group = visible.filter { it.type == type }
                    item(key = "section-" + type.name) {
                        Column {
                            AssetSectionHeader(type.displayName(), group.size)
                            if (group.isEmpty()) {
                                EmptyHint("还没有" + type.displayName() + "账户")
                            }
                        }
                    }
                    items(
                        accountOrder.ids.mapNotNull { id ->
                            group.firstOrNull { it.id == id && !it.isArchived }
                        },
                        key = { accountOrder.keyOf(it.id) }
                    ) { account ->
                        ReorderableItem(reorderState, key = accountOrder.keyOf(account.id)) { _ ->
                            // 第 59 批：拖柄挂整格，MUST NOT 再画三道横线「≡」小手柄。
                            val dragHandle = with(this) {
                                Modifier.longPressDraggableHandle(
                                    onDragStopped = { accountOrder.commit() }
                                )
                            }
                            AccountCard(
                                account = account,
                                dragModifier = dragHandle,
                                onDetail = { detailId = account.id },
                                onReconcile = { reconcileId = account.id },
                                onEdit = { editError = null; editAccount = account },
                                onDelete = { deleting = account },
                                onMove = { d -> scope.launch { vm.moveAccount(account.id, d) } },
                                onArchived = { a -> scope.launch { vm.setArchived(account.id, a) } }
                            )
                        }
                    }
                    // 已归档账户不参与拖拽，仍按列表展示。
                    group.filter { it.isArchived }.forEach { account ->
                        item(key = "archived-account-" + account.id) {
                            AccountCard(
                                account = account,
                                onDetail = { detailId = account.id },
                                onReconcile = { reconcileId = account.id },
                                onEdit = { editError = null; editAccount = account },
                                onDelete = { deleting = account },
                                onMove = { d -> scope.launch { vm.moveAccount(account.id, d) } },
                                onArchived = { a -> scope.launch { vm.setArchived(account.id, a) } }
                            )
                        }
                    }
                }
                item(key = "assets-footer") {
                    Text(
                        text = "总负债仅作展示；各币种小计 = SUM(计入统计账户余额)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAdd) {
        AddAccountDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, type, initial, limit, last4, cardKind ->
                scope.launch {
                    val err = vm.addAccount(name, type, initial, limit, last4, cardKind)
                    if (err == null) { showAdd = false; addError = null } else addError = err
                }
            },
            errorText = addError
        )
    }

    editAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editAccount = null },
            onConfirm = { name, type, initial, limit, cc, include, last4, cardKind, iconRef ->
                scope.launch {
                    val err = vm.editAccount(
                        account, name, type, initial, limit, cc, include, last4, cardKind, iconRef
                    )
                    if (err == null) { editAccount = null; editError = null } else editError = err
                }
            },
            errorText = editError
        )
    }

    deleting?.let { account ->
        // 第 60 批 Q2A：真机反馈「点删除没弹窗」。除了在 AccountCard 里用
        // pendingDelete + LaunchedEffect(menu) 把触发与 DropdownMenu dismiss 解耦，
        // 弹窗本身也补上账户名 / 余额 / 红字警示 /「确认删除」四个明确要素。
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账户") },
            text = {
                Column {
                    // 第 62 批：删除确认也要能看出删的是哪张卡。
                    Text("账户：" + accountBaseLabel(account), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "当前余额：" + formatCents(account.balanceCents),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠ 有流水的账户不能删除，只能归档；此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.ExpenseRed
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.deleteAccount(account.id) }
                    deleting = null
                }) { Text("确认删除", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }

    detailId?.let { id ->
        AccountDetailDialog(
            accountId = id,
            // 第 62 批：账户详情标题带卡号，多张同名卡不混淆。
            accountName = state.accounts.firstOrNull { it.id == id }
                ?.let { accountBaseLabel(it) } ?: "账户详情",
            onDismiss = { detailId = null },
            onChanged = { vm.reload() }
        )
    }

    reconcileId?.let { id ->
        ReconcileDialog(
            accountId = id,
            onDismiss = { reconcileId = null },
            onDone = { vm.reload() }
        )
    }
}

/**
 * 第 59 批：蓝底顶部身份区。
 * MUST NOT 把 state.groups 汇总成跨币种的单一「净资产」总数
 * （基线 reporting-ui「资产页按币种分组且移除单一净资产总数」）。
 */
@Composable
private fun AssetHeader(
    state: AssetsUiState,
    onToggleArchived: () -> Unit,
    onAdd: () -> Unit,
    onOpenQuickSort: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LedgerColors.CardSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("资产账户", style = MaterialTheme.typography.titleLarge, color = LedgerColors.OnSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "（" + state.accounts.count { !it.isArchived } + "）",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.OnSurface.copy(alpha = 0.72f)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleArchived) {
                Text(
                    text = if (state.showArchived) "隐藏已归档" else "含已归档",
                    color = LedgerColors.OnSurface
                )
            }
            TextButton(onClick = onAdd) { Text("+ 新增", color = LedgerColors.OnSurface) }
        }
        Spacer(Modifier.height(6.dp))
        if (state.groups.isEmpty()) {
            Text(
                text = "还没有账户",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.OnSurface.copy(alpha = 0.72f)
            )
        } else {
            state.groups.forEach { g ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Q1A：组内叫「净资产」；仍是逐币种分组，
                        // MUST NOT 汇总成跨币种单一总数（基线 reporting-ui）。
                        Text(
                            text = g.currencyCode + " · 净资产",
                            style = MaterialTheme.typography.titleMedium,
                            color = LedgerColors.OnSurface
                        )
                        Text(
                            text = formatCents(g.netCents) + " " + g.currencyCode,
                            style = MaterialTheme.typography.titleMedium,
                            color = LedgerColors.OnSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "资产 +" + formatCents(g.assetsCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.OnSurface.copy(alpha = 0.72f)
                        )
                        // Q1A：负债恢复红字 + 负号。CurrencyGroup.liabilitiesCents
                        // 仍是 displayTotals 给的正绝对值，负号纯展示、不参与运算；
                        // 白卡底上直接用语义支出红，深色下自动为柔和珊瑚红。
                        Text(
                            text = "负债 -" + formatCents(g.liabilitiesCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.ExpenseRed
                        )
                    }
                }
            }
        }
        // Q4A：全部账户排序页入口固定在顶部身份区，不新增第 5 个 Tab。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onOpenQuickSort) {
                Text("记一笔优先排序 ›", color = LedgerColors.OnSurface)
            }
        }
    }
}

/** 分组头：蓝色小竖条 + 分组名 + 账户数；不显示跨币种聚合金额。 */
@Composable
private fun AssetSectionHeader(text: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(CategoryHeaderBlue, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString() + " 个账户",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/**
 * 第 59 批：紧凑账户行。
 * - 仍是纯行 + `ListDivider()`，MUST NOT 用 Card / 阴影（基线）。
 * - 5 个横排 MiniAction 删除，7 项操作收进单「⋮」菜单。
 * - 图标复用 `CategoryIcon`（中性圆 + 首字），零新增资源、零在线下载。
 * - 拖柄挂整格，MUST NOT 再显示「≡」。
 */
@Composable
private fun AccountCard(
    account: AccountEntity,
    onDetail: () -> Unit,
    onReconcile: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onArchived: (Boolean) -> Unit,
    dragModifier: Modifier? = null
) {
    var menu by remember { mutableStateOf(false) }
    // 第 60 批 Q2A：DropdownMenu 的 Popup dismiss 与 AlertDialog 新窗口同帧会竞争，
    // 真机表现为「点删除没弹窗」。把触发延迟到 menu 完全关闭之后。
    var pendingDelete by remember { mutableStateOf(false) }
    LaunchedEffect(menu) {
        if (!menu && pendingDelete) {
            pendingDelete = false
            onDelete()
        }
    }

    // 第 60 批 Q3A：cardLast4 是单一真相源；旧库 name 已含 " (1122)" 时
    // 不再追加，MUST NOT 出现「中行储蓄卡(1122)(1122)」。
    // 第 61 批：与记一笔 / 独立排序页共用同一组合点，旧库后缀去重逻辑只留一份。
    val shownName = accountBaseLabel(account) +
        " · " + account.currencyCode +
        if (account.isArchived) "（已归档）" else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier ?: Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountIconBox(
                name = account.name,
                cardKind = account.cardKind,
                iconKey = AccountIconCatalog.iconKeyFor(account.name),
                iconRef = account.iconRef,
                size = 36
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shownName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val credit = Reconciliation.availableCredit(
                    account.creditLimitCents, account.balanceCents
                )
                val extra = buildList {
                    if (account.type == AccountType.LIABILITY) {
                        if (account.balanceCents == 0L) add("无欠款")
                        credit?.let { add("可用 " + formatCents(it)) }
                    } else if (account.type != AccountType.ASSET) {
                        add(account.type.displayName())
                    }
                    if (!account.includeInTotal) add("不计入净资产")
                }.joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = formatCents(account.balanceCents),
                style = MaterialTheme.typography.titleSmall,
                color = if (account.balanceCents < 0) LedgerColors.ExpenseRed
                else LedgerColors.IncomeGreen
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("详情") },
                        onClick = { menu = false; onDetail() })
                    DropdownMenuItem(text = { Text("对账") },
                        onClick = { menu = false; onReconcile() })
                    DropdownMenuItem(text = { Text("编辑") },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (account.isArchived) "恢复" else "归档") },
                        onClick = { menu = false; onArchived(!account.isArchived) }
                    )
                    DropdownMenuItem(text = { Text("删除", color = LedgerColors.ExpenseRed) },
                        onClick = { menu = false; pendingDelete = true })
                    DropdownMenuItem(text = { Text("上移") },
                        onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("下移") },
                        onClick = { menu = false; onMove(1) })
                }
            }
        }
        ListDivider(Modifier.padding(horizontal = 16.dp))
    }
}

/** 第 59 批：新建账户页分组标题（蓝色小竖条 + 文本）。 */
@Composable
private fun AssetFormSectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(CategoryHeaderBlue, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * 第 59 批：新建账户从 AlertDialog 小弹窗改近全屏 Dialog。
 * 字段与顺序不变（名称 / 卡号后四位 / 类型 / 期初余额 / 信用额度），
 * `onConfirm` 签名与 `AssetsViewModel.addAccount` 零改动。
 * 不新增备注 / 隐藏 / 默认使用 / 图标字段，不升表、不加路由。
 */
@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, AccountType, String, String, String, String?) -> Unit,
    errorText: String?
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.ASSET) }
    var initial by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var cardKind by remember { mutableStateOf<String?>(null) }
    var showTemplateSheet by remember { mutableStateOf(false) }

    val save = { onConfirm(name, type, initial, limit, last4, cardKind) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LedgerColors.Background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LedgerColors.CardSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.KeyboardArrowLeft,
                                contentDescription = "返回",
                                tint = LedgerColors.OnSurface
                            )
                        }
                        Text(
                            text = "添加资产账户",
                            style = MaterialTheme.typography.titleLarge,
                            color = LedgerColors.OnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = save) { Text("保存", color = LedgerColors.OnSurface) }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        AssetFormSectionTitle("账户信息")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LedgerColors.CardSurface, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIcon(
                                name = name.ifBlank { "账" },
                                colorHex = null,
                                size = 44
                            )
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = {
                                    Text("账户名称", style = MaterialTheme.typography.bodyMedium)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            )
                        }
                        Text(
                            text = "点下方「从模板选择」可快速填入名称与类型",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        OutlinedTextField(
                            value = last4,
                            onValueChange = { last4 = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = {
                                Text("卡号后四位（可选）", style = MaterialTheme.typography.bodyMedium)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        )

                        AssetFormSectionTitle("账户类型")
                        AccountType.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { t ->
                                    FilterChip(
                                        selected = type == t,
                                        onClick = { type = t },
                                        label = { Text(t.displayName()) }
                                    )
                                }
                            }
                        }

                        // 第 61 批：自定义名称 / 导入来的账户也能设卡种，
                        // 不再只有从模板建卡时才带 DEBIT / CREDIT。
                        AssetFormSectionTitle("卡种（可选）")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(null to "不设", "DEBIT" to "储蓄卡", "CREDIT" to "信用卡")
                                .forEach { (k, label) ->
                                    FilterChip(
                                        selected = cardKind == k,
                                        onClick = { cardKind = k },
                                        label = { Text(label) }
                                    )
                                }
                        }

                        AssetFormSectionTitle("余额")
                        OutlinedTextField(
                            value = initial,
                            onValueChange = { initial = it },
                            placeholder = {
                                Text("期初余额（元，默认 0）", style = MaterialTheme.typography.bodyMedium)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        )
                        if (type == AccountType.LIABILITY) {
                            OutlinedTextField(
                                value = limit,
                                onValueChange = { limit = it },
                                placeholder = {
                                    Text("信用额度（元，可空）", style = MaterialTheme.typography.bodyMedium)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            )
                        }

                        AssetFormSectionTitle("快捷模板")
                        Button(
                            onClick = { showTemplateSheet = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("从模板选择（" + AccountTemplatesCount + " 个）")
                        }

                        errorText?.let {
                            Text(
                                text = it,
                                color = LedgerColors.ExpenseRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // 底部拇指区主操作：不随表单滚动，键盘弹起仍在可见区。
                    Button(
                        onClick = save,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightIn(min = 52.dp)
                    ) {
                        Text("保存")
                    }
                }

                if (showTemplateSheet) {
                    AccountTemplateSheet(
                        onDismiss = { showTemplateSheet = false },
                        onPick = { t ->
                            name = t.name
                            type = t.type
                            cardKind = t.cardKind
                            showTemplateSheet = false
                        }
                    )
                }
            }
        }
    }
}

/** 第三批：账户类型可改，解决「类型选错只能重建」。本批零改动。 */
@Composable
private fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, AccountType, String, String, String, Boolean, String, String?, String?) -> Unit,
    errorText: String?
) {
    // 第 60 批 Q3A：旧库 name 可能已含 " (1122)"，初始化时剥掉，
    // 否则编辑一次会把历史后缀写回 name，与展示层组合成双后缀。
    var name by remember {
        mutableStateOf(stripLegacyCardSuffix(account.name, account.cardLast4))
    }
    // 第 61 批 Q3：编辑页补「卡种」状态。原实现把 account.cardKind 写死传给
    // onConfirm，就算有选择器也存不回去——这才是「编辑也选择不了」的真根因。
    var cardKind by remember { mutableStateOf(account.cardKind) }
    var last4 by remember { mutableStateOf(account.cardLast4 ?: "") }
    var type by remember { mutableStateOf(account.type) }
    var initial by remember { mutableStateOf(Cents(account.initialBalanceCents).toYuanString()) }
    var limit by remember { mutableStateOf(
        account.creditLimitCents?.let { Cents(it).toYuanString() } ?: ""
    ) }
    var currency by remember { mutableStateOf(account.currencyCode) }
    var include by remember { mutableStateOf(account.includeInTotal) }
    var expanded by remember { mutableStateOf(false) }
    // 第 82 批：账户图标关联（预制 / 本机导入 / 自动），随「保存」一次落库。
    var iconRef by remember { mutableStateOf(account.iconRef) }
    var showPreset by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        AccountCustomIcons.import(context, uri)
            .onSuccess { iconRef = it; importError = null; showPreset = false }
            .onFailure { importError = it.message ?: "导入失败" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("账户名称", style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                )
                // 第 60 批 Q3A：编辑页补上卡号输入，修掉「改不了卡号信息」。
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = last4,
                    onValueChange = { last4 = it.filter { ch -> ch.isDigit() }.take(4) },
                    placeholder = { Text("卡号后四位（可选）", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    singleLine = true
                )
                Column {
                    AccountType.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(t.displayName()) }
                                )
                            }
                        }
                    }
                }
                // 第 61 批：编辑页补卡种三态选择，导入 / 自定义账户也能补上
                //「储」「信」小标志；选中值由 cardKind 传给 onConfirm 落库。
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "不设", "DEBIT" to "储蓄卡", "CREDIT" to "信用卡")
                        .forEach { (k, label) ->
                            FilterChip(
                                selected = cardKind == k,
                                onClick = { cardKind = k },
                                label = { Text(label) }
                            )
                        }
                }
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = initial, onValueChange = { initial = it },
                    placeholder = { Text("期初余额（元）", style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                )
                if (type == AccountType.LIABILITY) {
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyMedium,
                    value = limit, onValueChange = { limit = it },
                        placeholder = { Text("信用额度（元，可空）", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                    )
                }
                Box {
                    TextButton(onClick = { expanded = true }) { Text("币种：$currency") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LedgerCurrency.SUPPORTED.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code) },
                                onClick = { currency = code; expanded = false }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("计入净资产", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = include, onCheckedChange = { include = it }
                    )
                }
                ListDivider()
                Text(
                    "账户图标",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountIconBox(
                        name = name,
                        cardKind = cardKind,
                        iconKey = AccountIconCatalog.iconKeyFor(name),
                        iconRef = iconRef,
                        size = 40
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showPreset = !showPreset }) { Text("预置") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("image/png")) }) {
                        Text("导入 PNG")
                    }
                    TextButton(onClick = {
                        iconRef = null; showPreset = false; importError = null
                    }) { Text("自动") }
                }
                if (showPreset) {
                    val keys = remember {
                        AccountTemplates.ALL.mapNotNull { it.iconKey }.distinct()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        keys.chunked(5).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { k ->
                                    val res = AccountIconCatalog.drawableFor(k)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (iconRef == "preset:$k")
                                                    LedgerColors.Primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                iconRef = "preset:$k"
                                                showPreset = false
                                                importError = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (res != null) {
                                            Icon(
                                                painter = painterResource(res),
                                                contentDescription = null,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                importError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.ExpenseRed
                    )
                }
                Text(
                    "导入的图标仅保存在本机，不上传、不进备份；请使用透明底 PNG。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                Text(
                    text = "改期初余额或类型会按流水重新计算余额；改币种只改标签。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                errorText?.let { Text(it, color = LedgerColors.ExpenseRed) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, type, initial, limit, currency, include, last4, cardKind, iconRef)
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}


