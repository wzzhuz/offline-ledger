# Delta for 多级分类与统计

## ADDED Requirements

### Requirement: 分类管理复用预留字段且禁止物理删除

设置页 SHALL 提供两级分类管理，支持新增一级 / 二级、改名、排序与归档。
系统 MUST NOT 物理删除任何分类（尤其已有流水的分类），
归档 SHALL 只置 `isArchived` 且不改任何历史流水的 `rootCategoryId`。
新增分类 SHALL 校验 `level ∈ {0,1}`、二级的 `parentId` 非空且父级为一级，
子级 SHALL 从父级继承 `type`。

#### Scenario: 归档一个已有流水的子类

- **GIVEN** 「零食」下有 200 笔历史流水
- **WHEN** 在分类管理里归档「零食」
- **THEN** `isArchived = 1`，分类仍存在于库中
- **AND** 历史月报「零食」仍归入「餐饮」，合计不变

#### Scenario: 拒绝三级分类

- **GIVEN** 用户在「食材」下再建子类
- **WHEN** 提交
- **THEN** 拒绝并提示最多两级
- **AND** 分类树保持两层

#### Scenario: 类型继承校验

- **GIVEN** 用户在支出大类「餐饮」下建一个收入子类
- **WHEN** 提交
- **THEN** 拒绝并提示类型须与父级一致

**理由**：`CategoryEntity` 在阶段 1 就预留了 `isArchived` / `sortOrder`，
本阶段直接启用，零 Migration；物理删除有流水的分类会产生孤儿流水，
是 `project.md` 永久不做项。

---

### Requirement: 新增分类写方法全部走事务并登记清单

阶段 5 新增的 `createCategory` / `renameCategory` / `archiveCategory` /
`reorderCategories` SHALL 全部包在 `db.withTransaction` 内，
SHALL 登记进 `check_transaction_annotations.py` 的 `WRITE_METHODS`（13 → 17）。
排序变更 SHALL 在事务末尾调用 `rebuildAllBalancesInternal()` 兜底。

#### Scenario: 忘记登记

- **GIVEN** 有人新增一个分类写方法却没扩 `WRITE_METHODS`
- **WHEN** CI 执行静态检查
- **THEN** 评审或检查发现清单与方法数不一致
- **AND** 不产出一个「新方法绕开事务」的版本

**理由**：硬编码清单最典型的失效方式是「加了新方法忘了登记」；
阶段 3 已为此扩过一次清单，本阶段沿用同一规则。
