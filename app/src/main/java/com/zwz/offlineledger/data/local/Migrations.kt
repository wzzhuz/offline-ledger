package com.zwz.offlineledger.data.local

import androidx.room.migration.Migration

/**
 * 显式迁移的唯一落点。
 *
 * 真实账本绝不能用 fallbackToDestructiveMigration（数据会全丢）。
 * 从 version 2 起：
 *   1. 在 AppDatabase 的 @Database(version = ...) 递增，并设 exportSchema = true
 *   2. 在 build.gradle.kts 配 ksp { arg("room.schemaLocation", "$projectDir/schemas") }
 *   3. 把 Migration(1, 2) 等对象加入 ALL，并在 builder 上 addMigrations(*ALL)
 *   4. 若新增预置分类/账户，在本文件的 migrate() 里补，不依赖 SeedCallback
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
