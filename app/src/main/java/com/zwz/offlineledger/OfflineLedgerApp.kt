package com.zwz.offlineledger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfflineLedgerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SQLCipher 的 native 库必须在打开任何数据库之前加载。
        System.loadLibrary("sqlcipher")
    }
}
