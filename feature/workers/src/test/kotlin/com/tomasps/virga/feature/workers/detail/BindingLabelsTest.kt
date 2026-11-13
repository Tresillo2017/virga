package com.tomasps.virga.feature.workers.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BindingLabelsTest {

    @Test
    fun `a known binding type maps to a friendly label resource`() {
        // Arrange
        val type = "kv_namespace"

        // Act
        val label = bindingTypeLabelRes(type)

        // Assert
        assertNotEquals(null, label)
    }

    @Test
    fun `the r2, d1 and kv types map to distinct labels`() {
        // Arrange
        val types = listOf("r2_bucket", "d1", "kv_namespace")

        // Act
        val labels = types.map { bindingTypeLabelRes(it) }

        // Assert
        assertEquals(3, labels.toSet().size)
    }

    @Test
    fun `an unknown binding type has no label so the raw id is shown`() {
        // Arrange
        val type = "future_binding_kind"

        // Act
        val label = bindingTypeLabelRes(type)

        // Assert
        assertNull(label)
    }
}
