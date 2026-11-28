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

    # ---- 第 90 批：预制分类清单 v3 四处一致性 + 三段边界 ----
    import json as _json
    root = os.path.join(BASE, 'app', 'src')
    assets = os.path.join(root, 'main', 'assets', 'default_categories.json')
    testres = os.path.join(root, 'test', 'resources', 'default_categories.json')
    if read(assets) != read(testres):
        problems.append('default_categories.json 的 assets 与 test resources 不逐字一致')
    cat = _json.loads(read(assets))
    rows = cat['categories']
    if cat.get('version') != 5 or len(rows) != 114:
        problems.append('清单 v3 应为 version 5 / 114 行')
    roots = [r for r in rows if r['level'] == 0]
    leaves = [r for r in rows if r['level'] == 1]
    if len(roots) != 16 or len(leaves) != 98:
        problems.append('清单 v3 应为一级 16 / 二级 98')
    if sum(1 for r in rows if r['enabled']) != 61:
        problems.append('清单 v3 启用数应为 61')
    byid = {r['id']: r for r in rows}
    if not any(r['id'] == 11 and r['name'] == '日常' and r['enabled'] for r in roots):
        problems.append('缺新增一级「日常」(11) 或未默认启用')
    for old in (4007, 4009, 4011):
        if old in byid:
            problems.append('旧 id %d 迁入后 MUST NOT 留在清单' % old)
    disabled_roots = sorted(r['name'] for r in roots if not r['enabled'])
    if disabled_roots != ['人情社交', '学习娱乐']:
        problems.append('默认停用一级应只剩 学习娱乐 / 人情社交，实为 %s' % disabled_roots)
    pet = [r for r in rows if r['id'] == 8 or r.get('parentId') == 8]
    if len(pet) != 8 or not all(r['enabled'] for r in pet):
        problems.append('宠物一级与其 7 个二级 MUST 全部默认启用')
    names = lambda pid: [r['name'] for r in leaves if r.get('parentId') == pid]
    housing, daily, shopping = names(4), names(11), names(5)
    for n in ('话费', '家具家电', '保洁搬家'):
        if n in housing:
            problems.append('「居住」不应再含 ' + n)
    for n in ('话费', '理发美容', '快递邮寄', '家政保洁', '绿植鲜花'):
        if n not in daily:
            problems.append('「日常」缺 ' + n)
    if '家具家电' not in shopping or '家居家纺' not in shopping or '家居' in shopping:
        problems.append('「购物」应含 家具家电 / 家居家纺，且旧名「家居」MUST 已改')
    # ID 段规则
    for r in leaves:
        if r['id'] // 1000 != r['parentId']:
            problems.append('二级 %d 不在父段' % r['id'])
    # 四处 Kotlin / 资源一致性
    keys = {r['iconKey'] for r in rows}
    catalog = read(os.path.join(root, 'main', 'java', 'com', 'zwz', 'offlineledger', 'ui', 'components', 'CategoryIconCatalog.kt'))
    tints = read(os.path.join(root, 'main', 'java', 'com', 'zwz', 'offlineledger', 'engine', 'CategoryIconTints.kt'))
    keys_kt = read(os.path.join(root, 'main', 'java', 'com', 'zwz', 'offlineledger', 'engine', 'CategoryIconKeys.kt'))
    seed = read(os.path.join(root, 'main', 'java', 'com', 'zwz', 'offlineledger', 'data', 'local', 'SeedData.kt'))
    for k in sorted(keys):
        if '"%s" ->' % k not in catalog:
            problems.append('CategoryIconCatalog 缺 ' + k)
        if '"%s" ->' % k not in tints:
            problems.append('CategoryIconTints 缺 ' + k)
    for r in rows:
        if '%dL ->' % r['id'] not in keys_kt:
            problems.append('CategoryIconKeys 缺 id=%d' % r['id'])
        if 'c(%d, "%s"' % (r['id'], r['name']) not in seed:
            problems.append('SeedData fallback 缺/错 id=%d %s' % (r['id'], r['name']))
    for k in ('scissors', 'flower'):
        if not os.path.isfile(os.path.join(root, 'main', 'res', 'drawable', 'ic_cat_%s.xml' % k)):
            problems.append('缺新增 drawable ic_cat_%s.xml' % k)
    notice = read(os.path.join(root, 'main', 'assets', 'NOTICE'))
    if '92 个' not in notice:
        problems.append('NOTICE 未同步 92 个 SVG')
    # 文档同步
    prompt = read(os.path.join(BASE, 'docs', '自动记账规则生成提示词.md'))
    activity = read(os.path.join(root, 'main', 'java', 'com', 'zwz', 'offlineledger', 'ui', 'intake', 'TextIntakeActivity.kt'))
    for text, label in ((prompt, '提示词文档'), (activity, 'RULE_PROMPT')):
        if '居住 / 日常 / 购物' not in text:
            problems.append('%s 一级清单未含「日常」' % label)
    for text, label in ((read(os.path.join(BASE, 'openspec', 'specs', 'category-stats', 'spec.md')), 'category-stats'),
                        (read(os.path.join(BASE, 'docs', '页面优化经验与避坑指南.md')), '活文档')):
        if '第 90 批' not in text:
            problems.append('%s 未登记第 90 批' % label)

    if problems:
        print('check_docs_consistency: FAIL')
        for p in problems:
            print('  - ' + p)
        return 1
    print('check_docs_consistency: PASS（版本表 / targetSdk / 不引入清单 / 预制分类 v3 一致性）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
