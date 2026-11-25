# category-stats · Delta Specification

> 第 75 批：统计标签维度。1 条 ADDED。

## ADDED Requirements

### Requirement: 统计页提供标签维度且不硬算百分比

统计页 SHALL 提供标签维度，按当前周期与当前币种聚合每个标签的
**命中金额与笔数**，SHALL 用相对条形体现大小，
MUST NOT 硬算百分比或把标签金额加总成一个「总支出」
（一笔可挂多个标签，合计会超过总支出，给了反而误导）。
卡片 SHALL 明示「一笔可含多个标签，合计可能大于总支出」。
`TagDao.tagStats` SHALL 走 `record_tags` 关联聚合，
MUST NOT 修改既有 `SUM(amountCents)` 分类 / 收款方 / 账户聚合 SQL，
MUST NOT 跨币种混算、MUST NOT 改变净支出切片口径。
标签维度 SHALL 与分类占比卡彼此独立，MUST NOT 把两者合并到同一容器。

#### Scenario: 多标签合计不误导

- **GIVEN** 本月总支出 ¥1000，其中一笔 ¥200 同时挂「家用」与「给老婆」
- **WHEN** 查看标签维度
- **THEN** 两行各自显示命中金额与笔数、相对条形
- **AND** 页面无百分比、无「标签合计 = 总支出」的表述

#### Scenario: 与分类口径独立

- **WHEN** 切换标签维度的选中项
- **THEN** 分类占比卡的数字与扇区 MUST NOT 跟着变化

#### Scenario: 按当前币种

- **GIVEN** 账本含 CNY 与 HKD 流水
- **WHEN** 币种切到 CNY
- **THEN** 标签命中金额只统计 CNY，MUST NOT 混算
