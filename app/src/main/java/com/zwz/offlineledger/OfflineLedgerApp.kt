package com.zwz.offlineledger

import android.app.Application
import com.zwz.offlineledger.core.StartupTrace
import com.zwz.offlineledger.data.startup.StartupSnapshotStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfflineLedgerApp : Application() {
    override fun onCreate() {
        StartupTrace.mark(0)
        super.onCreate()
        // 只存 applicationContext 并注册失效钩子，不触发开库、不做磁盘 IO。
        StartupSnapshotStore.init(this)
        // SQLCipher 的 native 库必须在打开任何数据库之前加载。
        System.loadLibrary("sqlcipher")
        StartupTrace.mark(1)
    }
}
