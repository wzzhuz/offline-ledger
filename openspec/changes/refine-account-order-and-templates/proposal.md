# 第 60 批：账户排序拆分、模板正规化与 v0.2.40 回归修复

> 本文件是 change 的上下文入口。新会话只读这一份文件即可还原全貌。

## 日期

2026-11-24

## 为什么改

v0.2.40 出包后使用者给出 8 条真机反馈并拍板
**Q1A / Q2A（没弹窗）/ Q3A / Q4A / Q5A / Q6A（小标志）/ Q7（自定义）**。
核查 `AssetsScreen.kt`、`AssetsViewModel.kt`、`AccountDao.kt`、
`AccountTemplates.kt`、`AccountQuickCandidates.kt`、`AppDatabase.kt`、
`Migrations.kt`、`MigrationContractTest.kt` 后，定性如下：

| # | 核查 | 定性 |
|---|---|---|
| 1 | 账户行负余额仍是 `ExpenseRed` 红字且数值本身是负数（基线 `money-precision`「账户余额统一带符号」）；但第 59 批把**顶部蓝底概览区**的「负债 / 小计」全改成白字 | **本会话引入的回归** |
| 2 | 代码里有 `deleting?.let { AlertDialog }`，但真机「没弹窗」。第 59 批把删除收进 `DropdownMenuItem`，`onClick` 里 `menu = false` 与 `deleting = account` 同帧，`DropdownMenu` 的 Popup dismiss 与 `AlertDialog` 新窗口可能冲突 | 真机现象优先，不猜根因；按 Q2A 加固 |
| 3 | `EditAccountDialog` 无卡号输入；`vm.editAccount` → `repository.updateAccountMeta` → DAO `UPDATE` **全程不含 `cardLast4`** | 真缺口 |
| 4 | `addAccount` 早把 `" (1122)"` 拼进 `name` 落库（基线「三击建卡」要求显示名带 `(1234)`），第 59 批 `AccountCard` 又加了一段 `cardLast4?.let { " ($it)" }` → `(1122)(1122)` | **本会话引入的回归**，与 #3 同源 |
| 5 | 只有一套 `sortOrder`；`AccountDao.all() ORDER BY sortOrder, id`；资产页按 `AccountType` 分组、`moveAccount` 第 15 批改分组内交换；记一笔 `AccountQuickCandidates.select(items, selectedId, 4)` 取全局前 4。基线明写「顺序 SHALL 同步到记一笔」——分组内一交换，全局序列里同类型被聚拢，前 4 可能全是某一类 | 需拆两套顺序 → **必须升表** |
| 6 | `AccountTemplates` 仅 12 项：5 家储蓄卡 + 泛用「信用卡」+ 花呗/白条/美团月付/抖音月付 | 清单扩容 |
| 7 | `label`/`name` 都是「工行储蓄卡」，卡种靠 `type` 推断；`AccountQuickCandidates.labelOf` 直接返回 `name` | 改正规名后同名卡会撞名 |
| 8 | 见下「Q7 处置」 | 使用者已拍板，执行层面缺资源输入 |

## Q7 处置：按使用者判断执行，但本批不预置品牌图形

使用者明确回复：

> 「我了解了我们只是用 Logo 来指示账户归属，而且是记账类，没有授权风险。
> 可以从iconfont获取对应logo资源」

**这是使用者在知悉上一轮所列公开权利声明（工行 / 民生官网权利声明、Simple Icons
商标免责、《商标法》第 43 条、第三方逐家许可口径）之后的明确决策**，本批**不再劝阻、
不再否决**，按「用 logo 指示账户归属」的方向落地。

但执行层面有两个必须如实登记的客观事实，不是重新讨论风险：

1. **IconFont 是平台，不是单一开源许可**。上面每个图标由不同作者上传，授权状态
   逐个不同；平台聚合页不等于「可随包分发」。因此 `assets/NOTICE` 的许可证 /
   作者 / 来源审计**不能凭平台名称伪造**，必须逐项登记。
