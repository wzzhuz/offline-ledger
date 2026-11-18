package com.zwz.offlineledger.backup.snapshot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.zwz.offlineledger.ui.LedgerDi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每日自动快照。AlarmManager 是 framework 能力，零新增依赖、零权限；
 * 失败只写日志，绝不因为快照失败影响账本。
 */
class SnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SnapshotPrefs.autoEnabled(context)) return
        if (SnapshotPrefs.treeUri(context) == null) return
        val pass = SnapshotPrefs.loadPassphrase(context) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SnapshotWriter.write(app, LedgerDi.entryPoint(app).ledgerRepository(), pass)
            } catch (e: Exception) {
                android.util.Log.w("AutoSnapshot", "定时快照失败", e)
            } finally {
                pending.finish()
            }
        }
    }
}

object SnapshotScheduler {

    private const val REQUEST = 7717
    private val INTERVAL = AlarmManager.INTERVAL_DAY

    private fun operation(context: Context): PendingIntent {
        val i = Intent(context, SnapshotReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun enable(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL,
            INTERVAL,
            operation(context)
        )
    }

    fun disable(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(operation(context))
    }
}
