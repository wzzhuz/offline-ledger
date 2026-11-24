package com.zwz.offlineledger.ui.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 第 62 批：全量排查后，账户显示名的组合逻辑只剩 `accountLabel()` 一处实现，
 * DAO 投影（RecordRow / ReviewRow）、Entity 直读（accountBaseLabel）都复用它。
 * 这组单测锁住「旧库 name 已含后缀时不重复追加」这一最容易漂移的分支。
 */
class AccountDisplayLabelTest {

    @Test
    fun `plain name without last4 stays unchanged`() {
        assertEquals("工商银行", accountLabel("工商银行", null))
        assertEquals("工商银行", accountLabel("工商银行", ""))
    }

    @Test
    fun `last4 is appended exactly once`() {
        assertEquals("工商银行 (1122)", accountLabel("工商银行", "1122"))
        assertEquals("工商银行 (3344)", accountLabel("工商银行", "3344"))
    }

    @Test
    fun `legacy name already carrying suffix is not duplicated`() {
        // v0.2.40 及更早把 " (1122)" 拼进了 name 本身，
        // 直接再拼一次就是使用者反馈的「(1122)(1122)」。
        assertEquals(
            "中行储蓄卡 (1122)",
            accountLabel("中行储蓄卡 (1122)", "1122")
        )
    }

    @Test
    fun `different suffix on legacy name is appended`() {
        assertEquals(
            "中行储蓄卡 (1122) (3344)",
            accountLabel("中行储蓄卡 (1122)", "3344")
        )
    }

    @Test
    fun `null name stays null`() {
        assertNull(accountLabel(null, "1122"))
        assertNull(accountLabel(null, null))
    }
}
