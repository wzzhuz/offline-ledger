package com.zwz.offlineledger.core.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 87 批：六笔 HyperOS / 支付宝实测原文 + 规则覆盖层的纯 JVM 契约。 */
class TextIntakeInspectorTest {

    private val qinggu = """
南京青谷酒店管理有限公司
-12.00
交易成功
支付时间
2026-11-26 17:45:47
付款方式
交通银行信用卡(2203)
商品说明
南京-华为软件基地-N7-1F-青谷-Z06SJ1-餐线-中卡6号02
支付奖励
已领取6积分
收单机构
北京海科融通支付服务有限公司
清算机构
中国银联股份有限公司
收款方全称
南京青谷酒店管理有限公司
订单号
2026112623001455461446630585
商家订单号
商家可扫码退款或查询交易
7895279692244253
7895 ****** 点击查看订单号
账单管理
已解锁"家居数码"贴纸，去兑换好礼
账单分类
餐饮美食
标签
请选择
使用记账本，查看自定义分类、标签统计
计入收支
备注
添加
AA收款
查看往来记录
对此订单有疑问
""".trimIndent()

    private val youfude = """
南京优芙得餐饮管理有限公司
-15.60
交易成功
订单金额
15.90
实体店通用红包
-0.30
支付时间
2026-11-24 12:13:59
付款方式
交通银行信用卡(2203)
商品说明
南京-华为软件基地-N7-1F-优芙得-Z00YC9-餐线-1号餐线
支付奖励
已领取6积分
收单机构
拉卡拉支付股份有限公司
清算机构
中国银联股份有限公司
收款方全称
南京优芙得餐饮管理有限公司
订单号
2026112423001455461424767037
商家订单号
商家可扫码退款或查询交易
7895086616914056
7895 ****** 点击查看订单号
账单管理
已解锁"吃顿饭"贴纸，去兑换好礼
账单分类
餐饮美食
标签
请选择
为您推荐
三餐
买软件
下馆子
计入收支
备注
添加
AA收款
查看往来记录
对此订单有疑问
""".trimIndent()

    private val wuting = """
吴婷
-2.50
自动扣款成功
管理自动扣款
自动扣款 免密支付
支付时间
2026-11-22 16:37:57
付款方式
交通银行信用卡(2203)
支付奖励
已领取2积分
交易详情
季华老鹅 店内购物
支付场景：其他综合零售
订单号
2026112204200133001030892451
账单管理
我的消费图鉴
账单分类
亲友代付
标签
请选择
为您推荐
代付
计入收支
备注
添加
申请电子回单
对此订单有疑问
""".trimIndent()

    private val kexiang = """
科巷冰糖藕
-6.00
交易成功
支付时间
2026-11-22 16:15:31
付款方式
交通银行信用卡(2203)
商品说明
科巷冰糖藕【买单】消费6.00元
支付奖励
已领取2积分
收单机构
拉卡拉支付股份有限公司
清算机构
中国银联股份有限公司
服务详情
省团生活
进入小程序
订单号
2026112223001455461418326459
商家订单号
商家可扫码退款或查询交易
20261122110113230266233061374696
2026 ****** 点击查看订单号
账单管理
我的消费图鉴
账单分类
餐饮美食
标签
请选择
使用记账本，查看自定义分类、标签统计
计入收支
备注
添加
联系小程序客服
查看往来记录
对此订单有疑问
""".trimIndent()

    private val fubao = """
南京福宝潮玩
-20.00
交易成功
支付时间
2026-11-22 15:58:37
付款方式
中国银行储蓄卡(7375)
查看扣款顺序
商品说明
直接付款
支付奖励
已领取9积分
收单机构
拉卡拉支付股份有限公司
清算机构
中国银联股份有限公司
订单号
2026112223001455461417056740
商家订单号
商家可扫码退款或查询交易
20261122110113230166233061280918
2026 ****** 点击查看订单号
账单管理
已解锁"休闲娱乐"贴纸，去兑换好礼
账单分类
文化休闲
标签
请选择
使用记账本，查看自定义分类、标签统计
计入收支
备注
添加
AA收款
查看往来记录
对此订单有疑问
""".trimIndent()

    private val pilusi = """
logo
南京毗卢寺
-39.71
交易成功
订单金额
40.00
中国银行立减金
-0.29
arrow
支付时间
2026-11-22 14:11:51
付款方式
中国银行储蓄卡(7375)
arrow
商品说明
南京毗卢寺香烛店-消费
查看购物详情arrow
支付奖励
已领取4积分
arrow
收单机构
中国建设银行股份有限公司江苏省分行
清算机构
网联清算有限公司
订单号
2026112223001455461413261230
商家订单号
商家可扫码退款或查询交易
0920261122000584220011
0920 ****** 点击查看订单号
账单管理
我的消费图鉴
贴纸
账单分类
公共服务
标签
请选择
使用记账本，查看自定义分类、标签统计
计入收支
备注
添加
extend-icon
查看往来记录
extend-icon
去评价
extend-icon
对此订单有疑问
""".trimIndent()

