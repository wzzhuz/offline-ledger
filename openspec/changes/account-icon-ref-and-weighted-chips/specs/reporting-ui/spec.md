# reporting-ui · delta（第 82 批）

## MODIFIED Requirements

### Requirement: 记一笔低频项由键盘上方芯片直达

「记一笔」**支出 / 收入态** SHALL 在数字键盘上方提供**固定单行 5 枚非等宽 chips**：
账户 / 备注 / 优惠 / 标签 / 模板，顺序 SHALL 为
**账户 → 备注 → 优惠 → 标签 → 模板**，权重 SHALL 依次为
**2.2 / 1.7 / 1.3 / 0.9 / 0.9**；**转账态 SHALL 为 4 枚**：
备注 / 手续费 / 标签 / 模板，依次 **2.0 / 1.6 / 0.9 / 0.9**，
MUST NOT 出现「转出/转入」账户 chip（账户已常驻首屏两张卡）。
MUST NOT 再用 `weight(1f)` 等分。

每枚 SHALL 用 `heightIn(min = 40.dp)` 容器、`RoundedCornerShape(12.dp)`
与 `labelMedium` 字号。**模板 chip SHALL 固定显示「模板」，
选中模板后 MUST NOT 跟着改成模板名**；备注固定「备注」、
优惠固定「优惠」/「手续费」；**标签 SHALL 只显示「标签」或有值时的 `#N`**；
账户 SHALL 用最大权重显示完整账户名。
chip 有值（备注 / 不计入收支 / 优惠 / 标签）或应用模板后 SHALL 以
**右上角 6dp 小圆点**提示，MUST NOT 把摘要拼进 label 造成 ellipsis 截断；
完整值 SHALL 在各自的底部 sheet 内可见并修改，MUST NOT 让旧值不可修改。
「不计入收支」开关 SHALL 并入备注 sheet，MUST NOT 为它新增第 6 枚 chip。
每枚点一下 SHALL 直接打开对应底部 sheet，MUST NOT 经过「更多」这一层。
chips 条 SHALL 位于滚动表单区之外、与键盘同属固定底部区，MUST NOT 挤压首屏。
各 sheet 关闭后 SHALL 保留金额、分类、账户、收款方、描述与保存按钮的全部状态。

#### Scenario: 窄屏不再截字

- **GIVEN** 360dp 宽设备、账户名「工商银行」、备注「同事聚餐 AA」且不计入收支、两个标签
- **WHEN** 打开记一笔且不做任何滚动
- **THEN** 账户 chip 显示完整账户名，模板 chip 仍显示「模板」，标签 chip 显示「#2」
- **AND** 备注 / 优惠 / 标签 chip 右上角有小圆点，点开对应 sheet 可见完整旧值

#### Scenario: 选中模板不改名

- **GIVEN** 用户建了「午餐」模板
- **WHEN** 点「模板」chip 并选中「午餐」后回到首屏
- **THEN** chip 文字仍是「模板」，MUST NOT 变成「午餐」
- **AND** 表单已按模板预填

#### Scenario: 转账态四枚

- **GIVEN** 类型切到转账
- **WHEN** 查看键盘上方
- **THEN** 恰好 4 枚 chips（备注 / 手续费 / 标签 / 模板），窄项收在右端
- **AND** 首屏两张账户卡可见，底部仍是明细 / 资产 / 统计 / 设置 4 个 Tab

**理由**：第 80 批把编辑态摘要塞进 chip label，而 5 枚等分每枚约 64dp，
真机只能看到「备注」，账户名也只剩前半。chip 是入口不是展示位；
按信息量分配宽度、窄项收尾，比两行平铺（+40dp 首屏风险）更贴合低摩擦。

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

「记一笔」支出 / 收入态首屏 SHALL 只呈现：类型 Tab、金额、日期时间无标签副行、
分类快捷行（带 `›` 整行可点全量选择器）、收款方与描述的**无边框内联行**，
以及固定底部区的键盘上方**单行非等宽 chips**、键盘、保存按钮。
**转账态 SHALL 例外**：分类行、收款方行与联想条隐藏，
SHALL 在日期副行之后常驻**转出 / 转入两张账户卡**，其余维持不变。
账户在支出 / 收入态 SHALL 从首屏表单行移到 chips（默认账户预填、低频）；
备注与「不计入收支」、模板、标签、优惠/手续费 MUST NOT 占首屏表单区。
收款方与描述 MUST 仍在首屏可见（描述是明细主标题来源，MUST NOT 藏进面板）。
收款方联想条 SHALL 只在输入框聚焦或有内容时出现，MUST NOT 常驻。

