# Delta for 界面与交互

## ADDED Requirements

### Requirement: 记一笔提供模板快选且两步内完成

「记一笔」Dialog SHALL 在顶部提供常用模板 chips，
点一下 SHALL 直接生成流水并关闭弹窗，MUST NOT 弹二次编辑。
从打开记一笔到完成记账 SHALL 不超过 2 次点击。
系统 SHALL 保持既有两级分类快选与计算器键盘的交互不变。

#### Scenario: 两步记完午餐

- **GIVEN** 桌面或 FAB 打开记一笔
- **WHEN** 点「午餐」模板
- **THEN** 直接保存 3500 分支出并关闭弹窗
- **AND** 明细页出现该笔流水

#### Scenario: 没有模板时

- **GIVEN** 尚未创建任何模板
- **WHEN** 打开记一笔
- **THEN** 不显示模板 chips
- **AND** 原有手工记账路径完全不受影响

**理由**：「时间长了没动力」的根源是重复输入；模板若还要再弹一次编辑
就等于没省步骤。

---

### Requirement: 桌面快捷方式直达记一笔且不绕过应用锁

系统 SHALL 提供静态 App Shortcut，点开 SHALL 直接进入「记一笔」
而非首页。启用应用锁时，快捷方式 SHALL 仍先展示 `AppLockScreen`，
MUST NOT 提供绕过锁的路径。快捷方式 SHALL NOT 申请任何权限。

#### Scenario: 桌面直达

- **GIVEN** 长按应用图标选择「记一笔」
- **WHEN** 启动
- **THEN** 直接显示记一笔 Dialog
- **AND** 底部导航仍是 4 个 Tab

#### Scenario: 已启用应用锁

- **GIVEN** 应用锁已开启，从桌面快捷方式启动
- **WHEN** 未解锁
- **THEN** 只显示解锁界面，不渲染任何账目
- **AND** 解锁后才进入记一笔

**理由**：低摩擦不能以牺牲「数据是你的」为代价；
快捷方式是 framework 静态能力，零依赖零权限。

---

### Requirement: 模板与规则管理收纳进设置页且不改导航

设置页 SHALL 新增「模板与周期记账」入口，Dialog SHALL 提供
模板 / 周期规则两个 Tab 与新增、归档、启用开关。
系统 SHALL 保持恰好 4 个底部 Tab，MUST NOT 新增导航格数，
MUST NOT 把管理界面做成第 5 个 Tab。

#### Scenario: 打开管理

- **GIVEN** 在设置页
- **WHEN** 点「模板与周期记账」
- **THEN** 弹出 Dialog，含模板与周期规则两个 Tab
- **AND** 关闭后底部仍是 4 个 Tab

#### Scenario: 归档模板

- **GIVEN** 一条不再使用的模板
- **WHEN** 归档
- **THEN** 从模板 chips 消失
- **AND** 已由它生成的历史流水不受影响

**理由**：4 Tab 结构在阶段 2 已一次成型，且经过三次 MODIFIED 收口；
新功能只能收纳进已有页面。
