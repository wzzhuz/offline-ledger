package com.zwz.offlineledger.data.startup

import android.content.Context
import java.io.File

/**
 * 启动快照的磁盘读写。
 *
 * 落 `context.noBackupFilesDir`：该目录天然不进 Auto Backup / 设备间转移，
 * 再配合 backup_rules.xml / data_extraction_rules.xml 的 `domain="file"` 排除双保险。
 * 写路径沿用 SnapshotWriter 已验证的 `.tmp → 回读校验 → 改名`，不留半截文件。
 *
 * 这是**一次性启动快照**，不是缓存层：换源后覆写一次，任何写事务提交立即删除。
 */
object StartupSnapshotStore {

    private const val TMP_SUFFIX = ".tmp"

    @Volatile
    private var appContext: Context? = null

    /** Application.onCreate 调用；只存 applicationContext，不触发开库、不做 IO。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        StartupSnapshotBus.register { delete() }
    }

    private fun dir(): File? = appContext?.noBackupFilesDir

    private fun finalFile(): File? = dir()?.let { File(it, StartupSnapshotCodec.FILE_NAME) }
    private fun tmpFile(): File? =
        dir()?.let { File(it, StartupSnapshotCodec.FILE_NAME + TMP_SUFFIX) }

    /** 读不到 / 解析失败 / 空行一律返回 null，调用方按判废回退骨架。 */
    fun read(): StartupSnapshot? {
        val f = finalFile() ?: return null
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        val snapshot = StartupSnapshotCodec.decode(text) ?: return null
        return snapshot.takeIf { it.rows.isNotEmpty() }
    }

    /** 只在真实首屏到达后调用一次；空列表不落盘。 */
    fun write(snapshot: StartupSnapshot) {
        val target = finalFile() ?: return
        val tmp = tmpFile() ?: return
        if (snapshot.rows.isEmpty()) {
            delete()
            return
        }
        runCatching {
            val text = StartupSnapshotCodec.encode(snapshot)
            tmp.writeText(text)
            // 回读校验：编码/解码任一环出问题就丢弃整个临时文件
            if (StartupSnapshotCodec.decode(tmp.readText()) == null) {
                tmp.delete()
                return@runCatching
            }
            if (!target.delete() && target.exists()) {
                tmp.delete()
                return@runCatching
            }
            if (!tmp.renameTo(target)) tmp.delete()
        }.onFailure { runCatching { tmp.delete() } }
    }

    fun delete() {
        runCatching { finalFile()?.delete() }
        runCatching { tmpFile()?.delete() }
    }
}
