package com.zwz.offlineledger.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 显式迁移的唯一落点。
 *
 * 真实账本绝不能用 fallbackToDestructiveMigration（数据会全丢）。
 * 阶段 6a 首次引入 Migration(1, 2)：只建两张新表，不动原有 5 张表与索引。
 *
 * 关于 exportSchema：原注释建议「version 2 起设 true 并配 ksp schemaLocation」，
 * 那是给 MigrationTestHelper（androidTest 仪器测试）用的。本项目 CI 没有模拟器，
 * 跑不了仪器测试，导出 JSON 不产生验证价值，反而多改一处 Gradle 配置，
 * 故**保持 exportSchema = false**。迁移正确性改由三层兜底：
 *   ① check_transaction_annotations.py 核对 version / ALL / SQL 列名
 *   ② MigrationContractTest 纯 JVM 校验 CREATE TABLE 常量
 *   ③ 使用者真机从 v1 覆盖升级实测（CI 无法代替，不假报）
 *
 * SeedCallback.onCreate 仍只对全新库生效。6a 不新增预置分类 / 账户 / 模板，
 * 所以 migrate() 不需要补 seed 数据。
 */
object Migrations {
    // 列集合 / 类型 affinity / notNull / 主键必须与 RecordTemplateEntity 一致。
    // Room 按列名比较 TableInfo，列顺序不影响；Kotlin 默认值不产生 SQL DEFAULT，
    // 所以 SQL 里也不写 DEFAULT。字符串拼接常量（const val）便于单测与静态检查。
    const val SQL_1_2_CREATE_TEMPLATES = "CREATE TABLE IF NOT EXISTS `record_templates` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
            "`name` TEXT NOT NULL," +
            "`type` TEXT NOT NULL," +
            "`amountCents` INTEGER NOT NULL," +
            "`categoryId` INTEGER," +
            "`rootCategoryId` INTEGER," +
            "`fromAccountId` INTEGER," +
            "`toAccountId` INTEGER," +
            "`note` TEXT NOT NULL," +
            "`excludeFromStats` INTEGER NOT NULL," +
            "`sortOrder` INTEGER NOT NULL," +
            "`isArchived` INTEGER NOT NULL," +
            "`createdAt` INTEGER NOT NULL" +
            ")"

    const val SQL_1_2_CREATE_RULES = "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
            "`name` TEXT NOT NULL," +
            "`type` TEXT NOT NULL," +
            "`amountCents` INTEGER NOT NULL," +
            "`categoryId` INTEGER," +
            "`rootCategoryId` INTEGER," +
            "`fromAccountId` INTEGER," +
            "`toAccountId` INTEGER," +
            "`note` TEXT NOT NULL," +
            "`excludeFromStats` INTEGER NOT NULL," +
            "`cadence` TEXT NOT NULL," +
            "`dayOfMonth` INTEGER NOT NULL," +
            "`dayOfWeek` INTEGER NOT NULL," +
            "`anchorLocalDate` INTEGER NOT NULL," +
            "`lastGeneratedLocalDate` INTEGER," +
            "`isEnabled` INTEGER NOT NULL," +
            "`createdAt` INTEGER NOT NULL" +
            ")"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_1_2_CREATE_TEMPLATES)
            db.execSQL(SQL_1_2_CREATE_RULES)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
