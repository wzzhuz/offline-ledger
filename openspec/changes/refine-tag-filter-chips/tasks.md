# tasks · refine-tag-filter-chips

- [x] 0.1 阶段 0 对齐（Q1A / Q2A / Q3A，验收按拟）
- [x] 0.2 新建 change 四件套 + 静态脚本，本地静态检查
- [x] 1.1 `TagChips` 改轻量行内标记（10sp / 单行 / limit 参数）
- [x] 1.2 `RecordDetailPage` 传全量 limit
- [x] 2.1 `FilterSheet` 标签改 PickRow + produceState 查 TagDao.active()
- [x] 2.2 删除标签独立 OutlinedTextField，picker 加 "tag" 分支
- [x] 3.1 `RecordFilter` 加 FilterField / clearField()，activeCount 补 tagName
- [x] 3.2 `DetailViewModel` 加 clearFilterField()
- [x] 3.3 `DetailScreen` 删 drillLabel when，改统一 chips 行 + 独立 ✕
- [x] 4.1 纯 JVM 单测覆盖 clearField 联动与 activeCount
- [x] 4.2 版本号 0.2.55、build.yml 挂脚本、活文档 / project.md 登记
- [ ] 4.3 推送 → 双 CI → 出 v0.2.55 包 → 交付说明
