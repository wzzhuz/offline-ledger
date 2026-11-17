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

# 2026-11-17 使用者澄清：红线是「默认回传厂商且用户不可控」，不是权限/依赖的形式禁令。
# 因此以下两项改为「未经 project.md 显式授权则禁止」——有授权标记时记提示并放行，
# 而不是无条件 FAIL。当前 App 无任何授权标记，行为与改前一致。
AUTHORIZATION_MARKER = '权限显式授权'
FORBIDDEN_PERMISSIONS = [
    'android.permission.INTERNET',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.ACCESS_NETWORK_STATE',
]
# 始终禁止：默认回传数据且用户不可控的 SDK，不因授权而放行。
ALWAYS_FORBIDDEN_DEPS = ['firebase', 'umeng', 'bugly', 'appsflyer',
                         'crashlytics', 'sentry', 'amplitude', 'mixpanel']
# 需显式授权才可引入：本身不是回传 SDK，但可能带网络栈或跨版本 API 风险。
GATED_DEPS = ['coil', 'glide', 'okhttp', 'retrofit', 'vico']
# 阶段 5 新增：唯一允许声明的权限（BiometricPrompt 用，normal 级）。
# 除此之外的任何 uses-permission 都会让检查失败，防止「加了权限忘了评审」。
ALLOWED_PERMISSIONS = ['android.permission.USE_BIOMETRIC']


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
    project = read(os.path.join(BASE, 'openspec', 'project.md'))
    authorized = AUTHORIZATION_MARKER in project
    notes = []
    m = without_xml_comments(read(MANIFEST))
    for perm in FORBIDDEN_PERMISSIONS:
        if perm in m and not authorized:
            problems.append('Manifest 声明了未授权的权限：' + perm +
                            '（如需放行，先在 project.md 写「' + AUTHORIZATION_MARKER +
                            '」说明用途/数据流向/用户控制方式）')
        elif perm in m and authorized:
            notes.append('已按 project.md 授权放行权限：' + perm)
    if 'android:allowBackup="false"' not in m:
        problems.append('Manifest 未设置 android:allowBackup="false"')
    if '@xml/backup_rules' not in m:
        problems.append('Manifest 未引用 backup_rules.xml')
    if '@xml/data_extraction_rules' not in m:
        problems.append('Manifest 未引用 data_extraction_rules.xml')

    # 精确匹配 <uses-permission> 标签并先剥注释；不做宽泛子串搜索，
    # 否则注释里提到权限名会自己把自己判失败（阶段 1 归档自误报两次）。
    declared = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', m)
    for perm in declared:
        if perm not in ALLOWED_PERMISSIONS:
            problems.append('Manifest 声明了未在白名单中的权限：' + perm)

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
    for dep in ALWAYS_FORBIDDEN_DEPS:
        if dep in g:
            problems.append('app/build.gradle.kts 引入了始终禁止的回传 SDK：' + dep)
    for dep in GATED_DEPS:
        if dep in g and not authorized:
            problems.append('app/build.gradle.kts 引入了需显式授权的依赖：' + dep)
        elif dep in g and authorized:
            notes.append('已按 project.md 授权放行依赖：' + dep)

    if problems:
        print('check_manifest_privacy: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    for n in notes:
        print('  [已授权] ' + n)
    print('check_manifest_privacy: PASS（无未授权的网络权限 / 关闭自动备份 / '
          '排除数据库 / 无默认回传 SDK；边界=数据流向由使用者控制）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