> **第 82 批归档修订**：编辑态旧值原本要求「chips 在 label 上显示可读摘要」，
> 与「固定短标签 + 非等宽」冲突。本批改为「角标提示有值 +
> 点开 sheet 可见并修改完整值」，标题沿用历史名称（归档匹配基准）。

#### Scenario: 旧内容不因收起丢失

- **GIVEN** 编辑一条带备注、优惠、两个标签、且不计入收支的旧流水
- **WHEN** 打开编辑态
- **THEN** 备注 / 优惠 / 标签 chips 右上角显示小圆点
- **AND** 点开对应 sheet 可查看并修改全部字段，MUST NOT 让任何旧值不可修改

#### Scenario: 转账态两张账户主字段常驻

- **GIVEN** 用户切到「转账」Tab
- **WHEN** 不做任何滚动、不点任何 chip
- **THEN** 首屏直接看到「转出账户」与「转入账户」两张卡及其当前账户名
- **AND** MUST NOT 需要先打开底部 sheet 或切换分段才能看到它们

### Requirement: 记一笔各表单行高度量级一致

各表单行内容高度 SHALL 落在 **32–44dp** 区间。收款方与描述的
`OutlinedTextField` SHALL 把 `colors` 的 border 与 container 设为
`Color.Transparent`，MUST NOT 保留整圈描边；MUST NOT 改用 `BasicTextField`
或 filled 变体。一键清空图标、placeholder、`heightIn(min = 40.dp)` 与
`bodyMedium` 字号 SHALL 保持不变。一键清空图标 SHALL 用 36dp 容器 +
18dp Icon，MUST NOT 用默认 48dp 的 `IconButton` 把输入行撑高。
键盘上方 chips 与收款方联想条的每一个可点项 SHALL 用
`heightIn(min = 40.dp)` 容器承载，字号 SHALL NOT 低于 `labelMedium`；
**chips 改非等宽 `weight(...)` 后，最窄项 MUST NOT 低于 40dp 点击区，
右上角 6dp 角标 MUST NOT 成为独立点击目标或缩小可点区域。**

#### Scenario: 窄项仍一次点中

- **GIVEN** 记一笔首屏，模板 / 标签两枚窄 chip 约 41dp 宽
- **WHEN** 点其中任一枚
- **THEN** 一次点击即打开对应 sheet，SHALL NOT 误触相邻 chip 或点不中
- **AND** 可点区不低于 40dp、字号为 `labelMedium`

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。**支出 / 收入态**账户与模板
SHALL 不再作为首屏表单按钮或平铺快捷 chips 存在，统一由键盘上方 chip
直达底部 sheet；**转账态**账户 SHALL 以两张首屏卡片常驻，MUST NOT 再进 chips。
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
账户选择面板 SHALL 按本批新增 Requirement
「账户选择面板展示品牌图标与卡种」呈现，MUST NOT 再是纯文字列表。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随第 80 批作废。

#### Scenario: 账户 chip 显示默认账户（收支态）

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 打开记一笔（支出）
- **THEN** 账户 chip 完整显示该账户名，点开两列 sheet 可按「1122」搜索
- **AND** 首屏表单区 MUST NOT 再出现「账户」行

## ADDED Requirements

### Requirement: 账户选择面板展示品牌图标与卡种

账户选择底部 sheet 与转账态两张账户卡 SHALL 复用项目既有
`AccountIconBox` 展示账户图标（预制品牌 / 自定义 / 中性首字回退），
图标尺寸 SHALL 为 **44dp**；卡种角标 SHALL 按图标尺寸等比放大
（`size * 0.4`，约 17dp，字号 `size * 0.5`），MUST NOT 再用
12dp / 7sp 的「储 / 信」小圆。
面板与两张卡 MUST NOT 显示账户余额（使用者明确「显示余额没必要，
不需要抄一羽」）；卡号后四位与卡种 SHALL 降为次要小字
（形如 `尾号 1122 · 储蓄卡 · CNY`），由单一组合点 `accountSecondaryLabel`
生成，SHALL NOT 要求使用者记忆卡号。
搜索 SHALL 仍按 `accountBaseLabel`（账户名含卡号后缀）过滤，零数据路径改动。
MUST NOT 为对齐截图新增 Gradle 依赖或图标资源。

