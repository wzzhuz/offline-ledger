# 交付加密备份与完整设置

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原话与项目现状）**：

- 使用者最初的动机是「**资产数据是高度敏感数据，不信任第三方**」，
  以及「数据存在他们服务器上，部分功能收费」
- 阶段 0–4 全部完成并归档：8 个模块 / 116 条 Requirement / 166 个 Scenario、
  162 个文件、CI 全绿（`b92038a2a6` / `9ddfa6cde8`），已产出约 22.7 MB debug APK。
  四个 Tab 都是真内容，三个原始诉求（资产、分类支出、年终曲线）都已落地
- **但第一条铁律「数据是你的」只补完了一半**：Manifest 无 `INTERNET`、
  `allowBackup=false`、SQLCipher + Keystore 都做了；
  **「数据随时可带走」目前只有阶段 2 的临时明文 JSON 导出**（写应用私有目录），
  **「设备丢了别人也看不到」的应用锁与 FLAG_SECURE 还没做**
- 阶段 4 归档时记录了 **3 处规格债**，其中第 ③ 条最麻烦：
  `reporting-ui`「v1 范围收敛」把 CSV 列入 v1.1 禁止清单，
  但 `backup-restore`「CSV 格式约定」、`privacy-security`「导出文件默认加密」、
  `project.md` 阶段 5 表格都把 CSV 当 v1 功能——**两处长期约束互相矛盾**

**问题**：阶段 5 是 v1 的收口阶段，必须把「导出文件默认加密」「应用锁」
从 spec 文字变成可运行代码，同时把临时明文导出入口换掉、把偏好与分类管理
收进设置页，并修掉阶段 4 遗留的规格债。

## 做什么

1. **SAF 加密 JSON 导入导出**：`CreateDocument` / `OpenDocument`，用户选位置，
   口令派生密钥 + Gzip 压缩 + AES-256/GCM 加密；覆盖导入二次确认
2. **应用锁**：`androidx.biometric:biometric:1.1.0` 生物识别 + 自建 PIN 兜底，
   冷启动与后台恢复都校验；`FLAG_SECURE` 开关防最近任务预览泄露
3. **设置页收口**：阈值可配置、配色反转开关、FLAG_SECURE、应用锁与 PIN 设置、
   账本健康页、分类管理（新增 / 改名 / 排序 / 归档）
4. **4 份 MODIFIED**：修掉阶段 4 遗留的 3 处规格债 + CSV 口径统一
5. **CSV 按拍板结论**：v1 不做，统一标注为 v1.1 预留（使用者确认「按推荐来」）
6. 纯 JVM 加密往返单测；静态检查扩到新权限白名单与 17 个写方法

## 不做什么

- ❌ **不改表结构 / 备份信封头部 / `schemaVersion`**：`AppDatabase` 仍 `version = 1`，
  **无 Room Migration**；`CategoryEntity` 在阶段 1 已预留 `isArchived` / `sortOrder`，
  偏好走 SharedPreferences，不进 Room、不进备份 DTO
- ❌ 不改 4 Tab 导航格数、中央 `+` FAB；设置项全部收纳进已有第 4 个 Tab
- ❌ 不改明细 / 记一笔 / 资产 / 统计的既有业务行为（配色开关只换语义色）
- ❌ **v1 不提供 CSV 导出入口**（拍板结论 B）；不做模板 / 周期账、快捷方式、
  预算、退款报销、多币种混算、三级分类、账单导入
- ❌ 不申请 `READ/WRITE_EXTERNAL_STORAGE`，**不声明 `INTERNET`**，
  不引入 OkHttp / Coil / Glide / 广告 / 埋点 / 崩溃上报 SDK
- ❌ 不改 `wzzhuz/my-app-workflow`、不改 `docs/设计规格书_v2.md` 历史快照
- ❌ 不建 `release.yml`、不配正式签名 Secrets（出包阶段单独提 change）
- ❌ 不在沙盒内编译 APK 或做真机验证（无 SDK / 模拟器）

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。

1. **要解决什么问题**
   把第一条铁律「数据是你的」从 Manifest 与数据库层补完到最后两环：
   **数据能安全地随时带走**（SAF + 口令加密备份）、
   **设备丢了别人也看不到**（应用锁 + FLAG_SECURE）。
   同时把阶段 2 的临时明文导出换掉，兑现「导出文件默认加密」，
   并把阈值 / 配色 / 分类管理收进设置页。这是 v1 的收口阶段。

2. **范围边界**
   - 改：SAF 加密导入导出、应用锁与 FLAG_SECURE、设置页、分类管理、
     阈值与配色偏好、4 份 MODIFIED、`USE_BIOMETRIC` 权限白名单、
     `WRITE_METHODS` 13→17、纯 JVM 加密单测
   - 不改：表结构 / `schemaVersion` / 信封头部、4 Tab 格数、既有业务行为、
     v1.1 清单、历史快照、正式签名与分发方案

