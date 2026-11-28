package com.zwz.offlineledger.core.prefs

import android.content.Context

/**
 * 第 91 批：虚拟账本定义与开关走 SharedPreferences，和 accountPickerLayout 同口径。
 *
 * 不进 Room / 不进备份 DTO / 不触发 Migration。换设备或清数据后视图列表回空，
 * 但流水上的「台账:」标签仍随加密备份往返，设置页重建同名视图即可恢复查看。
 */
class VirtualBookStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<VirtualBook> =
        VirtualBookCodec.normalize(VirtualBookCodec.decode(sp.getString(KEY_BOOKS, null).orEmpty()))

    fun active(): List<VirtualBook> = all().filter { !it.archived }

    fun byId(id: String): VirtualBook? = all().firstOrNull { it.id == id }

    fun save(list: List<VirtualBook>) {
        sp.edit().putString(KEY_BOOKS, VirtualBookCodec.encode(VirtualBookCodec.normalize(list))).apply()
    }

    fun add(name: String): VirtualBook? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = all().toMutableList()
        if (current.any { it.name == trimmed }) return null
        val book = VirtualBook(
            id = VirtualBookCodec.newId(),
            name = trimmed,
            tagName = VirtualBookCodec.tagFor(trimmed),
            sortOrder = current.size
        )
        current += book
        save(current)
        return book
    }

    fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        save(all().map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    fun setArchived(id: String, archived: Boolean) {
        save(all().map { if (it.id == id) it.copy(archived = archived) else it })
    }

    fun move(id: String, delta: Int) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        save(list)
    }

    /**
     * 台账录入默认勾「不计入收支」，主账本月度收入/支出因此零污染。
     * 关掉时设置页会提示「会让月度收入虚高」，由使用者自行承担口径。
     */
    var excludeByDefault: Boolean
        get() = sp.getBoolean(KEY_EXCLUDE_BY_DEFAULT, true)
        set(value) = sp.edit().putBoolean(KEY_EXCLUDE_BY_DEFAULT, value).apply()

    /** 所有台账标签前缀，供主账本明细排除与静态断言共用。 */
    val tagPrefix: String get() = VirtualBookCodec.TAG_PREFIX

    private companion object {
        const val PREFS_NAME = "virtual_books"
        const val KEY_BOOKS = "books"
        const val KEY_EXCLUDE_BY_DEFAULT = "exclude_by_default"
    }
}