#### Scenario: 一眼分储信

- **GIVEN** 用户有「工商银行」储蓄卡与「招商银行」信用卡
- **WHEN** 打开账户选择 sheet
- **THEN** 每格有 44dp 品牌图标与放大角标，次要行显示「尾号 xxxx · 储蓄卡 / 信用卡」
- **AND** 不出现余额数字，MUST NOT 只剩纯文本 + 12dp 小角标

#### Scenario: 自定义图标也走同一位置

- **GIVEN** 某账户在编辑页导入了自定义 PNG
- **WHEN** 打开账户选择 sheet / 资产页 / 排序页 / 模板预览
- **THEN** 四处都显示该自定义图标，尺寸 / 角标与预制一致
- **AND** 导入失败或文件缺失时回退中性圆 + 中文首字，MUST NOT 显示空白

### Requirement: 账户可关联并导入本机图标

`AccountEntity` SHALL 提供 `iconRef: String?`（`accounts` 表 version 9），
取值 SHALL 为 `null` / `auto`（按账户名反查 `AccountTemplates` 自动推断）、
`preset:<iconKey>`（显式关联预制品牌，改名 MUST NOT 丢）、
`custom:<uuid>`（本机导入）。编辑账户 SHALL 随「保存」一次写回该列，
MUST NOT 分两次事务导致图标与元信息不一致。
账户编辑页 SHALL 提供「预置（26 项网格）/ 导入 PNG / 自动」三入口；
预置网格 SHALL 用编译期 `AccountIconCatalog.drawableFor` 常量表，
MUST NOT 用运行时字符串反射（T14）。

导入 SHALL 复用既有 SAF `ActivityResultContracts.OpenDocument()` +
`contentResolver.openInputStream`，只收**透明底 PNG**；
无透明区域的图片 SHALL 被拒绝并给出可读提示，MUST NOT 做自动抠图。
生成物 SHALL 与第 64 批 26 个预置账户 PNG 同构：
128×128、白圆底、图形 56% 内缩、保留 alpha 的统一单色剪影，
写进应用私有 `filesDir/account-icons/`，**不进备份、不随 APK 分发**。
导入图标与预制图标 SHALL 共用同一 `AccountIconBox` 尺寸 / 容器 / 角标。
**分类自定义图标入口本批 MUST NOT 提供**；`CategoryIconCatalog`、
90 个 Tabler drawable 与 `default_categories.json` SHALL 零改动
（使用者第 3 项：后续需要时直接发新版追加 Tabler 预置资源）。

`Migrations.ALL` SHALL 保持空数组（使用者明确测试数据、卸载重装）；
备份 DTO SHALL NOT 扩该字段，导出 / 导入后 SHALL 回退自动图标，
CSV / Firefly 导入新建账户 SHALL 用默认 `null`。

#### Scenario: 改名不丢图标

- **GIVEN** 账户「工商银行」在编辑页显式关联 `preset:icbc`
- **WHEN** 把名称改成「我的工资卡」并保存
- **THEN** 图标仍是中国工商银行徽记，MUST NOT 回退成中文首字

#### Scenario: 导入与预制并排

- **GIVEN** 用户导入一张透明底 PNG 给某账户
- **WHEN** 在账户 sheet 与预置品牌账户并排查看
- **THEN** 两者同为 128×128 白圆底、同尺寸、同角标，观感一致
- **AND** 单色剪影 MUST NOT 被写成「与 Tabler 线性笔画同款」（如实登记差异）

#### Scenario: 非透明图被明确拒绝

- **GIVEN** 用户选了一张无透明区域的普通照片
- **WHEN** 确认导入
- **THEN** 显示「请使用透明底 PNG，App 不做自动抠图」，图标保持原值

#### Scenario: 不进备份

- **GIVEN** 用户导出备份后在另一设备恢复
- **WHEN** 查看该账户图标
- **THEN** 回退按账户名自动推断，MUST NOT 因私有目录文件缺失而崩溃或空白

#### Scenario: 升表卸载重装

- **GIVEN** 设备已装 v0.2.60
- **WHEN** 直接覆盖安装 v0.2.61
- **THEN** 开库失败（version 9 且无 Migration），属已知口径
- **AND** 交付说明 MUST 写明「卸载重装，装之前先导出备份」
