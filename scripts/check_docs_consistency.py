#!/usr/bin/env python3
"""粗筛文档与构建脚本的技术选型是否矛盾（踩坑清单 4.3 / 4.4）。

只做关键词一致性检查，不做语义判断。
"""
import os
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
GRADLE = os.path.join(BASE, 'build.gradle.kts')
APP_GRADLE = os.path.join(BASE, 'app', 'build.gradle.kts')
README = os.path.join(BASE, 'README.md')
PROJECT = os.path.join(BASE, 'openspec', 'project.md')


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def main() -> int:
    problems = []
    top = read(GRADLE)
    app = read(APP_GRADLE)
    readme = read(README)
    project = read(PROJECT)

    # 顶层声明的版本号应当出现在 README 的版本表里
    for key in ('8.13.0', '2.3.21', '2.3.11', '2.57.1'):
        if key not in top:
            problems.append('顶层 build.gradle.kts 缺少预期版本 ' + key)
        if key not in readme:
            problems.append('README.md 版本表未同步 ' + key)

    # targetSdk 必须与 Google Play 当前要求一致
    if 'targetSdk = 36' not in app:
        problems.append('app/build.gradle.kts 的 targetSdk 不是 36，请复核官方要求')

    # project.md 明确不引入的依赖，不得出现在构建脚本里。
    # 构建脚本的注释会提到这些名字（解释为什么不引入），必须先去掉行注释。
    code_only = '\n'.join(line.split('//')[0] for line in (top + app).splitlines()).lower()
    for dep in ('Vico', 'Coil', 'Glide'):
        if dep.lower() in code_only:
            problems.append('构建脚本引入了 project.md 明确不引入的 ' + dep)

    # README 当前状态不得声称已有可用产物
    if '阶段 ' not in readme:
        problems.append('README.md 未标注当前阶段')

    if '阶段 0' not in project and '阶段0' not in project:
        problems.append('openspec/project.md 未包含分阶段计划')

    if problems:
        print('check_docs_consistency: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_docs_consistency: PASS（版本表 / targetSdk / 不引入清单一致）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
