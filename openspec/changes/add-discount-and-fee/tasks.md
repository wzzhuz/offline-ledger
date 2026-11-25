# 任务：add-discount-and-fee（v0.2.47）

## 1. 四件套与对齐
- [x] 1.1 核查远端 HEAD / RecordEntity / DAO SUM / 余额双实现 / CSV / 备份 DTO
- [x] 1.2 查证竞品官方手册与商店描述，明确用途与字段语义
- [x] 1.3 五项对齐 + 9 条验收 + 分级判定（需新建 change）
- [x] 1.4 使用者 A 确认后推四件套 + 全部实现，逐 blob 双校验
      （见活文档 §7.49）

## 2. 数据层（version 7）
- [x] 2.1 `RecordEntity` 加 `discountCents` / `feeCents` 可空列
- [x] 2.2 `TrashEntryEntity` 同步两列
- [x] 2.3 `AppDatabase.version = 7`；`Migrations.ALL` 保持空、`MigrationContractTest` 零改动
- [x] 2.4 `SeedData` 确认无 records 插入，列清单无需改
- [x] 2.5 `check_transaction_annotations.py` 预期值同步

## 3. 余额与规则
- [x] 3.1 `RecordDao.aggregateAccountDeltas` 的 TRANSFER from 侧加 fee
- [x] 3.2 `LedgerEngine.deltasFrom` 同步（纯 JVM）
- [x] 3.3 纯 JVM 单测：DAO SQL 常量与引擎结果一致、负 fee = 转账优惠
- [x] 3.4 校验器：`0 < discount <= amount`、`fee` 互斥与范围

## 4. UI
- [x] 4.1 记一笔「优惠 / 手续费」折叠入口 + 三行金额
- [x] 4.2 明细行划线原价 + 蓝色优惠 / 灰色手续费标签
- [x] 4.3 详情页三行金额可编辑（原价由推导回填）
- [x] 4.4 不碰 `detail-filter-and-transfer-edit` 的账户 id 回填逻辑

## 5. 往返
- [x] 5.1 `CsvRow` / `CsvCodec` 加两列，`duplicateKey()` 不变
- [x] 5.2 `BackupRecord` 补两字段；核查确认 Exporter / Importer /
      SnapshotCodec 全经 `LedgerMappers.toDto/toEntity`，**三文件零改动**
      即完成往返（见 design 11.2）
- [x] 5.3 回收站删除→恢复带两字段
- [x] 5.4 旧备份 / 老 8~13 列 CSV 兼容单测

## 6. 出包
- [x] 6.1 六静态检查 + push CI 全绿
- [x] 6.2 触发与轮询拆成独立调用，出包 CI `45526043408` success /
      tag `v0.2.47` / SHA256 `d307f0937c39136bf47406ea48607d5b0a79134c586c1dfa08bdae68c5b7fe9e`
- [x] 6.3 实测回填（活文档 §7.49 与 project.md），含首轮 CI 失败与
      临时诊断如实登记；正式归档校验仍保持 `[ ]`

## 7. 真机待验（全部 `[ ]`）
- [ ] 7.1 三行金额键盘不遮挡 / 非法优惠拦截
- [ ] 7.2 转账手续费后两账户余额、负 fee 文案
- [ ] 7.3 还款优惠冲减后欠款归零
- [ ] 7.4 明细 / 详情 / CSV / 旧备份 / 回收站往返
- [ ] 7.5 卸载重装后数据；§8 第 45–65 批既有项整体继承
