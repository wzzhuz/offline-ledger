#!/usr/bin/env python3
"""静态检查：Repository 写方法在事务内、迁移显式、SQL 契约完整、无破坏性迁移。"""
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(BASE, 'app', 'src', 'main', 'java', 'com', 'zwz', 'offlineledger')
REPO_FILE = os.path.join(SRC, 'data', 'repository', 'LedgerRepository.kt')
DB_FILE = os.path.join(SRC, 'data', 'local', 'AppDatabase.kt')
MIG_FILE = os.path.join(SRC, 'data', 'local', 'Migrations.kt')
MODULE_FILE = os.path.join(SRC, 'data', 'di', 'DatabaseModule.kt')
SRC_ROOT = os.path.join(BASE, 'app', 'src', 'main', 'java')

WRITE_METHODS = [
    'insertRecord', 'updateRecord', 'deleteRecord', 'restoreRecord',
    'addAdjustment', 'revokeAdjustment', 'rebuildAllBalances',
    'checkAndRepair', 'importBackup',
    # 阶段 3
    'reconcile', 'createAccount', 'updateAccountMeta', 'archiveAccount',
    # 阶段 5
    'createCategory', 'renameCategory', 'archiveCategory', 'reorderCategories',
    # 阶段 6a
    'createTemplate', 'updateTemplate', 'archiveTemplate', 'reorderTemplates',
    'createRecurringRule', 'updateRecurringRule', 'setRuleEnabled',
    'recordFromTemplate', 'applyDueRules',
    # 阶段 6b
    'createBudget', 'updateBudget', 'archiveBudget', 'recordSettlement'
]

TEMPLATE_SQL_COLS = [
    'id', 'name', 'type', 'amountCents', 'categoryId', 'rootCategoryId',
    'fromAccountId', 'toAccountId', 'note', 'excludeFromStats',
    'sortOrder', 'isArchived', 'createdAt'
]
RULE_SQL_COLS = [
    'id', 'name', 'type', 'amountCents', 'categoryId', 'rootCategoryId',
    'fromAccountId', 'toAccountId', 'note', 'excludeFromStats',
    'cadence', 'dayOfMonth', 'dayOfWeek', 'anchorLocalDate',
    'lastGeneratedLocalDate', 'isEnabled', 'createdAt'
]
BUDGET_SQL_COLS = [
    'id', 'name', 'scope', 'categoryId', 'limitCents',
    'sortOrder', 'isArchived', 'createdAt'
]


def strip_comments(text):
    """先剥注释再判断；说明性注释自己不能把自己判失败（已犯过 7 次）。"""
    clean = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return '\n'.join(line.split('//')[0] for line in clean.splitlines())


def sql_const_block(text, name):
    """精确取 const val SQL_xxx 的赋值段，避免宽泛子串自误报。"""
    m = re.search(
        r'const val ' + name + r'\s*=\s*(.*?)(?=\n    (?:const val|val |private val |object )|\Z)',
        text, re.S)
    return m.group(1) if m else ''


def main() -> int:
    problems = []
    s = open(REPO_FILE, encoding='utf-8').read()

    for name in WRITE_METHODS:
        m = re.search(r'suspend fun ' + name + r'\b', s)
        if not m:
            problems.append('缺失写方法 suspend fun %s' % name)
            continue
        nxt = re.search(r'\n    (?:private )?suspend fun ', s[m.end():])
        body = s[m.end(): m.end() + (nxt.start() if nxt else len(s))]
        if 'db.withTransaction' not in body:
            problems.append('写方法 %s 未包裹在 db.withTransaction 内' % name)

    for dirpath, _, filenames in os.walk(SRC_ROOT):
        for fn in filenames:
            if not fn.endswith('.kt'):
                continue
            text = open(os.path.join(dirpath, fn), encoding='utf-8').read()
            if 'fallbackToDestructiveMigration' in strip_comments(text):
                problems.append('检测到破坏性迁移：' + fn)

    db_text = strip_comments(open(DB_FILE, encoding='utf-8').read())
    if 'version = 3' not in db_text:
        problems.append('AppDatabase 版本未升到 3')
    for e in ['RecordTemplateEntity::class', 'RecurringRuleEntity::class',
              'BudgetEntity::class']:
        if e not in db_text:
            problems.append('AppDatabase 未登记 ' + e)

    mig_text = open(MIG_FILE, encoding='utf-8').read()
    mig_clean = strip_comments(mig_text)
    for m in ['Migration(1, 2)', 'Migration(2, 3)']:
        if m not in mig_clean:
            problems.append('缺少 ' + m)
    if 'arrayOf(MIGRATION_1_2, MIGRATION_2_3)' not in mig_clean:
        problems.append('Migrations.ALL 未同时登记两段迁移')
    mod_text = strip_comments(open(MODULE_FILE, encoding='utf-8').read())
    if 'addMigrations(*Migrations.ALL)' not in mod_text:
        problems.append('DatabaseModule 未调用 addMigrations')

    for const, cols, table in [
        ('SQL_1_2_CREATE_TEMPLATES', TEMPLATE_SQL_COLS, 'record_templates'),
        ('SQL_1_2_CREATE_RULES', RULE_SQL_COLS, 'recurring_rules'),
        ('SQL_2_3_CREATE_BUDGETS', BUDGET_SQL_COLS, 'budgets'),
    ]:
        block = sql_const_block(mig_text, const)
        if not block:
            problems.append('缺少 SQL 常量 ' + const)
            continue
        if table not in block:
            problems.append('%s 未建表 %s' % (const, table))
        for col in cols:
            if '`' + col + '`' not in block:
                problems.append('%s 缺少列 %s' % (const, col))

    for const, table in [
        ('SQL_2_3_ALTER_RECORDS', 'records'),
        ('SQL_2_3_ALTER_TRASH', 'trash'),
    ]:
        block = sql_const_block(mig_text, const)
        if not block:
            problems.append('缺少 SQL 常量 ' + const)
            continue
        if table not in block or 'linkedRecordId' not in block:
            problems.append('%s 未给 %s 加 linkedRecordId' % (const, table))

    if problems:
        print('check_transaction_annotations: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_transaction_annotations: PASS（%d 个写方法均在事务内，'
          '两段 Migration 显式且 SQL 契约完整，无破坏性迁移）' % len(WRITE_METHODS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
