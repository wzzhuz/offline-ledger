package com.zwz.offlineledger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * 第 20 批：reorderable 3.1.0 与业务层之间的唯一适配点。
 *
 * 库只负责「拖动中实时给出 from/to 的 LazyListItemInfo」，不负责持久化、
 * 回滚、分组语义，也不负责松手时机。这里把 key 映射回业务 id：
 *
 * - 拖动中：onMove 每次只改内存 ids（库要求 onMove 返回前列表已更新，
 *   否则会等 layoutInfo 两帧直到超时）；
 * - 松手：draggableHandle(onDragStopped) 调 commit()，一次性写 sortOrder；
 * - 落库后 ViewModel reload 会推来新 ids，sync() 只在未脏时同步，避免把
 *   拖动中的临时顺序覆盖掉。
 *
 * 与第 15-17 批自研 ReorderDragState 的区别：手势检测、pointer pass、
 * 长按窗口、滚动协商、边缘按距变速滚动、animateItem 让位补间全部交由库，
 * 本类不再写任何 pointerInput / scrollable 代码。
 */
@Stable
class KeyedReorderState internal constructor(
    initialIds: List<Long>,
    private val prefix: String,
    private val onCommit: (List<Long>) -> Unit
) {
    var ids by mutableStateOf(initialIds)
        private set
    private var dirty = false

    fun keyOf(id: Long): String = prefix + "-" + id

    fun idOf(key: Any?): Long? =
        (key as? String)?.removePrefix(prefix + "-")?.toLongOrNull()

    /** 库 onMove 回调：按 key 交换内存顺序；跨组/无效 key 交由调用方过滤。 */
    fun move(fromKey: Any?, toKey: Any?) {
        val f = idOf(fromKey) ?: return
        val t = idOf(toKey) ?: return
        val fi = ids.indexOf(f)
        val ti = ids.indexOf(t)
        if (fi < 0 || ti < 0 || fi == ti) return
        ids = ids.toMutableList().also { it.add(ti, it.removeAt(fi)) }
        dirty = true
    }

    /** 松手一次性落库。 */
    fun commit() {
        if (!dirty) return
        dirty = false
        onCommit(ids)
    }

    /** ViewModel 推来新顺序时同步；拖动中（dirty）不覆盖。 */
    fun sync(newIds: List<Long>) {
        if (!dirty && ids != newIds) ids = newIds
    }
}

@Composable
fun rememberKeyedReorder(
    ids: List<Long>,
    prefix: String,
    onCommit: (List<Long>) -> Unit
): KeyedReorderState {
    val currentCommit = rememberUpdatedState(onCommit)
    val state = remember(prefix) {
        KeyedReorderState(ids, prefix) { currentCommit.value(it) }
    }
    LaunchedEffect(ids) { state.sync(ids) }
    return state
}
