package com.zwz.offlineledger

import android.app.Application
import com.zwz.offlineledger.core.StartupTrace
import dagger.hilt.android.HiltAndroidApp

/**
 * 实验分支：不再加载 sqlcipher.so、不再解密数据库口令。
 *
 * 连续打 0/1/2 三点是为了和 v0.2.30 同口径对照：
 * 加密版 0→1 是 so 加载、1→2 是 Keystore + AES-GCM；
 * 本分支这两段被消除，读数恒为 0ms，可直接看出省了多少。
 */
@HiltAndroidApp
class OfflineLedgerApp : Application() {
    override fun onCreate() {
        StartupTrace.mark(0)
        super.onCreate()
        StartupTrace.mark(1)
        StartupTrace.mark(2)
    }
}
