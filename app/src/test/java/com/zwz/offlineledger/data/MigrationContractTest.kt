package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.Migrations
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 契约测试：CI 没有模拟器，跑不了 MigrationTestHelper，
 * 这里只能证明「冻结的 CREATE TABLE 常量写了该写的列」。
 * 使用者明确「稳定版前不考虑升级迁移」，故 ALL 为空是**预期行为**；
 * SQL 常量保留仅供历史追溯，运行时不执行。
 */
class MigrationContractTest {

    private val templateCols = listOf(
        "id", "name", "type", "amountCents", "categoryId", "rootCategoryId",
        "fromAccountId", "toAccountId", "note", "excludeFromStats",
        "sortOrder", "isArchived", "createdAt"
    )
    private val ruleCols = listOf(
        "id", "name", "type", "amountCents", "categoryId", "rootCategoryId",
        "fromAccountId", "toAccountId", "note", "excludeFromStats",
        "cadence", "dayOfMonth", "dayOfWeek", "anchorLocalDate",
        "lastGeneratedLocalDate", "isEnabled", "createdAt"
    )

    @Test
    fun migrations_are_frozen_before_stable_release() {
        // 稳定版前不注册迁移；恢复条件见 project.md「稳定版前的版本策略」。
        assertTrue(Migrations.ALL.isEmpty())
    }

    @Test
    fun create_template_sql_has_all_columns() {
        val sql = Migrations.SQL_1_2_CREATE_TEMPLATES
        assertTrue(sql.contains("record_templates"))
        templateCols.forEach { assertTrue("缺列 $it", sql.contains("`$it`")) }
        assertTrue(sql.contains("PRIMARY KEY AUTOINCREMENT"))
    }

    @Test
    fun create_rule_sql_has_all_columns() {
        val sql = Migrations.SQL_1_2_CREATE_RULES
        assertTrue(sql.contains("recurring_rules"))
        ruleCols.forEach { assertTrue("缺列 $it", sql.contains("`$it`")) }
        assertTrue(sql.contains("PRIMARY KEY AUTOINCREMENT"))
    }

    @Test
    fun no_index_or_foreign_key_in_migration() {
        // 两张新表刻意不加索引 / 外键，Migration SQL 因此只需 CREATE TABLE。
        val both = Migrations.SQL_1_2_CREATE_TEMPLATES + Migrations.SQL_1_2_CREATE_RULES
        assertTrue(!both.contains("CREATE INDEX"))
        assertTrue(!both.contains("FOREIGN KEY"))
    }



    @Test
    fun create_budgets_sql_has_all_columns() {
        val sql = Migrations.SQL_2_3_CREATE_BUDGETS
        assertTrue(sql.contains("budgets"))
        listOf("id", "name", "scope", "categoryId", "limitCents",
               "sortOrder", "isArchived", "createdAt")
            .forEach { assertTrue("缺列 $it", sql.contains("`$it`")) }
        assertTrue(sql.contains("PRIMARY KEY AUTOINCREMENT"))
        assertTrue(!sql.contains("FOREIGN KEY"))
    }

    @Test
    fun alter_sql_adds_linked_record_id_to_both_tables() {
        assertTrue(Migrations.SQL_2_3_ALTER_RECORDS.contains("records"))
        assertTrue(Migrations.SQL_2_3_ALTER_RECORDS.contains("linkedRecordId"))
        assertTrue(Migrations.SQL_2_3_ALTER_TRASH.contains("trash"))
        assertTrue(Migrations.SQL_2_3_ALTER_TRASH.contains("linkedRecordId"))
    }



    @Test
    fun alter_3_4_sql_has_default_HKD_for_both_tables() {
        for (sql in listOf(Migrations.SQL_3_4_ALTER_RECORDS, Migrations.SQL_3_4_ALTER_TRASH)) {
            assertTrue(sql.contains("currencyCode"))
            assertTrue(sql.contains("NOT NULL"))
            assertTrue("缺 DEFAULT: $sql", sql.contains("DEFAULT 'HKD'"))
        }
        assertTrue(Migrations.SQL_3_4_ALTER_RECORDS.contains("records"))
        assertTrue(Migrations.SQL_3_4_ALTER_TRASH.contains("trash"))
    }
}
