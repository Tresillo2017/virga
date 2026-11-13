package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.feature.workers.data.BindingTarget
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeploymentSplitTest {

    @Test
    fun `a split is valid only when the shares total exactly 100`() {
        assertTrue(DeploymentSplit.isValid(listOf(60.0, 40.0)))
        assertFalse(DeploymentSplit.isValid(listOf(60.0, 30.0)))
        assertFalse(DeploymentSplit.isValid(listOf(60.0, 50.0)))
    }

    @Test
    fun `a share of zero or more than 100 is never valid`() {
        assertFalse(DeploymentSplit.isValid(listOf(100.0, 0.0)))
        assertFalse(DeploymentSplit.isValid(listOf(101.0, -1.0)))
        assertFalse(DeploymentSplit.isValid(emptyList()))
    }

    @Test
    fun `a rollback is one version at 100 percent`() {
        assertEquals(listOf("v-old" to 100.0), DeploymentSplit.rollbackTo("v-old"))
    }
}

class BindingMappingTest {

    @Test
    fun `r2 d1 and kv bindings point at the resource they name`() {
        val raw = listOf(
            JsonObject(mapOf("type" to JsonPrimitive("r2_bucket"), "name" to JsonPrimitive("ASSETS"), "bucket_name" to JsonPrimitive("demo-assets"))),
            JsonObject(mapOf("type" to JsonPrimitive("d1"), "name" to JsonPrimitive("ORDERS"), "id" to JsonPrimitive("db-1"))),
            JsonObject(mapOf("type" to JsonPrimitive("kv_namespace"), "name" to JsonPrimitive("CACHE"), "namespace_id" to JsonPrimitive("ns-1"))),
        )

        val bindings = bindingsFrom(raw)

        assertEquals(BindingTarget.R2("demo-assets"), bindings[0].target)
        assertEquals(BindingTarget.D1("db-1"), bindings[1].target)
        assertEquals(BindingTarget.Kv("ns-1"), bindings[2].target)
    }

    @Test
    fun `unknown binding types are kept without a link and entries without a type are dropped`() {
        val raw = listOf(
            JsonObject(mapOf("type" to JsonPrimitive("queue"), "name" to JsonPrimitive("JOBS"))),
            JsonObject(mapOf("name" to JsonPrimitive("NO_TYPE"))),
        )

        val bindings = bindingsFrom(raw)

        assertEquals(1, bindings.size)
        assertNull(bindings.single().target)
    }

    @Test
    fun `plain text values are carried only for plain text bindings`() {
        val raw = listOf(
            JsonObject(mapOf("type" to JsonPrimitive("plain_text"), "name" to JsonPrimitive("LOG_LEVEL"), "text" to JsonPrimitive("info"))),
            JsonObject(mapOf("type" to JsonPrimitive("secret_text"), "name" to JsonPrimitive("TOKEN"))),
        )

        val bindings = bindingsFrom(raw)

        assertEquals("info", bindings[0].text)
        assertNull(bindings[1].text)
    }
}
