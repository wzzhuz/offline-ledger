# Delta for privacy-security

## ADDED Requirements

### Requirement: 账户品牌图标资源逐项登记且缺项回退

账户图标 SHALL 由 `AccountIconCatalog` 以**编译期显式常量表**
（`when(iconKey)`）提供，MUST NOT 用运行时字符串反射 / 查表映射。
`AccountTemplate.iconKey` SHALL 为资源键位预留，
资源 SHALL 为**随包本地 VectorDrawable**，MUST NOT 引入在线下载、
MUST NOT 因此申请 `INTERNET` 权限、MUST NOT 新增 Gradle 依赖。
每一份引入的品牌图形 SHALL 在 `assets/NOTICE` 逐项登记
**作者 / 来源链接 / 授权类型 / 传递依赖**，
MUST NOT 用「来自某图标平台」代替逐项审计
（平台图标由不同作者上传、授权状态逐个不同）。
**本批资源目录为空**：所有账户 SHALL 回退
`CategoryIcon` 的中性圆 + 中文首字，MUST NOT 凭空绘制或临摹银行行徽，
MUST NOT 复制一羽记账的视觉资产。
Q7 的使用者判断（「指示账户归属、记账类、无授权风险」）
与其公开权利声明的差异 SHALL 在 design.md「Q7 知识产权登记」
如实记录并标注**由使用者决策并承担**，
MUST NOT 写成「已解决」或「无风险」的事实结论。

#### Scenario: 资源缺项不崩

- **GIVEN** 本批 `AccountIconCatalog` 无任何已登记资源
- **WHEN** 打开资产页 / 模板 sheet / 记一笔
- **THEN** 所有账户显示中性圆 + 中文首字，页面正常渲染、零网络请求

#### Scenario: 补资源时只改目录

- **GIVEN** 后续拿到逐项授权登记后的图标文件
- **WHEN** 补 `when` 分支与 `assets/NOTICE` 条目
- **THEN** 三页 UI 代码零改动即可显示对应图标
- **AND** 未登记的 `iconKey` 仍回退中性圆
