package com.zwz.offlineledger.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zwz.offlineledger.ui.assets.AssetsScreen
import com.zwz.offlineledger.ui.detail.DetailScreen
import com.zwz.offlineledger.ui.record.RecordEditorDialog
import com.zwz.offlineledger.ui.settings.SettingsScreen
import com.zwz.offlineledger.ui.stats.StatsScreen

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

/** 恰好 4 个 Tab + 中央 + FAB；设置不是第 5 个 Tab（reporting-ui spec）。 */
@Composable
fun LedgerApp() {
    val navController = rememberNavController()
    val tabs = listOf(
        TabSpec("detail", "明细", Icons.Filled.List),
        TabSpec("assets", "资产", Icons.Filled.Home),
        TabSpec("stats", "统计", Icons.Filled.Star),
        TabSpec("settings", "设置", Icons.Filled.Settings)
    )

    var showEditor by remember { mutableStateOf(false) }
    var editorRecordId by remember { mutableStateOf<Long?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    fun openEditor(recordId: Long?) {
        editorRecordId = recordId
        showEditor = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                tabs.forEach { tab ->
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "detail",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("detail") {
                DetailScreen(
                    refreshTick = refreshTick,
                    onEdit = { id -> openEditor(id) }
                )
            }
            composable("assets") { AssetsScreen() }
            composable("stats") { StatsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }

    if (showEditor) {
        RecordEditorDialog(
            recordId = editorRecordId,
            onDismiss = { showEditor = false },
            onSaved = {
                showEditor = false
                refreshTick += 1
            }
        )
    }
}
