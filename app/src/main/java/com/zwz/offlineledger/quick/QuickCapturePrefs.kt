package com.zwz.offlineledger.quick

import android.content.Context

/**
 * 第 49 批：白名单与开关走 SharedPreferences，不进 Room、不进备份 DTO、不改表。
 * 白名单之外的包名一律不解析、不截图。
 */
class QuickCapturePrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("quick_capture_prefs", Context.MODE_PRIVATE)

    val defaultPackages: Set<String> = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "com.chinamworld.main"
    )

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    var nodeCacheEnabled: Boolean
        get() = sp.getBoolean(KEY_NODE_CACHE, true)
        set(v) { sp.edit().putBoolean(KEY_NODE_CACHE, v).apply() }

    fun packages(): Set<String> =
        sp.getStringSet(KEY_PACKAGES, null) ?: defaultPackages

    fun setPackages(items: Set<String>) {
        sp.edit().putStringSet(KEY_PACKAGES, items).apply()
    }

    fun isAllowed(packageName: String): Boolean =
        enabled && packageName in packages()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NODE_CACHE = "node_cache"
        private const val KEY_PACKAGES = "packages"
    }
}
