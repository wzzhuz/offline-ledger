package com.zwz.offlineledger.data.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 只在**全新库**创建时执行。第 30 批起清单优先读随包 JSON，
 * 读不到或解析失败才退回 SeedData 同构 fallback。
 */
class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val json = runCatching {
            context.assets.open("default_categories.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        val statements = if (json.isNullOrBlank()) {
            SeedData.allInsertStatements()
        } else {
            runCatching { SeedCatalog.insertStatements(json) }
                .getOrElse { SeedData.allInsertStatements() }
        }
        statements.forEach { db.execSQL(it) }
    }
}