2. **本批拿不到可审计的资源文件**。沙盒访问不了 IconFont 的登录态下载，
   也没有使用者提供的 SVG/PNG 导出包，我不会凭空绘制或临摹银行行徽
   （那比复制更糟），也不会把「平台上有」写成「已授权」。

因此 Q7 本批落地的是**可插拔机制**，资源留作下一步输入：

- 新增 `AccountIconCatalog`：`when(iconKey)` 编译期显式常量表 + `else` 回退
  `CategoryIcon` 中性圆 + 中文首字（与 Tabler 分类图标同构，零运行时字符串反射）。
- `AccountTemplate` 带 `iconKey` 键位；清单把 21 家银行的键位先登记好。
- **资源目录本批为空 → 三页全部回退中性圆 + 首字**。
- 下一批补资源时只需：放入 SVG/PNG → 生成 VectorDrawable → 补 `when` 分支 →
  `assets/NOTICE` 逐项登记作者 / 来源链接 / 授权类型 / 传递依赖 → 真机验观感。

**需要使用者给的输入**（回一句即可）：从 IconFont 导出的资源文件（或图标清单 +
文件），以及每个图标的授权类型。在那之前 `v0.2.41` 包里**不会**出现银行 logo，
这点必须写进交付说明，MUST NOT 含糊成「已完成 Q7」。

design.md「Q7 知识产权登记」小节如实记录「使用者判断」与「公开权利声明」
的差异，标注**由使用者决策并承担**，不写成「无风险」的结论。

## 改什么

- **Q1A**：蓝底概览区每个 `currencyCode` 组的「X 小计」改标「净资产」；
  「负债」恢复红字并以负数文本展示；「资产」保持常规色。仍是**逐币种分组**，
  MUST NOT 出现跨币种单一净资产总数（基线 `reporting-ui`）。
- **Q2A**：删除确认加固为显示账户名 / 余额 / 流水数提示、红色警示、
  按钮改「确认删除」；并用 `pendingDelete + LaunchedEffect(menu)` 把弹窗触发
  与 `DropdownMenu` dismiss **解耦**，确保一定弹出。
- **Q3A**：`addAccount` 的 `name` **不再拼** `(1122)`；全站展示由
  `name + cardLast4` 组合；`EditAccountDialog` 新增卡号输入，
  打通 `vm.editAccount` → `repository.updateAccountMeta` → DAO `UPDATE` 写路径；
  旧库已含后缀的账户做显示去重，覆盖安装不出现双后缀。
- **Q4A**：`AccountEntity` 新增 `quickOrder` 列 → Room `version 5 → 6`；
  `sortOrder` 只管资产页**分组内**排序，`quickOrder` 管**记一笔优先**；
  新增独立「全部账户排序」页（reorderable + 保存 / 放弃 + `draftOrder`）；
  记一笔候选改按 `quickOrder` 取前 4。
- **Q5A**：模板扩到 21 家银行（六大行 + 邮储/交行 + 主要股份行 + 南京银行等城商行），
  **每家储蓄卡 + 信用卡各一条**；网络信用保留 4 项。
- **Q6A**：`label` / `name` 改正规全称「工商银行」；新增 `cardKind`
  （`DEBIT` / `CREDIT` / null）；**用一个 12dp 小角标**（单字「储」/「信」）
  贴在账户图标圆右下角，不占名称宽度（使用者明确要求「最好一个小标志、
  避免挤压账户内容」）；记一笔 chip 同样带小标志。
- **Q7**：见上，机制落地、资源待补。

## 不改什么 / 硬约束

