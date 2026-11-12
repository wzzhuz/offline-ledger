# Delta for 多级分类与统计

## ADDED Requirements

### Requirement: 记账入口解析并冗余 rootCategoryId

记一笔在保存前 SHALL 由 `CategoryTree.resolveRootId` 解析 `rootCategoryId`，
一级分类 SHALL 指向自身，二级 SHALL 指向父级。
系统 SHALL 允许 `categoryId` 直接指向一级分类。

#### Scenario: 一级直记

- **GIVEN** 用户只选一级「交通」
- **WHEN** 保存
- **THEN** `categoryId = 2` 且 `rootCategoryId = 2`

#### Scenario: 二级记账

- **GIVEN** 用户选「交通 → 打车」
- **WHEN** 保存
- **THEN** `categoryId = 202` 且 `rootCategoryId = 2`

**理由**：冗余一列换来两条最简单的 `GROUP BY`，统计阶段不必递归或闭包表。

---

### Requirement: 分类图标不做字符串映射

分类图标 SHALL 用 `colorHex` 绘制彩色圆 + 分类名中文首字呈现，
MUST NOT 把 `iconName` 字符串反射映射到 `ImageVector`。
`iconName` 字段 SHALL 原样保留供将来扩展。

#### Scenario: 新增分类名不含已知图标名

- **GIVEN** 用户自定义一个分类，图标名是任意字符串
- **WHEN** 打开分类网格
- **THEN** 正常显示彩色圆 + 首字
- **AND** 不因图标名不存在而编译失败或运行时崩溃

**理由**：预置数据有 30 个图标名，任一在当前 Compose BOM 的图标集里不存在
就会让整份映射表编译失败；中文首字辨识度反而更高，且零依赖风险。
