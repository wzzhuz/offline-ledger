package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 4 批：设置页重排为分组列表 → 二级页。
 * 不再把十几个 Button、输入框、下拉堆在一屏；每组只留一行入口。
 * 4 Tab 导航结构不变，设置仍是第 4 个 Tab。
 */
@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "按分组进入各自页面；所有数据只存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )

        // 第 15 批：按使用频率重排（账本 → 数据与备份 → 偏好 → 安全 → 关于）。
        SettingGroupCard(
            title = "账本",
            summary = "分类 / 模板 / 预算 / 回收站 / 健康自检"
        ) { onOpen("ledger") }
        SettingGroupCard(
            title = "数据与备份",
            summary = "加密备份 / CSV 明文导出 / 账单导入 / Firefly III 迁移"
        ) { onOpen("backup") }
        // 第 38 批：规则提为设置一级入口，紧跟「数据与备份」；复用已有 bulk 路由，零新增 NavHost 路由。
        SettingGroupCard(
            title = "规则列表",
            summary = "保存的规则 / 编辑 / 应用到历史 / 一键撤销"
        ) { onOpen("bulk") }
        SettingGroupCard(
            title = "偏好",
            summary = "对账阈值 / 配色 / 默认币种 / 默认账户"
        ) { onOpen("prefs") }
        SettingGroupCard(
            title = "安全",
            summary = "应用锁 / PIN / 最近任务防泄露"
        ) { onOpen("security") }

        SectionTitle("关于")
        Text(
            text = "隐私基线：不声明 INTERNET 权限 · 关闭系统自动备份并排除数据库 · " +
                "SQLCipher 加密数据库 · 口令由 Android Keystore 保管 · " +
                "备份口令不落盘，与数据库口令完全独立。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}