| 基线 | 约束 | 本批做法 |
|---|---|---|
| `reporting-ui`「资产页按币种分组且移除单一净资产总数」 | MUST NOT 跨币种单一净资产 | 蓝底区逐币种分组，仅组内叫「净资产」 |
| `money-precision`「账户余额统一带符号」 | 欠款存负数，不按 type 反向 | 不动金额口径，只改展示文案 / 颜色 |
| `reporting-ui`「管理类页面入口固定且列表与编辑分离」 | 资产账户行纯行 + `ListDivider()` | 保留，不加 Card / 阴影 |
| `ledger-transactions`「账户类型可改与自定义排序」 | `reorderable:3.1.0` + 上移/下移兜底 | 两套排序都沿用；**整块 MODIFIED** 把「顺序 SHALL 同步到记一笔」改为两套解耦 |
| `ledger-transactions`「快捷账户模板替代预制账户行」 | 按资产组 / 信用负债组、点选自动填、可填后四位 | 整块 MODIFIED；语义全保留，只扩清单与正规名 |
| `privacy-security`「不声明网络权限」 | 联网需 `project.md` 显式授权 | Q7 图标是**随包本地资源**，零网络请求；本批不申请授权 |
| `backup-restore` 备份格式 | Entity 加字段不破坏旧备份 | `BackupAccount` 加两字段且带默认值；mappers 双向补齐 |
| 4 Tab 结构 | MUST NOT 加第 5 Tab | 新排序页用 NavHost 已有 `composable` 路由，Tab 数不变 |
| 稳定版前版本策略 | 测试数据不考虑升级迁移 | `version = 6`、`Migrations.ALL` 保持空、不注册迁移；测试期卸载重装、装前导出 |

## 升表口径（第 60 批补充指示：测试数据，不考虑升级迁移）

使用者在确认时追加：

> 「现在都是测试数据，不需要考虑升级迁移」

**因此本批撤回上一版四件套里「必须注册 MIGRATION_5_6 / 覆盖安装数据不丢」的全部要求**，
按 `AppDatabase.kt` 既有惯例处理：

- `AppDatabase version = 5 → 6`（Entity 加两列，schema 变了必须升版本，
  否则 Room 对旧库做 identity 校验时会在 TableInfo 不一致处崩得更隐晦）
- **`Migrations.ALL` 保持 `emptyArray()`，不新增任何迁移常量**
- **`MigrationContractTest` 的 `assertTrue(Migrations.ALL.isEmpty())` 一个字不改**
- **`SeedData` 的 `INSERT INTO accounts` 列清单仍补两列**（全新库 / 备份恢复
  走的是 Entity 创建路径，列必须对齐），VALUES 补 `0, NULL`
- 测试期覆盖安装会开库失败 → **卸载重装，装前先导出**；这不作为缺陷登记
- 从 v1–v5 跨版本升级一律无迁移路径，属既有边界，不在本批范围

**风险等级随之降档**：不再有「真机覆盖安装迁移」这一项；
CI 全绿仍不替代观感 / 手势 / 键盘验收，但**不替代的不再是 migration**。

## 五项对齐结论

1. **问题**：修 2 个回归 + 补 2 个缺口 + 拆两套排序 + 扩模板清单与正规命名 + 定图标机制。
2. **范围**：资产页 / 新建·编辑账户 / 模板 sheet / 记一笔候选 / 新增全账户排序页 /
   Entity·DAO·备份 DTO·Migration / `LedgerApp` 一个路由。Q7 资源不在本批。
3. **约束**：见上表九条；零新增 Gradle 依赖；不复制一羽视觉资产。
4. **验收标准**：**由我拟、非使用者逐字给出**，共 10 条，见 design.md §7；
   使用者可直接改砍补。
5. **被否决方案**：把两套顺序塞回同一个 `sortOrder`（正是 #5 根因）；
   `name` 继续冗余拼卡号后缀（正是 #4 根因）；
   用 `fallbackToDestructiveMigration`；**为测试数据额外注册 Migration**；
   随包分发未经逐项授权登记的 logo；把 Q7 的「无授权风险」写成事实结论。

## 分级判定

含**数据模型变更、首次注册 Room Migration、新路由、新增组件、知识产权判断**
→ **需建 change**（本文件 `refine-account-order-and-templates`，
与尚未归档的 `refine-category-management-pages`、`refine-asset-account-pages` 并存）。
归档仍需使用者明确说「可以归档」。
