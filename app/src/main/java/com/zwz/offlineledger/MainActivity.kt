package com.zwz.offlineledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.time.LedgerClock
import dagger.hilt.android.AndroidEntryPoint

/**
 * 阶段 0 占位页：证明工程骨架、Compose、Hilt 与基础类型能一起编译运行。
 * 阶段 2 会替换为 4 Tab + 中央记账按钮的真实结构。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "离线账本")
                        Text(text = "阶段 0 骨架 · 无网络权限")
                        Text(text = "金额示例：${Cents.fromYuan("0.01").toYuanString()} 元")
                        Text(text = "默认币种：${LedgerCurrency.DEFAULT}")
                        Text(text = "今日：${LedgerClock.fromEpochMilli(System.currentTimeMillis()).localDate}")
                    }
                }
            }
        }
    }
}
