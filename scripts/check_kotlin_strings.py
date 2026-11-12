#!/usr/bin/env python3
"""检测 Kotlin 源码里被提前转义成真实换行的字符串字面量。

症状（踩坑清单 1.1，犯过两次）：编译报 Expecting a top level declaration，
错误信息完全指不到真正原因。根因是用 Python 拼接 Kotlin 时 \n 变成真实换行，
Kotlin 字符串字面量不允许跨行。

启发式：逐行统计未转义的双引号数量，奇数即疑似未闭合；
跳过注释行、三引号原始字符串块与不含引号的行。
"""
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src')


def unescaped_quotes(line: str) -> int:
    count = 0
    i = 0
    while i < len(line):
        if line[i] == '\\':
            i += 2
            continue
        if line[i] == '"':
            count += 1
        i += 1
    return count


def main() -> int:
    problems = []
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            if not fn.endswith('.kt'):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, os.path.join(os.path.dirname(__file__), '..'))
            in_raw = False
            with open(path, encoding='utf-8') as f:
                for lineno, line in enumerate(f, 1):
                    stripped = line.lstrip()
                    if '"""' in stripped:
                        # 三引号块内允许任意内容；简单切换状态
                        in_raw = stripped.count('"""') % 2 == 1
                        continue
                    if in_raw or stripped.startswith('//') or stripped.startswith('*'):
                        continue
                    if unescaped_quotes(line.rstrip('\n')) % 2 == 1:
                        problems.append('%s:%d  字符串字面量疑似未闭合: %s'
                                        % (rel, lineno, line.rstrip()[:100]))
    if problems:
        print('check_kotlin_strings: FAIL')
        for p in problems:
            print('  ' + p)
        return 1
    print('check_kotlin_strings: PASS（未发现跨行字符串字面量）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
