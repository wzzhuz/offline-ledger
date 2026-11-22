package com.zwz.offlineledger

import android.app.Application
import com.zwz.offlineledger.core.StartupTrace
import dagger.hilt.android.HiltAndroidApp

/**
 * 第 50 批：不再加载 sqlcipher.so、不再解密数据库口令。
 * 明文 Room 由 DatabaseModule 构建，Application 只留最轻的启动打点。
 */
@HiltAndroidApp
class OfflineLedgerApp : Application() {
    override fun onCreate() {
        StartupTrace.mark(0)
        super.onCreate()
        StartupTrace.mark(1)
    }
}
