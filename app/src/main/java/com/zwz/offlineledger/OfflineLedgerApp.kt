package com.zwz.offlineledger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。阶段 0 用最小闭环验证 Hilt + KSP 的代码生成能跑通，
 * 阶段 1 起在这里接入加密数据库的初始化。
 */
@HiltAndroidApp
class OfflineLedgerApp : Application()
