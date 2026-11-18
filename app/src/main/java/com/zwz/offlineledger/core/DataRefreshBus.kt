package com.zwz.offlineledger.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 第 23 批：跨页面「账本数据已变」的进程内事件。
 *
 * 导入 / 恢复 / 删除流水等写操作发生在设置类二级页，明细 / 资产 / 统计的
 * ViewModel 各自独立，原先只能靠「切月份换参数」才重查。
 * 这里只发一个轻量事件，订阅方自行 reload，MUST NOT 把账本数据塞进事件。
 */
object DataRefreshBus {
    private val _events = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val events: SharedFlow<Long> = _events.asSharedFlow()

    fun notifyChanged(at: Long = System.currentTimeMillis()) {
        _events.tryEmit(at)
    }
}
