package com.zwz.offlineledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.data.model.RecordType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zwz.offlineledger.ui.assets.AccountQuickSortScreen
import com.zwz.offlineledger.ui.assets.AssetsScreen
import com.zwz.offlineledger.ui.budget.BudgetManagerScreen
import com.zwz.offlineledger.ui.detail.DetailScreen
import com.zwz.offlineledger.ui.detail.DetailViewModel
import com.zwz.offlineledger.ui.record.RecordEditorDialog
import com.zwz.offlineledger.ui.settings.BackupSettingsScreen
import com.zwz.offlineledger.ui.settings.CategoryManagerScreen
import com.zwz.offlineledger.ui.settings.CategoryPickerScreen
import com.zwz.offlineledger.ui.settings.CategorySortScreen
import com.zwz.offlineledger.ui.settings.FireflyImportScreen
import com.zwz.offlineledger.ui.settings.LedgerSettingsScreen
import com.zwz.offlineledger.ui.settings.PreferenceSettingsScreen
import com.zwz.offlineledger.ui.settings.SecuritySettingsScreen
import com.zwz.offlineledger.ui.settings.SettingsScreen
import com.zwz.offlineledger.ui.settings.SnapshotSettingsScreen
import com.zwz.offlineledger.ui.settings.TagManagerScreen
import com.zwz.offlineledger.ui.bulk.RuleListScreen
import com.zwz.offlineledger.ui.stats.StatsScreen
import com.zwz.offlineledger.ui.templates.TemplateManagerScreen
import com.zwz.offlineledger.ui.trash.TrashScreen
import com.zwz.offlineledger.core.prefs.VirtualBookStore
import com.zwz.offlineledger.ui.settings.VirtualBookSettingsScreen
import com.zwz.offlineledger.ui.virtual.VirtualBookScreen
import com.zwz.offlineledger.ui.virtual.VirtualBookSwitcher

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

/** 第 91 批：仅虚拟台账录入使用的预填上下文；普通记一笔与自动记账一律 null。 */
private data class VirtualEditorPreset(
    val type: RecordType,
    val tagNames: List<String>,
    val excludeFromStats: Boolean
)

private val MAIN_ROUTES = setOf("detail", "assets", "stats", "settings")

