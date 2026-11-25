# 提案：补齐「优惠 / 手续费」附加金额能力（add-discount-and-fee）

## Why

一羽记账的「优惠 / 手续费」在实际消费里很常见：满减、券、信用卡还款优惠、
转账手续费。本 App 目前 `RecordEntity` 只有 `amountCents` 一个金额字段，
记「原价 500、券减 29.99、实付 470.01」时只能二选一：
记 500 则账户余额与统计虚高，记 470.01 则原价信息丢失。

竞品定位（已查证）：「支持优惠、手续费等附加金额记录，**还原真实消费情况**」；
官方 CSV 手册把「金额」定义为「**实际的金额**」，「优惠」定义为
「支出类型的账单金额，必须为正数，且不能超过账单金额」，
「手续费」为「转账类型的账单金额，填写负数金额表示转账优惠」。
**一羽没有「累计省了多少钱」这类省钱报表** —— 那是另一类产品思路，
本提案按一羽口径「把一笔账记准」，不引入省钱激励统计。

## What Changes

- `records` 新增可空列 `discountCents`（支出/收入优惠）与 `feeCents`
  （转账手续费；**允许负数表示转账优惠**）；`trash` 同步加两列。
- **`amountCents` 继续存「实付 / 实际金额」**，是唯一真相源；
  原价 = `amountCents + discountCents`。统计、预算、排行、下钻、
  CSV 金额列、余额联动全部沿用现有 `SUM(amountCents)` 口径，零改写。
- 记一笔新增「优惠 / 手续费」入口（支出/收入给优惠、转账给手续费）；
  明细行蓝色「优惠 29.99」小标签 + 划线原价；详情页三行金额可编辑。
- 转账手续费参与余额：转出扣 `amount + fee`、转入收 `amount`
  （DAO SQL 与 `LedgerEngine.deltasFrom` 两处同步），不进收支统计。
- 信用卡还款优惠按一羽官方口径改为 `TRANSFER` + 负 `feeCents`（转账优惠），
  `SETTLEMENT` 保持退款/报销语义干净；详见 design 11.1（Q3A 路径变更，如实登记）。
- CSV 增 `discountYuan` / `feeYuan` 两列（带默认值，老 13 列兼容）；
  加密备份 DTO / 快照 / 回收站补可选字段，旧备份按默认零值解析。
- Room `version 6 → 7`；按使用者既定「测试数据、不考虑升级迁移」口径
  **不注册 Migration**，`Migrations.ALL` 保持空。

## 不改什么（零回归支点）

- 统计、预算引擎、图表、排行、下钻、CSV 复合匹配键
  （`accountName | amountYuan | date | note`）、`accountLabel` 唯一实现
- 记一笔/明细的账户回填逻辑（与活跃 change
  `detail-filter-and-transfer-edit` 重叠，本批不碰）
- 筛选 / 规则不做「按优惠金额」扩展（等 `ff3-description-field` 收口）
- 不新增「手续费」预置分类（避开 `restructure-preset-categories`）
- 零 `INTERNET` 权限、零新增 Gradle 依赖、不做会员墙、不联网
- 三个已归档 change 与另 5 个活跃 change 一律不动

## 五项对齐结论

1. **问题**：补齐一羽式优惠/手续费，覆盖普通收支、信用卡还款优惠、转账手续费。
2. **范围**：2 列 + 记一笔/明细/详情/编辑/CSV/备份/回收站全链路；
   不改统计与余额统计口径（余额联动仅转账手续费一处）。
3. **约束**：整数分铁律；优惠 MUST `0 < d <= amountCents`；
   CSV 复合匹配键不变；旧备份/旧 CSV 兼容；两套余额实现必须同步；
   零新增依赖、零 `INTERNET`；`version 7` 不注册迁移需卸载重装。
4. **验收标准（由我拟，非使用者逐字给出）**：见下 9 条。
5. **被否决**：`amountCents` 存原价；优惠拼进 `note`；优惠做成独立流水
   （重复记账）；给 `SETTLEMENT` 附加金额或自动补冲减流水；会员墙；
   新增预置分类；把「省钱报表」当成一羽已有能力。

## 验收标准（9 条，可测 / 可真机验）

1. 记「原价 500 / 优惠 29.99」落库为 `amountCents=47001`、
   `discountCents=2999`，原价可推导，统计总额按 47001 计。
2. 优惠 MUST 正数且不超过账单金额；非法输入保存前拦截并提示。
3. 转账 1000 + 手续费 5：转出账户扣 1005、转入收 1000，
   DAO 聚合与 `LedgerEngine.deltasFrom` 结果一致；手续费不进收支统计。
4. `feeCents` 为负（转账优惠）时转出少扣，UI 明示为「转账优惠」。
5. 信用卡还款优惠经 `SETTLEMENT + linkedRecordId` 冲减后欠款归零。
6. 明细行显示划线原价 + 蓝色优惠标签；详情页三行金额编辑后往返不丢。
7. CSV 导出含两列、老 13 列 CSV 导入按空串兼容、
   `duplicateKey()` 一字不变、复合匹配 `(name, cardLast4)` 不受影响。
8. 加密备份与快照带两字段；旧备份按零值解析；回收站删除→恢复不丢优惠。
9. `Migrations.ALL` 仍为空、`MigrationContractTest` 零改动、
   六静态检查与 CI 全绿；§8 真机未验项全部留 `[ ]`。

## 分级判定

改变系统行为 + 新增数据列 + 升表 + 新 UI + 与 2 个活跃 change 交叉
→ **需新建 change `add-discount-and-fee`**。不复用已归档 3 个，
也不混入 5 个活跃 change。

## 风险

升 `version 7` 是本批最高风险动作：`Migrations.ALL` 空时覆盖安装会开库失败，
MUST 先导出备份再卸载重装；`SeedData` 只插 categories / accounts，
**没有 records 预置行**，因此 INSERT 列清单无需改。
