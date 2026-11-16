# polish-settings-pages · Proposal

## 为什么

使用者体验 v0.1.8 后转向设置 Tab，反馈三点：

- 分类管理里「分类列表和新增分类在一个页面」，分类越多新增入口越往下移，局部留白多、不美观
- 模板管理存在同样的通用问题
- 回收站多条记录之间留白也很大

并追加「顺带检查其他没提到的页面」。

这是活文档 `docs/页面优化经验与避坑指南.md` 生效后第二次外推到非记一笔页面。
全局扫描 36 个 UI 文件后确认：前 11 批已经统一过的写法（`FilterChip` 底色选中、
`placeholder` 输入框、选项不限高、`LazyColumn`）在设置类老页面成片残留，
且预算管理页有完全相同的结构问题。只修被点名的 3 页，等于把修复留成下一次再犯。

## 改什么

- 4 个管理页（分类 / 模板 / 预算 / 回收站）重排为「**顶部固定新增入口 + 纯列表 + 编辑独立视图**」
- 列表改 `LazyColumn`；每行 4 个 48dp `TextButton` 收进紧凑「⋯」菜单；回收站行重排但保留全部信息字段
- 全仓 `● ` 文字前缀清零（9 处），统一 `FilterChip` / `selected` 底色
- 管理类 18 处浮动 `label` → `placeholder` + `bodyMedium` + `heightIn(min=40)`
- 模板分类下拉限高 + 搜索
- 2 份 MODIFIED 按标题整块替换修规格腐烂 + 1 条 ADDED 沉淀长期约束
- 版本 0.1.8 → 0.1.9，出包带短 sha

## 不改什么

表结构、备份格式与 `schemaVersion`、4 Tab、NavHost 路由图、DAO 与写方法、
金额铁律、统计口径、Manifest 权限、`release.yml`、历史快照、
操作型浮层的一次性短表单（10 处浮动 `label`，理由见 `design.md` §2.1）、
第 5 批补齐的回收站信息字段、筛选面板第 11 批成果。

## 影响

| 模块 | 影响 |
|---|---|
| `reporting-ui` | 主：2 MODIFIED + 1 ADDED + 设置类页面布局 |
| `category-stats` | 分类管理行为（只读，不改 Requirement） |
| `ledger-transactions` / `backup-restore` / `privacy-security` / `money-precision` | 只读，无 delta |
| `build-and-ci` | 版本号与静态检查（只读，无 delta） |

## 风险

- 跨 4 个页面 + 27 处写法统一 + 2 个 `Column(verticalScroll)` → `LazyColumn`，
  是第 11 批之后改动面最大的一次 UI 重构，首次 CI 大概率要一轮 import / 状态提升修复；
  按 `design.md` §3.3 回退顺序每次只改一处单独推送
- **「CI 全绿」不等于「6.73 寸上一屏能看几条、编辑视图进出顺不顺」**，需使用者装机确认
- 零表结构变更，覆盖安装即可，不用卸载

## 分级

新功能（固定新增入口 / 编辑视图）+ UI 重构 + 2 份 MODIFIED → 必须建 change。
`compact-filter-sheet` 已按规范先归档，本 change 新建而非并入。
