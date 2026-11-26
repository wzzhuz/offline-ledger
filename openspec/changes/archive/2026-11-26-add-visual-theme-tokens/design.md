# 技术设计：视觉规范适配与真机反馈修复

## 1. 三个真缺口与一个隐患（第 78 批）

审阅仓库 `main`（`33d2512` / v0.2.56）后的事实盘点：

| 缺口 | 现状证据 |
|---|---|
| 深色模式 | 全仓 0 处 `darkColorScheme` / `isSystemInDarkTheme`；第 77 批 API 31+ 用 `dynamicLightColorScheme`，恒为浅色 |
| 金额等宽数字 | 全仓 0 处 `fontFeatureSettings` / `tnum`；项目无自定义 `Typography`，`LedgerTheme` 只传 `colorScheme` |
| 设计 token | `RoundedCornerShape` 散落 2/3/4/8/12/14/16/24dp，padding 六档；仅 1 处 Card 显式 16dp |
| 两套蓝（已登记隐患） | `LedgerColors.Primary` 是 object 常量、237 处引用 / 34 文件直接读，不随 `colorScheme.primary` 变；`CategoryHeaderBlue` 另写一份蓝 |

## 2. 关键决策：可观察调色板，而非 CompositionLocal

`formatCents`、`amountColor` 是普通函数，`DonutChart` 在 draw phase、
图例 `values.map` 都在非 `@Composable` 上下文读 `LedgerColors`。
`CompositionLocal` 在这些地方读不到，硬迁 237 处会让「配色反转」开关失效。

做法：`LedgerColors` 从「常量 + 两个可观察语义色」升为整块可观察状态，
由 `LedgerTheme` 在一次 `SideEffect` 里同步当前 `ColorScheme`。
写相同值不触发重组，不会形成循环；`applyReverseColors` 反转状态在深浅切换后保留。

## 3. 深色不走系统动态深色

动态深色的 `primary` 是浅色调，小面积主色（FAB、进度条）上的白图标会失去对比。
静态深板 `#121417` 底 / `#1E2025` 卡 + 固定蓝 primary + 白 onPrimary，
同时保住「冷静克制」与语义色可读。红绿在深色下换
柔和珊瑚红 `0xFFF0A6A0` / 柔和绿 `0xFF9CCFA6`。

## 4. 第 79 批：两条真机反馈的根因

| 反馈 | 根因 | 修复 |
|---|---|---|
| 统计页浅色卡片分界不明显 | 第 78 批把 `StatsCard` 容器从固定 `Color.White` 改成读 `scheme.surface`，而统计页底来自 `Scaffold` 的 `scheme.background`；**动态浅色下二者是同一档近白**。叠加间距由 16dp 收到 12dp，边界彻底消失 | 拆出 `Background` / `CardSurface` / `OnSurface` 三语义；`dynamicWithNeutralSurface()` 只借动态强调色，中性槽位套回静态板；卡片容器固定纯白、间距回 16dp |
| 蓝底顶栏太突兀 | `CategoryHeaderBlue` 改读 `LedgerColors.Primary` 后，动态色下系统 primary 可能更艳；6 处整块蓝底 + 白字与「微灰底 / 白卡」体系本就不搭 | 6 处顶栏与资产页身份区改中性卡片 + `OnSurface` 文字；主色只留小面积点缀 |

**实现细节**：`dynamicWithNeutralSurface()` 刻意用 `lightColorScheme(...)` 显式构造，
而不是 `ColorScheme.copy(...)` —— 参数名与静态板共用同一套，避免依赖
`copy` 在不同 M3 版本下的参数集合。

**顶栏改造清单**：资产页身份区、添加资产账户 TopBar、全部账户排序 TopBar、
分类管理 / 分类选择 / 分类排序三页顶栏。
配对处理：白字白图标 → `OnSurface`（次要信息 72% 透明）、
蓝底专用浅红负债字 → 语义 `ExpenseRed`、
统计页选中汇总气泡 → 主色 12% 透明底 + `OnSurface` 字、
分类排序选中态 → 12% 底 + 主色字、FAB 图标 → `OnPrimary`。

## 5. 考虑过的其他方案

| 方案 | 为什么否决 |
|---|---|
| 照搬原视觉规范：删全站分割线 + 明细 / 记一笔 / 资产列表卡片化 | 直接违反 `reporting-ui`「明细页极简网格与中性分类标识」「记一笔采用统一表单网格」等既有 Requirement；指南 §2.2 记过「靠加卡片填满空白会把刚降下去的噪点加回来」 |
| `CompositionLocalProvider` 暴露 `IncomeColor` / `ExpenseColor` | 237 处引用含 draw phase / 普通函数，非 Composable 上下文读不到；会破坏「反转开关无需逐个改写即生效」的既有约定 |
| 彩色分类图标 40dp 容器 + 分类色 12% 透明度 | 撤销第 55/56/68 批逐次放行的口径（彩色仅限分类管理三页 + 两个选择器 + 统计占比图例）；明细行仍是 30dp 中性圆 + 中文首字 |
| `Icons.Rounded.*` 替换默认图标 | 项目只引 `material-icons-core`，注释写明刻意不引数 MB 的 `material-icons-extended`；已用 Tabler Icons v3.34.0 / MIT / 90 个本地 drawable + NOTICE 落地 |
| 系统动态深色 | 见 §3，白图标对比度 |
| 卡片间距 12dp（第 78 批实现） | 真机反馈边界糊，回退到第 70 批已验证的 16dp |
| 顶栏改主色 12% 透明底（保留一点色） | 留作后续备选，本批先按「不搭 / 太突兀」的反馈彻底中性化，真机复验后再决定是否加回 |
| 缩放动画 / 自定义 Ripple | Ripple 已由 M3 默认提供；项目在拖拽排序上有手势坑，收益低 |

## 6. 范围与零回归

- 零改表（`version = 8` 不动）、零新增 Gradle 依赖、零 `INTERNET` 权限变化
- 静态脚本断言随版本与主题机制同步：`check_record_layout.py`（0.2.58）、
  `check_stats_merge.py`（容器色断言改为锁「显式指定容器色」这一意图）
- 12 个静态检查全程 PASS；两轮 CI 双全绿
- 既有 Requirement 的语义（分割线 / 纯行网格 / 4 Tab / 彩色图标范围 / 零改表）一字未删，
  归档时按流程对 7 条被行为改动影响的 Requirement 做 MODIFIED、新增 3 条 ADDED

## 7. 已知限制 / 真机待验

- `tnum` 依赖字体 OpenType 特性，部分 OEM 中文字体可能不生效，不写成「所有机型必然生效」
- 次级文字色从固定 `#616161` 改为跟随 `onSurfaceVariant`，浅色下略浅
- 深色模式、动态色逐页观感、16dp 间距、顶栏中性卡片层级、覆盖安装等列在活文档 §8
- CI 全绿不替代真机观感验收
