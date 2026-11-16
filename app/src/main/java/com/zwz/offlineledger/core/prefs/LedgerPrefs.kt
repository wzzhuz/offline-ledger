package com.zwz.offlineledger.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.engine.Reconciliation
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 偏好走 SharedPreferences：不进 Room、不进备份 DTO、不触发 Migration。
 * 换设备后重新设置即可，避免为一个布尔值把设置项混进账本。
 */
class LedgerPrefs(context: Context) {

    private val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 尾差阈值，单位分。UI / ViewModel / Repository 三层 MUST 读同一个值。 */
    var reconcileThresholdCents: Long
        get() = sp.getLong(KEY_THRESHOLD, Reconciliation.TAIL_DIFFERENCE_THRESHOLD_CENTS)
            .coerceIn(0L, MAX_THRESHOLD_CENTS)
        set(value) = sp.edit()
            .putLong(KEY_THRESHOLD, value.coerceIn(0L, MAX_THRESHOLD_CENTS)).apply()

    var reverseColors: Boolean
        get() = sp.getBoolean(KEY_REVERSE_COLORS, false)
        set(value) = sp.edit().putBoolean(KEY_REVERSE_COLORS, value).apply()

    var flagSecureEnabled: Boolean
        get() = sp.getBoolean(KEY_FLAG_SECURE, false)
        set(value) = sp.edit().putBoolean(KEY_FLAG_SECURE, value).apply()

    var appLockEnabled: Boolean
        get() = sp.getBoolean(KEY_APP_LOCK, false)
        set(value) = sp.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** 阶段 7：默认币种，只影响之后新建的账户，不自动改已有数据。 */
    var defaultCurrency: String
        get() = sp.getString(KEY_DEFAULT_CURRENCY, null) ?: LedgerCurrency.DEFAULT
        set(value) = sp.edit().putString(KEY_DEFAULT_CURRENCY, value).apply()

    /** 第 6 批：默认账户，记一笔与模板填充的账户兜底；0 = 未设置。只存 id，不进备份。 */
    var defaultAccountId: Long
        get() = sp.getLong(KEY_DEFAULT_ACCOUNT_ID, 0L)
        set(value) = sp.edit().putLong(KEY_DEFAULT_ACCOUNT_ID, value).apply()

    fun hasPin(): Boolean = sp.getString(KEY_PIN_HASH, null)?.isNotBlank() == true

    /**
     * 加盐 SHA-256。防的是「明文存 PIN」与彩虹表，
     * 不是对抗有物理接触的攻击者——账本本体由 SQLCipher + Keystore 保护。
     */
    fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() }) { "PIN 需为 4–12 位数字" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash(salt.toHex() + ":" + pin))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = sp.getString(KEY_PIN_SALT, null) ?: return false
        val stored = sp.getString(KEY_PIN_HASH, null) ?: return false
        val candidate = hash(salt + ":" + pin)
        return constantTimeEquals(stored, candidate)
    }

    fun clearPin() {
        sp.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
        appLockEnabled = false
    }

    private fun hash(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        private const val PREFS_NAME = "offline_ledger_prefs"
        private const val KEY_THRESHOLD = "reconcile_threshold_cents"
        private const val KEY_REVERSE_COLORS = "reverse_colors"
        private const val KEY_FLAG_SECURE = "flag_secure"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_DEFAULT_CURRENCY = "default_currency"
        private const val KEY_DEFAULT_ACCOUNT_ID = "default_account_id"
        const val MAX_THRESHOLD_CENTS = 100_000L // 1000.00 元
    }
}