/** 恰好 4 个 Tab + 中央记账按钮；设置不是第 5 个 Tab（reporting-ui spec）。 */
@Composable
fun LedgerApp(startWithRecordEntry: Boolean = false) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val tabs = listOf(
        TabSpec("detail", "明细", Icons.Filled.List),
        TabSpec("assets", "资产", Icons.Filled.Home),
        TabSpec("stats", "统计", Icons.Filled.Star),
        TabSpec("settings", "设置", Icons.Filled.Settings)
    )

    var showEditor by remember { mutableStateOf(startWithRecordEntry) }
    var editorRecordId by remember { mutableStateOf<Long?>(null) }
    var editorPreset by remember { mutableStateOf<VirtualEditorPreset?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // 第 5 批：ViewModel 与滚动状态提升到 Activity 作用域。
    // 切到资产页核对再回来时，筛选条件与列表位置都保留，不用重设、不用重滚。
    val detailVm: DetailViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            DetailViewModel(
                ep.recordDao(), ep.accountDao(), ep.categoryDao(), ep.ledgerRepository()
            )
        }
    )
    val detailListState = rememberLazyListState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 第 23 批：导入 / 恢复发生在设置二级页，EventBus 让明细 / 资产 / 统计立即重查，
    // 不再需要「切月份再切回来」才触发刷新。
    LaunchedEffect(Unit) {
        DataRefreshBus.events.collect { refreshTick += 1 }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val today = LedgerClock.fromEpochMilli(System.currentTimeMillis()).localDate
            val n = LedgerDi.entryPoint(context).ledgerRepository().applyDueRules(today)
            if (n > 0) refreshTick += 1
        }.onFailure {
            android.util.Log.w("RecurringRules", "周期补记失败", it)
        }
    }

    fun openEditor(recordId: Long?, preset: VirtualEditorPreset? = null) {
        editorRecordId = recordId
        editorPreset = preset
        showEditor = true
    }

    fun goBack() {
        navController.popBackStack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 第 93 批：切换器不再常驻 4 个主 Tab（真机反馈太占位置）。
            // 只保留一层与状态栏等高的背景薄层做 edge-to-edge 避让，
            // 内容区比第 91 批多回一整行；入口改为明细页标题行里的一枚图标按钮。
            if (currentRoute in MAIN_ROUTES) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                )
            }
        },
        bottomBar = {
            // 第 4 批：二级页隐藏底栏，进入设置子页时更像「点开了一页」。
            if (currentRoute in MAIN_ROUTES) {
                NavigationBar {
                    val slots: List<TabSpec?> = listOf(tabs[0], tabs[1], null, tabs[2], tabs[3])
                    slots.forEach { tab ->
                        if (tab == null) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                FloatingActionButton(
                                    onClick = { openEditor(null) },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                                }
                            }
                        } else {
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) { launchSingleTop = true }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "detail",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("detail") {
                DetailScreen(
                    refreshTick = refreshTick,
                    vm = detailVm,
                    listState = detailListState,
                    onEdit = { id -> openEditor(id) },
                    virtualBookEntry = {
                        VirtualBookSwitcher(
                            onOpen = { book -> navController.navigate("virtual-book/${book.id}") },
                            onManage = { navController.navigate("virtual-book-settings") }
                        )
                    }
                )
            }
            composable("assets") {
                AssetsScreen(
                    refreshTick = refreshTick,
                    onOpenQuickSort = { navController.navigate("account-quick-sort") }
                )
            }
            // 第 60 批 Q4A：独立「全部账户排序」页，不改 4 Tab 结构。
            composable("account-quick-sort") {
                AccountQuickSortScreen(onBack = { goBack() })
            }
            composable("stats") {
                // 第 69 批 Q6A：三个跨 Tab 回调全删，统计页不再向明细 VM 注入状态；
                // 分类流水改由统计页内底部 sheet 展示（StatsDrillSheet）。
                // DetailViewModel 的 filterBy* 方法保留，供明细侧两个进行中 change 使用。
                StatsScreen(
                    refreshTick = refreshTick,
                    onOpenCategories = { navController.navigate("categories") }
                )
            }
            composable("settings") {
                SettingsScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable("backup") {
                BackupSettingsScreen(
                    onBack = { goBack() },
                    onOpenFirefly = { navController.navigate("firefly") },
                    onOpenSnapshots = { navController.navigate("snapshots") }
                )
            }
            composable("firefly") {
                FireflyImportScreen(
                    onBack = { goBack() },
                    onGoDetail = { navController.navigate("detail") }
                )
            }
            composable("snapshots") { SnapshotSettingsScreen(onBack = { goBack() }) }
            composable("bulk") { RuleListScreen(onBack = { goBack() }) }
            composable("security") { SecuritySettingsScreen(onBack = { goBack() }) }
            composable("prefs") { PreferenceSettingsScreen(onBack = { goBack() }) }
            composable("ledger") {
                LedgerSettingsScreen(
                    onBack = { goBack() },
                    onOpen = { route -> navController.navigate(route) }
                )
            }
            composable("categories") {
                CategoryManagerScreen(
                    onBack = { goBack() },
                    onOpenSort = { t -> navController.navigate("category-sort/" + t.name) },
                    onOpenPicker = { pid, t, m ->
                        navController.navigate(
                            "category-picker/${pid ?: -1L}/${t.name}/$m"
                        )
                    }
                )
            }
            composable("category-sort/{type}") { entry ->
                val t = runCatching {
                    RecordType.valueOf(entry.arguments?.getString("type") ?: "EXPENSE")
                }.getOrDefault(RecordType.EXPENSE)
                CategorySortScreen(type = t, onBack = { goBack() })
            }
            composable("category-picker/{parentId}/{type}/{mode}") { entry ->
                val pid = entry.arguments?.getString("parentId")
                    ?.toLongOrNull()?.takeIf { it > 0L }
                val t = runCatching {
                    RecordType.valueOf(entry.arguments?.getString("type") ?: "EXPENSE")
                }.getOrDefault(RecordType.EXPENSE)
                val m = entry.arguments?.getString("mode") ?: "add"
                CategoryPickerScreen(
                    parentId = pid, type = t, mode = m, onBack = { goBack() }
                )
            }
            composable("trash") {
                TrashScreen(onBack = { goBack() }, onChanged = { })
            }
            composable("templates") { TemplateManagerScreen(onBack = { goBack() }) }
            composable("budgets") { BudgetManagerScreen(onBack = { goBack() }) }
            // 第 75 批：独立标签管理页，二级页隐藏底栏。
            composable("tags") { TagManagerScreen(onBack = { goBack() }) }
            // 第 91 批：虚拟账本 = 标签聚合视图 + 设置页，零账本维度、零改表。
            composable("virtual-book/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                VirtualBookScreen(
                    bookId = id,
                    refreshTick = refreshTick,
                    onBack = { goBack() },
                    onRecord = { type ->
                        val store = VirtualBookStore(context)
                        val b = store.byId(id)
                        if (b != null) {
                            openEditor(
                                null,
                                VirtualEditorPreset(
                                    type = type,
                                    tagNames = listOf(b.tagName),
                                    excludeFromStats = store.excludeByDefault
                                )
                            )
                        }
                    },
                    // 第 93 批：台账流水复用全屏记一笔编辑态，不带任何预填。
                    onEditRecord = { recordId -> openEditor(recordId) }
                )
            }
            composable("virtual-book-settings") {
                VirtualBookSettingsScreen(
                    onBack = { goBack() },
                    onOpenBook = { id -> navController.navigate("virtual-book/$id") }
                )
            }
        }
    }

    if (showEditor) {
        RecordEditorDialog(
            recordId = editorRecordId,
            templateQuickPick = editorRecordId == null,
            presetType = editorPreset?.type,
            presetTagNames = editorPreset?.tagNames ?: emptyList(),
            presetExcludeFromStats = editorPreset?.excludeFromStats ?: false,
            onDismiss = { showEditor = false },
            onSaved = {
                showEditor = false
                refreshTick += 1
            }
        )
    }
}
