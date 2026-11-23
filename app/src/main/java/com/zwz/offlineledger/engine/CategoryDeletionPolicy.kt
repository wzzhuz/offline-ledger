package com.zwz.offlineledger.engine

/**
 * 第 51 批：分类删除策略，纯 JVM 可测。
 *
 * 有流水的分类 MUST NOT 物理删（历史 rootCategoryId 会悬空）；
 * 预制分类即使无流水也 MUST NOT 物理删——SeedCallback.onCreate 只在全新库跑一次，
 * 删掉后没有「恢复默认」入口，restoreCategory 对已删行影响 0 行，等于不可逆。
 * 两类情形统一改走归档，可在分类管理「已归档」区恢复。
 *
 * AUTOINCREMENT 永不重用 rowid，物理删除留下的 ID 空洞 MUST NOT 回填：
 * 回填会让旧备份 / 残留引用里的旧 ID 被新分类顶上，造成静默关联错乱。
 */
object CategoryDeletionPolicy {
    fun shouldArchive(linkedRecordCount: Int, isPreset: Boolean): Boolean =
        linkedRecordCount > 0 || isPreset
}
