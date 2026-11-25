# reporting-ui · Delta Specification

> 第 75 批：标签管理页与设置入口。1 条 ADDED。

## ADDED Requirements

### Requirement: 设置页提供标签管理入口与独立标签管理页

`SettingsScreen` SHALL 在「账本管理」组提供一行「标签管理」入口，
点击 SHALL 导航到 `composable("tags")` 的独立 `TagManagerScreen`，
二级页 SHALL 隐藏底部 4 Tab 并提供返回。
管理页 SHALL 支持新建、改名、排序、归档标签，SHALL 显示每个标签下的流水笔数；
默认 SHALL 用归档而非物理删除，归档项 MUST NOT 出现在记一笔的多选弹层。
预置「给老婆 / 家用 / 旅行」三个标签 SHALL 只在 `SeedCallback.onCreate` 种入，
使用者归档过的标签 MUST NOT 在下次启动被复活。
页面 SHALL 明示「标签与分类互补：分类一笔一个、标签一笔多个」。
底部 4 Tab 与中央记账按钮 MUST NOT 因本批改变。

#### Scenario: 从设置进入管理页

- **WHEN** 设置页点「标签管理」
- **THEN** 进入独立页、底栏隐藏、可返回

#### Scenario: 改名不重复

- **GIVEN** 标签「家用」下有 12 笔流水
- **WHEN** 改名为「家庭」
- **THEN** 12 笔仍关联同一行标签，列表不出现两个「家庭」

#### Scenario: 归档不复活

- **GIVEN** 使用者归档了预置标签「旅行」
- **WHEN** 重启 App 再进标签管理页
- **THEN** 「旅行」仍为已归档，MUST NOT 被 `SeedCallback` 重新种入为启用态

#### Scenario: 多标签说明可见

- **WHEN** 首次进入标签管理页
- **THEN** 可见「分类一笔一个、标签一笔多个」的口径说明
