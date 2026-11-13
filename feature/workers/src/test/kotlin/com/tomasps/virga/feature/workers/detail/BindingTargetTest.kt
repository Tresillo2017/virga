package com.tomasps.virga.feature.workers.detail

import com.tomasps.virga.feature.workers.data.BindingTarget
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class BindingTargetTest {

    @Test
    fun `a D1 binding links by database_id when the deprecated id field also exists`() {
        val binding: JsonObject = buildJsonObject {
            put("type", "d1")
            put("name", "DB")
            put("database_id", "db-current")
            put("id", "db-deprecated")
        }

        val target = bindingsFrom(listOf(binding)).single().target

        assertEquals(BindingTarget.D1("db-current"), target)
    }
}
