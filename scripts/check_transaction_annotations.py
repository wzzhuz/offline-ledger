#!/usr/bin/env python3
"""静态检查：Repository 的每个写方法都在 withTransaction 内，且无破坏性迁移。"""
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
REPO_FILE = os.path.join(
    BASE, 'app', 'src', 'main', 'java', 'com', 'zwz', 'offlineledger',
    'data', 'repository', 'LedgerRepository.kt'
)
SRC_ROOT = os.path.join(BASE, 'app', 'src', 'main', 'java')

WRITE_METHODS = [
    'insertRecord', 'updateRecord', 'deleteRecord', 'restoreRecord',
    'addAdjustment', 'revokeAdjustment', 'rebuildAllBalances',
    'checkAndRepair', 'importBackup',
    # 阶段 3 新增的 4 个写方法；不扩清单就会漏检（spec: build-and-ci delta）
    'reconcile', 'createAccount', 'updateAccountMeta', 'archiveAccount',
    # 阶段 5 新增的 4 个分类写方法；不扩清单就会漏检
    'createCategory', 'renameCategory', 'archiveCategory', 'reorderCategories'
]


def main() -> int:
    problems = []
    if not os.path.isfile(REPO_FILE):
        print('check_transaction_annotations: FAIL（找不到 LedgerRepository.kt）')
        return 1
    s = open(REPO_FILE, encoding='utf-8').read()

    for name in WRITE_METHODS:
        m = re.search(r'suspend fun ' + name + r'\b', s)
        if not m:
            problems.append('缺失写方法 suspend fun %s' % name)
            continue
        # 取该方法到下一个 suspend fun / private suspend fun 之前的片段
        nxt = re.search(r'\n    (?:private )?suspend fun ', s[m.end():])
        body = s[m.end(): m.end() + (nxt.start() if nxt else len(s))]
        if 'db.withTransaction' not in body:
            problems.append('写方法 %s 未包裹在 db.withTransaction 内' % name)

    for dirpath, _, filenames in os.walk(SRC_ROOT):
        for fn in filenames:
            if not fn.endswith('.kt'):
                continue
            text = open(os.path.join(dirpath, fn), encoding='utf-8').read()
            # 注释里会写明「禁止使用 xxx」，必须先剥掉注释再判断，
            # 否则说明性的注释自己会把自己判失败。
            clean = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
            clean = '\n'.join(line.split('//')[0] for line in clean.splitlines())
            if 'fallbackToDestructiveMigration' in clean:
                problems.append('检测到破坏性迁移：' + fn)

    if problems:
        print('check_transaction_annotations: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_transaction_annotations: PASS（%d 个写方法均在事务内，无破坏性迁移）'
          % len(WRITE_METHODS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
