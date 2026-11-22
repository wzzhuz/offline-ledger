package com.zwz.offlineledger.data.startup

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 启动快照的失效通知。
 *
 * 触发点在 `LedgerRepository` 的 Room InvalidationTracker observer：
 * 任何写事务提交（含账户 / 分类 / 模板 / 规则 / 预算等元数据写）都会触发，
 * 覆盖全部 42 个写方法，MUST NOT 只挂 `DataRefreshBus` / `bumpRevision` /
 * `refreshTick` 任一上层出口——三个出口覆盖面互不重叠，只挂一个必漏。
 *
 * `register` 让磁盘删除发生在 Application 层，不依赖明细页是否打开：
 * 即使在设置二级页导入 / 恢复，快照文件也会被立刻删掉。
 * 本文件保持纯 JVM，便于单测验证「写后即失效」。
 */
object StartupSnapshotBus {

    private val _invalidations = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val invalidations: SharedFlow<Long> = _invalidations.asSharedFlow()

    @Volatile
    private var onInvalidate: (() -> Unit)? = null

    /** 由 `StartupSnapshotStore.init` 在 Application.onCreate 注册一次。 */
    fun register(action: () -> Unit) {
        onInvalidate = action
    }

    fun invalidate(at: Long = System.currentTimeMillis()) {
        onInvalidate?.invoke()
        _invalidations.tryEmit(at)
    }
}