    @Test
    fun `all six alipay samples are strict ready`() {
        val samples = listOf(
            "青谷" to qinggu,
            "优芙得" to youfude,
            "吴婷" to wuting,
            "科巷" to kexiang,
            "福宝" to fubao,
            "毗卢寺" to pilusi,
        )
        samples.forEach { (name, text) ->
            val p = TextIntakeInspector.from("send", text)
            assertTrue("$name missing=" + p.missing, p.readyForAutoCommit)
            assertNotNull("$name draft", p.commitDraft)
            assertTrue("$name sourceId", p.commitDraft!!.sourceId.length >= 20)
            assertEquals("$name 来源", "alipay", p.commitDraft.importSource)
            assertTrue("$name page markers", p.commitDraft.pageMarkers >= 2)
        }
    }

    @Test
    fun `main amount is chosen by anchor not candidate uniqueness`() {
        val p = TextIntakeInspector.from("send", kexiang)
        assertTrue(p.amountCandidates.contains("-6.00"))
        assertTrue(p.amountCandidates.contains("6.00"))
        assertTrue(p.amountCandidates.size >= 2)
        assertEquals(600L, p.commitDraft!!.amountCents)
        assertEquals("6.00", p.commitDraft.amountYuan)
    }

    @Test
    fun `discount arithmetic maps to discountCents`() {
        val a = TextIntakeInspector.from("send", youfude).commitDraft!!
        assertEquals(1560L, a.amountCents)
        assertEquals(30L, a.discountCents)
        assertEquals("15.90", a.originalYuan)
        val b = TextIntakeInspector.from("send", pilusi).commitDraft!!
        assertEquals(3971L, b.amountCents)
        assertEquals(29L, b.discountCents)
        assertEquals("40.00", b.originalYuan)
    }

    @Test
    fun `account strips card kind and keeps last four`() {
        val a = TextIntakeInspector.from("send", qinggu).commitDraft!!
        assertEquals("交通银行", a.accountName)
        assertEquals("2203", a.cardLast4)
        val b = TextIntakeInspector.from("send", fubao).commitDraft!!
        assertEquals("中国银行", b.accountName)
        assertEquals("7375", b.cardLast4)
    }

    @Test
    fun `category only presets one mapping and otherwise stays empty`() {
        assertEquals("餐饮", TextIntakeInspector.from("send", qinggu).commitDraft!!.categoryRoot)
        assertEquals(null, TextIntakeInspector.from("send", fubao).commitDraft!!.categoryRoot)
        assertTrue(TextIntakeInspector.from("send", fubao).warnings.any { it.contains("未映射") })
    }

    @Test
    fun `counterparty handles auto debit detail block`() {
        val p = TextIntakeInspector.from("send", wuting)
        assertEquals("自动扣款成功", p.commitDraft!!.statusWord)
        assertEquals("季华老鹅 店内购物", p.commitDraft.counterparty)
    }

    @Test
    fun `icon placeholders do not become merchant`() {
        val p = TextIntakeInspector.from("send", pilusi)
        assertEquals("南京毗卢寺", p.commitDraft!!.counterparty)
    }

    @Test
    fun `unknown page degrades to draft`() {
        val p = TextIntakeInspector.from("send", "微信支付成功\n-8.00\n2026-11-27 10:00:00")
        assertFalse(p.readyForAutoCommit)
        assertTrue(p.missing.any { it.contains("页面特征不足") })
    }

    @Test
    fun `rule patch only adds words and maps`() {
        val patch = """
状态词 += 付款完成, 扣款完成
优惠词 += 立减金
噪声 += 查看购物详情
分类 文化休闲 = 学习娱乐
这行故意写错
""".trimIndent()
        val result = TextIntakeRules.withPatch(patch)
        assertTrue(result.rules.statusWords.contains("付款完成"))
        assertTrue(result.rules.discountWords.contains("立减金"))
        assertTrue(result.rules.noiseLabels.contains("查看购物详情"))
        assertEquals("学习娱乐", result.rules.categoryMap["文化休闲"])
        assertEquals(1, result.invalidLines.size)
    }

    @Test
    fun `yuan parser is strict two decimals and integer cents`() {
        assertEquals(1560L, TextIntakeInspector.yuanToCentsAbs("15.60"))
        assertEquals(30L, TextIntakeInspector.yuanToCentsAbs("-0.30"))
        assertEquals(null, TextIntakeInspector.yuanToCentsAbs("15.6"))
        assertEquals(null, TextIntakeInspector.yuanToCentsAbs("15.60元"))
    }

    @Test
    fun `empty or null input is a safe no-op`() {
        val a = TextIntakeInspector.from("empty", "")
        val b = TextIntakeInspector.from("debug", null)
        assertFalse(a.readyForAutoCommit)
        assertFalse(b.readyForAutoCommit)
        assertTrue(a.missing.isNotEmpty())
    }
}
