# 第 94 批 Design：通用容器 + 场景模板

## 决策 1：模板只管「措辞 + 预填分类」，不管数据口径

`VirtualBookTemplate` 是纯 JVM enum，三种：

| 模板 | 按钮 | 汇总词 | 分段 | 分类来源 |
|---|---|---|---|---|
| `GIFT` 人情往来 | 记收礼 / 记还礼 | 收 / 还 / 待还 | 往来汇总 | 内置 34004（INCOME）/ 9001（EXPENSE） |
| `PROJECT` 专项支出 | 仅「记支出」 | 收入 / 支出 / 净支出 | 汇总 | `VirtualBook.defaultCategoryId` |
| `CUSTOM` 自定义 | 记收入 / 记支出 | 收入 / 支出 / 净额 | 汇总 | `VirtualBook.defaultCategoryId` |

模板 MUST NOT 改变：记账仍落主账本、切换不带录入归属、台账记录不计入收支、
零 `bookId`、零统计 scope、自动记账永远落主账本。

`VirtualBook` 新增 `template` + `defaultCategoryId` 两个字段，编码从 5 段扩到 7 段。
**旧 5 段解码回退 `GIFT`**，第 91～93 批建的老视图行为零回归；非法模板 id 由
`normalize()` 回退 GIFT；`defaultCategoryId <= 0` 清成 null。
定义仍只存 SharedPreferences，不进 Room / 备份 DTO / Migration。

## 决策 2：预填分类按 id 精确命中，不要求 enabled

v0.2.72 的 `applyPresetTo` 取「该 type 下第一个启用一级」→ 记收礼预填**工资**、
记还礼预填**餐饮**，每笔都得手动改。清单里其实有对口的 9001 / 34004，但都默认停用。

两条可选路：

| 路 | 代价 |
|---|---|
| 启用 9001 / 34004 | `SeedCatalogTest` 硬断言停用一级 = {学习娱乐, 人情社交}；启用 9001 要把父级「人情社交」也启用 → 清单 v3→v4、三处一致性、单测、**卸载重装** |
| **按 id 精确预填（采用）** | 零改清单、零改单测、零改表、可直接覆盖 |

依据：`load()` 的分类数据源是 `categoryDao.all()` **全量**（第 52 批「停用项不再查不到」），
保存校验（第 430 行）只检查分类存在 + `type` 一致，**不检查 `enabled`**。
所以停用分类可以正常预填并落库。

实现：
```kotlin
val categoryId = presetCategoryId?.takeIf { id ->
    categories.any { it.id == id && it.type == effectiveType }
} ?: categories.firstOrNull { /* 第一个启用一级 */ }?.id
```
命中二级分类时 `expandedRootId` MUST 指向其父一级，否则分类快捷栏不会展开到对应分组。

## 决策 3：设置页一次配置，录入时零选择

- 新建表单加三个模板 chip + 一行模板说明
- 已建视图加「配置」：改模板；非礼金模板可「设置默认分类…」→ 弹分类列表
  （按模板过滤收支类型、排除归档项，二级显示成「一级 > 二级」）
- 切到 `GIFT` 时清掉 `defaultCategoryId`，避免两套口径并存
- 默认分类存 id 不存名字，分类改名后显示自动跟随

「设置一次、之后每笔自动带」正好对上用户「分类还要自己选」的不满，且没有新增
每笔交互。

## 决策 4：修第 93 批漏收口的三处腐化文案

第 93 批撤回明细排除 SQL 时只改了 SQL / spec，UI 与注释里还留着旧话：

| 位置 | 旧 | 新 |
|---|---|---|
| 设置页视图卡副标题 | 「主账本明细默认不显示」 | 「不计入主账本收支」 |
| 设置页空态 | 「从左上角切换器进入」 | 「从明细页标题行的账本图标进入」 |
| codec / store 注释 | 「主账本明细据此排除台账记录」 | 「台账视图据此聚合」 |
| VM 第 92 批注释 | 「主账本明细也不排除」 | 「主账本明细却会显示」 |

`check_virtual_books.py` 加了硬断言，防止这几个字面串再回来。

## 考虑过的其他方案（否决）

| 方案 | 否决理由 |
|---|---|
| A 收窄成「人情往来专用」，把名字改准 | 用户上一轮明确要的是「类似多账本的通用切换」，等于把通用性收回去 |
| C 只中性化文案 + 允许改按钮名 | 还有 DIY 感，且没解决分类预填错误 |
| 启用 9001 / 34004 改清单 | 见决策 2，代价远超收益 |
| 再增加「旅行 / 装修」等具名模板 | 稀释模板数量；专项支出 + 自定义已覆盖 |
| 给统计模块加台账 scope | 接近真多账本成本，与「视图」定位冲突 |
| 把模板定义进 Room / 备份 DTO | 打断覆盖安装链路，定义本来就是本机偏好 |

## 影响面核查

- `check_virtual_books.py`：旧断言 `vm.load(recordId, presetType, presetTagNames,
  presetExcludeFromStats)` 同步改成带 `presetCategoryId`；新增模板三件套、
  旧 5 段兼容、预填按 id、腐化文案 MUST NOT 残留等断言。
- 主 spec 3 条 MODIFIED（标题一律不变，归档匹配基准）+ 1 条 ADDED。
- 零改表（version 仍 9）、零改备份 DTO / CSV / 预置分类清单、零新增依赖 /
  权限 / `INTERNET`、可直接覆盖 v0.2.72。
