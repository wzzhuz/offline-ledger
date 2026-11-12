#!/usr/bin/env python3
"""把 privacy-security 铁律变成可执行的静态检查。"""
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
MANIFEST = os.path.join(BASE, 'app', 'src', 'main', 'AndroidManifest.xml')
BACKUP = os.path.join(BASE, 'app', 'src', 'main', 'res', 'xml', 'backup_rules.xml')
EXTRACT = os.path.join(BASE, 'app', 'src', 'main', 'res', 'xml', 'data_extraction_rules.xml')
GRADLE = os.path.join(BASE, 'app', 'build.gradle.kts')

FORBIDDEN_PERMISSIONS = [
    'android.permission.INTERNET',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.ACCESS_NETWORK_STATE',
]
FORBIDDEN_DEPS = ['coil', 'glide', 'okhttp', 'firebase', 'vico', 'retrofit',
                  'umeng', 'bugly', 'appsflyer']


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def without_xml_comments(text: str) -> str:
    """Manifest 与规则文件里有说明性注释，检查时必须排除，
    否则注释里提到 INTERNET 就会自己把自己判失败。"""
    return re.sub(r'<!--.*?-->', '', text, flags=re.S)


def without_line_comments(text: str) -> str:
    return '\n'.join(line.split('//')[0] for line in text.splitlines())


def main() -> int:
    problems = []
    m = without_xml_comments(read(MANIFEST))
    for perm in FORBIDDEN_PERMISSIONS:
        if perm in m:
            problems.append('Manifest 声明了被禁止的权限：' + perm)
    if 'android:allowBackup="false"' not in m:
        problems.append('Manifest 未设置 android:allowBackup="false"')
    if '@xml/backup_rules' not in m:
        problems.append('Manifest 未引用 backup_rules.xml')
    if '@xml/data_extraction_rules' not in m:
        problems.append('Manifest 未引用 data_extraction_rules.xml')

    for label, path in [('backup_rules.xml', BACKUP),
                        ('data_extraction_rules.xml', EXTRACT)]:
        s = without_xml_comments(read(path))
        if '<include' in s:
            problems.append(label + ' 使用了 <include> 白名单模式，'
                            '再 exclude 未被 include 的路径会被 lintVital 判为 fatal')
        if 'domain="database"' not in s:
            problems.append(label + ' 未排除 domain="database"')
        if label == 'data_extraction_rules.xml':
            for section in ('<cloud-backup>', '<device-transfer>'):
                if section not in s:
                    problems.append(label + ' 缺少 ' + section + ' 段')

    g = without_line_comments(read(GRADLE)).lower()
    for dep in FORBIDDEN_DEPS:
        if dep in g:
            problems.append('app/build.gradle.kts 引入了被禁止的依赖：' + dep)

    if problems:
        print('check_manifest_privacy: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_manifest_privacy: PASS（无网络权限 / 关闭自动备份 / 排除数据库 / 无网络 SDK）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
