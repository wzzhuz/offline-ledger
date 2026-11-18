package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.CounterpartyNormalizer
import com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterpartyNormalizerTest {

    private val aliases = listOf(
        CounterpartyAliasEntity(1, "盒马网络科技", "盒马"),
        CounterpartyAliasEntity(2, "南京肯德基", "KFC")
    )

    @Test fun `full merchant name becomes short name`() {
        assertEquals("盒马",
            CounterpartyNormalizer.normalize("南京盒马网络科技有限公司", aliases))
    }

    @Test fun `unknown merchant stays untouched`() {
        assertEquals("楼下煎饼摊",
            CounterpartyNormalizer.normalize("楼下煎饼摊", aliases))
    }

    @Test fun `blank stays blank`() {
        assertEquals("", CounterpartyNormalizer.normalize("", aliases))
    }
}
