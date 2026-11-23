package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.local.SeedData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDeletionPolicyTest {

    @Test
    fun `preset category is never physically deleted`() {
        assertTrue(CategoryDeletionPolicy.shouldArchive(0, true))
        val id = SeedData.categories.first { it.level == 1 && !it.enabled }.id
        assertTrue(SeedData.isPresetCategoryId(id))
        assertTrue(CategoryDeletionPolicy.shouldArchive(0, SeedData.isPresetCategoryId(id)))
    }

    @Test
    fun `linked category is archived`() {
        assertTrue(CategoryDeletionPolicy.shouldArchive(3, false))
    }

    @Test
    fun `only manual unused category can be deleted`() {
        assertFalse(CategoryDeletionPolicy.shouldArchive(0, false))
        assertFalse(SeedData.isPresetCategoryId(999999L))
    }
}
