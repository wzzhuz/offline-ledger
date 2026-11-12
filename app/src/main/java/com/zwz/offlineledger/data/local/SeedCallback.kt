package com.zwz.offlineledger.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 只在**全新库**创建时执行。
 * 后续版本若新增预置分类，必须在 Migrations.migrate() 里补，
 * 不能指望本 Callback 重跑（踩坑清单 3.4.2）。
 */
class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SeedData.allInsertStatements().forEach { db.execSQL(it) }
    }
}
