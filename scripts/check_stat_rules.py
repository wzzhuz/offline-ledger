#!/usr/bin/env python3
"""静态检查：余额聚合 SQL 与统计口径没被顺手改歪。"""
import os
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DAO = os.path.join(
    BASE, 'app', 'src', 'main', 'java', 'com', 'zwz', 'offlineledger',
    'data', 'local', 'dao', 'RecordDao.kt'
)
RULES = os.path.join(
    BASE, 'app', 'src', 'main', 'java', 'com', 'zwz', 'offlineledger',
    'engine', 'StatRules.kt'
)


def main() -> int:
    problems = []
    dao = open(DAO, encoding='utf-8').read()
    rules = open(RULES, encoding='utf-8').read()

    for needle in ['fromAccountId', '-amountCents', 'toAccountId',
                   'amountCents', 'GROUP BY accountId', 'UNION ALL']:
        if needle not in dao:
            problems.append('余额聚合 SQL 缺少关键片段：' + needle)

    if "type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0" not in rules:
        problems.append('StatRules.SQL_STAT_RELEVANT 被改动')

    # 所有收支聚合查询都必须带 excludeFromStats = 0
    # 阶段 4 新增的下钻查询也纳入统计口径检查。
    # 注意：subcategoryTotalsUnderRoot 刻意不以 expenseByCategory 为前缀，
    # 否则 dao.find('fun expenseByCategory') 会回溯到错误的 @Query。
    for marker in ['totalExpense', 'totalIncome', 'expenseByRoot',
                   'expenseByCategory', 'monthlyTrend', 'subcategoryTotalsUnderRoot',
                   'rootTotalsInRange', 'categoryTotalsInRange', 'drillDownRows',
                   # 阶段 6b 的 SETTLEMENT 聚合，同样必须带唯一口径
                   'settlementByRootInRange', 'settlementByCategoryInRange',
                   'settlementTotalInRange']:
        idx = dao.find('fun ' + marker)
        if idx < 0:
            problems.append('缺少统计查询 ' + marker)
            continue
        line = dao[dao.rfind('@Query', 0, idx):idx]
        if 'excludeFromStats = 0' not in line:
            problems.append('统计查询 %s 未复用唯一口径' % marker)

    if problems:
        print('check_stat_rules: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_stat_rules: PASS（聚合 SQL 与唯一统计口径完整）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
