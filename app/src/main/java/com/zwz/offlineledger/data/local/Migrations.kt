package com.zwz.offlineledger.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 显式迁移的唯一落点。
 *
 * 真实账本绝不能用 fallbackToDestructiveMigration（数据会全丢）。
 * 阶段 6a 引入 Migration(1, 2)；阶段 6b 引入 Migration(2, 3)：
 * 建 budgets 表 + 给 records / trash 各加一列 linkedRecordId。
 * 阶段 6c-1 引入 Migration(3, 4)：给 records / trash 各加 currencyCode。
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

    const val SQL_2_3_CREATE_BUDGETS = "CREATE TABLE IF NOT EXISTS `budgets` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
            "`name` TEXT NOT NULL," +
            "`scope` TEXT NOT NULL," +
            "`categoryId` INTEGER," +
            "`limitCents` INTEGER NOT NULL," +
            "`sortOrder` INTEGER NOT NULL," +
            "`isArchived` INTEGER NOT NULL," +
            "`createdAt` INTEGER NOT NULL" +
            ")"

    // linkedRecordId 是可空 Long? -> INTEGER（无 NOT NULL、无 DEFAULT），
    // 与 SQLite ADD COLUMN 生成的列定义完全一致。
    // trash 同步加列：SETTLEMENT 删除后从回收站恢复不能丢关联。
    const val SQL_2_3_ALTER_RECORDS =
        "ALTER TABLE `records` ADD COLUMN `linkedRecordId` INTEGER"
    const val SQL_2_3_ALTER_TRASH =
        "ALTER TABLE `trash` ADD COLUMN `linkedRecordId` INTEGER"

    // 阶段 6c-1：给流水与回收站各加币种。
    // SQLite 对 NOT NULL 的 ADD COLUMN 强制要求 DEFAULT，否则旧行填不进去。
    const val SQL_3_4_ALTER_RECORDS =
        "ALTER TABLE `records` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'HKD'"
    const val SQL_3_4_ALTER_TRASH =
        "ALTER TABLE `trash` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'HKD'"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_1_2_CREATE_TEMPLATES)
            db.execSQL(SQL_1_2_CREATE_RULES)
        }
    }

    /** Room 会依次执行 1→2、2→3；两段都必须显式登记，否则升级即炸。 */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_2_3_CREATE_BUDGETS)
            db.execSQL(SQL_2_3_ALTER_RECORDS)
            db.execSQL(SQL_2_3_ALTER_TRASH)
        }
    }

    /** Room 会依次执行 1→2、2→3、3→4；三段都必须显式登记。 */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_3_4_ALTER_RECORDS)
            db.execSQL(SQL_3_4_ALTER_TRASH)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
