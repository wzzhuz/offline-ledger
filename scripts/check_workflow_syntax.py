#!/usr/bin/env python3
"""静态检查：release.yml 的语法陷阱与凭据边界。

纯文本检查，不依赖 PyYAML。沿用阶段 1 归档换来的规则：
精确匹配、先剥注释，别宽泛搜子串造成自误报。
"""
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RELEASE = os.path.join(BASE, '.github', 'workflows', 'release.yml')


def strip_comments(text: str) -> str:
    """先剥 # 注释再检查，否则注释里提到 Secret 名会自己把自己判失败。"""
    return '\n'.join(line.split('#', 1)[0] for line in text.splitlines())


def main() -> int:
    problems = []
    if not os.path.isfile(RELEASE):
        print('check_workflow_syntax: FAIL（找不到 .github/workflows/release.yml）')
        return 1

    raw = open(RELEASE, encoding='utf-8').read()
    s = strip_comments(raw)

    if 'workflow_dispatch:' not in s:
        problems.append('release.yml 未声明 workflow_dispatch')
    if 'minify:' not in s or 'upload_nutstore:' not in s:
        problems.append('release.yml 缺少 minify / upload_nutstore 输入')

    # 第 81 批：v0.2.57–59 连续三次出包把 upload_nutstore 显式传成 false，
    # 坚果云步骤被 skipped（workflow 本身没坏）。落成可执行断言：
    # workflow 默认值必须是 true，且「上传到坚果云」步骤只认 inputs.upload_nutstore。
    nut = re.search(
        r'^\s*upload_nutstore:[ \t]*\n((?:^[ \t]+\S[^\n]*\n){0,8})',
        s, re.M)
    if not nut or not re.search(r'^[ \t]*default:[ \t]*true[ \t]*$', nut.group(1), re.M):
        problems.append('upload_nutstore 的 workflow 默认值不是 true（坚果云会被静默跳过）')
    if not re.search(r'^[ \t]*if:[ \t]*inputs\.upload_nutstore[ \t]*$', s, re.M):
        problems.append('上传到坚果云步骤未绑定 inputs.upload_nutstore')

    # YAML 1.1 会把裸 on 解析成布尔 true，与 default 的字符串类型对不上，
    # GitHub 会「静默不注册」这个工作流（文件在、Actions 页找不到）。
    # 精确匹配 choice 的 option 行，不去搜全文的 'on'。
    for option in ('none', 'on', 'both'):
        if not re.search(r"^\s*-\s*['\"]" + option + r"['\"]\s*$", s, re.M):
            problems.append("choice option '%s' 未加引号（YAML 1.1 会布尔化）" % option)

    if not re.search(r'contents:\s*write', s):
        problems.append('permissions 缺少 contents: write')
    if not re.search(r'actions:\s*write', s):
        problems.append('permissions 缺少 actions: write（只写 contents 会置 actions 为 none）')

    if 'overwrite: true' not in s:
        problems.append('upload-artifact@v4 未设置 overwrite: true（同名冲突会失败）')

    # 产物名必须纯 ASCII：GitHub Release 资产接口会静默丢弃中文。
    # 只检查「命名」用的赋值，跳过路径变量（含 / 或 ${{ ... }} 的是路径，
    # 不是产物名——第 7 次自误报就是把它当成了产物名）。
    for m in re.finditer(r'APK="([^"]+)"', s):
        name = m.group(1)
        if '/' in name or '${{' in name:
            continue
        if not name.isascii() or 'offline-ledger-v' not in name:
            problems.append('产物名不是 ASCII 的 offline-ledger-v*：' + name)

    # 硬编码凭据字面量：只匹配「赋值/冒号后跟一段可疑取值」的形式，
    # 不匹配注释里提到的 Secret 名（已剥注释）。
    forbidden = [
        r'JGY_APP_PASSWORD\s*[:=]\s*["\']?[A-Za-z0-9]{8,}',
        r'JGY_USER\s*[:=]\s*["\']?[^$\s]{8,}@',
        r'KEYSTORE_PASSWORD\s*[:=]\s*["\']?[A-Za-z0-9]{8,}',
    ]
    for pat in forbidden:
        if re.search(pat, s):
            problems.append('疑似硬编码凭据字面量：' + pat)

    if problems:
        print('check_workflow_syntax: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_workflow_syntax: PASS（workflow_dispatch / choice 引号 / 权限 / '
          'overwrite / ASCII 产物名 / 坚果云默认开启 / 无硬编码凭据）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