3. **关键约束**
   **两套密钥必须分开**：SQLCipher 的 Keystore 口令绑设备、重装即失效、
   用户不可见；备份口令由用户自设、PBKDF2 派生、跨设备可恢复。
   MUST NOT 复用或互相推导。
   加密 SHALL 用 `PBKDF2WithHmacSHA256` + `AES/GCM/NoPadding`，
   随机盐与 IV 随文件头写出，口令 MUST NOT 持久化；
   编解码层保持纯 JVM 可单测。
   导出 SHALL 流式 Gzip + Cipher，MUST NOT 产生第二份明文副本；
   导入复用已有 `importBackup()` 单事务 + `rebuildAllBalances()`，
   解析 / 校验失败整体回滚。
   SAF 不申请 `READ/WRITE_EXTERNAL_STORAGE`（官方明确 SAF 无需任何存储权限）；
   新增 `USE_BIOMETRIC` 是 normal 级无害权限，需写进静态检查白名单，
   **仍 MUST NOT 出现 `INTERNET`**。
   偏好用 SharedPreferences（PIN hash 加盐），不进 Room、不进备份、
   不触发 Migration；分类管理复用已预留的 `isArchived` / `sortOrder`，
   禁止物理删除有流水的分类，归档不改历史 `rootCategoryId`。
   所有新增写方法 MUST 包在 `withTransaction` 并登记进 `WRITE_METHODS`；
   统计口径与金额铁律不变；沙盒无 SDK，编译与单测由 Actions 验证；
   推送用单次 commit；Python 拼 Kotlin 后必跑五个检查。

4. **验收标准**（可测）
   - **GIVEN** CI 跑完 **THEN** 五个静态检查 / 单测 / debug / release(lintVital) 全绿，
     且 Actions 列表能查到该次运行
   - **GIVEN** 口令 `abc123` 导出再导入 **THEN** 账户 / 分类 / 流水 / 校准 /
     锚点全量一致、余额重算后净资产一致
   - **GIVEN** 用错口令 **THEN** 解密失败且原账本完全不变
   - **GIVEN** 备份文件中途损坏 **THEN** 事务回滚、不留半套数据
   - **GIVEN** 覆盖导入 **THEN** 先弹「将全量替换现有全部数据」二次确认，
     取消不写任何数据
   - **GIVEN** 启用应用锁冷启动 **THEN** 校验通过前不展示任何账目，
     失败不进主界面
   - **GIVEN** 导出加密文件 **THEN** 用文本编辑器打开看不到明文分类名与金额
   - **GIVEN** 归档阶段 5 **WHEN** 校验 specs **THEN** 4 份 MODIFIED 全部按标题替换、
     CSV 归属在 `project.md` 与各模块 spec 一致
   - **GIVEN** 推送完成 **WHEN** 用 API 校验 **THEN** sha 一致、无 token 明文

5. **被否决的方案**
   一句话结论：**所有「跳 change / 把 CSV 悄悄做进去 / 用 Keystore 口令当备份口令 /
   口令存明文 / `Runtime.exec` 调 openssl / `File(path)` 写公共目录 /
   把偏好塞进 Room 触发 Migration / 应用锁做成第 5 个 Tab 或只放开关不拦冷启动 /
   物理删除分类 / 只追加 ADDED 不改规格债 / 顺手做 v1.1 功能 /
   引入含网络行为的依赖」的方案均被否决**，
   采用「SAF + 用户口令 PBKDF2 派生 + AES-GCM + 复用既有导入事务 +
   BiometricPrompt/PIN 冷启动门禁 + SharedPreferences 偏好 + 4 份 MODIFIED」。
   完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：`backup-restore`（主，2 份 MODIFIED）、`privacy-security`（主，1 份 MODIFIED）、
`reporting-ui`（1 份 MODIFIED + 设置页）、`category-stats`、`reconciliation`、
`build-and-ci`。

**是否需要重新安装包**：否（签名未变，数据库结构未变，无 Migration）

**是否会丢数据**：否（新增导入是覆盖操作，但强制二次确认 + 单事务回滚）

**行为变化**：设置页从「明文导出 + 自检 + 回收站」变为完整设置中心；
冷启动与后台恢复在启用应用锁时先校验；其余页面行为不变。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（本轮兑现「导出文件默认加密」「数据随时可带走」「应用锁」）
- [x] 不涉及「已确认不做」清单中的功能（CSV 按拍板结论**留在** v1.1）
- [x] 无冲突

**补充说明**：阶段 4 归档 `design.md` 明确要求本 change 处理
① 「后续阶段不改导航」Scenario 仍含阶段号、② 明文导出入口将被替换、
③ CSV 两处长期约束互相矛盾。本 change 用 **4 份 MODIFIED** 全部收口，
其中第 4 份是写码前逐条扫 specs 时新发现的（详见 `design.md` 第 2 节）。
