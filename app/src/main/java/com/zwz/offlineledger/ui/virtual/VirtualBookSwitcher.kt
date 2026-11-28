package com.zwz.offlineledger.ui.virtual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.prefs.VirtualBook
import com.zwz.offlineledger.core.prefs.VirtualBookStore

/**
 * 第 93 批：切换器从「四个主 Tab 常驻一整行」压缩成**明细页标题行里的一枚图标按钮**。
 *
 * 关键口径：**只切浏览范围，不携带录入归属**。
 * 切换后不会改变记一笔 FAB、不会改变自动记账落库位置；
 * 一羽/钱迹那种「切换后新账单自动进当前账本」在这里 MUST NOT 出现，
 * 否则「分享 → 0 次确认自动落库」会把一笔日常消费静默记进礼金台账。
 * 默认永远回主账本，不做跨启动记忆，杜绝隐式状态。
 *
 * 不再自带 Surface / statusBarsPadding / 说明文字：状态栏避让由 LedgerApp 的
 * 薄 topBar 统一处理，说明文字收进设置页，避免每个 Tab 都少一行内容区。
 */
@Composable
fun VirtualBookSwitcher(
    onOpen: (VirtualBook) -> Unit,
    onManage: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    var expanded by remember { mutableStateOf(false) }
    // 不用 remember 缓存列表：从设置页返回后重组即读到最新定义（SharedPreferences 很便宜）。
    val books = store.active()

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.List,
                contentDescription = "切换虚拟账本",
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (books.isEmpty()) {
                TextButton(onClick = { expanded = false; onManage() }) {
                    Text("还没有虚拟账本 · 去新建")
                }
            } else {
                books.forEach { book ->
                    TextButton(onClick = { expanded = false; onOpen(book) }) {
                        Text(book.name)
                    }
                }
            }
            TextButton(onClick = { expanded = false; onManage() }) {
                Text("管理虚拟账本")
            }
        }
    }
}
