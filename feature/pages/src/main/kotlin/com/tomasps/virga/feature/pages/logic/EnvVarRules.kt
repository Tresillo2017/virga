package com.tomasps.virga.feature.pages.logic

import com.tomasps.virga.feature.pages.data.PagesDeploymentConfigValues
import com.tomasps.virga.feature.pages.data.envVarsPatch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class EnvVarType(val wire: String) {
    PLAIN("plain_text"),
    SECRET("secret_text");

    companion object {
        /** Anything that is not a secret is shown as plain text, so an unknown type never hides a value. */
        fun fromWire(raw: String?): EnvVarType = if (raw == SECRET.wire) SECRET else PLAIN
    }
}

/** One variable as the screen shows it. [value] is null for a secret: the server never returns one. */
data class EnvVarEntry(val name: String, val type: EnvVarType, val value: String?) {
    override fun toString(): String = "EnvVarEntry(name=$name, type=$type)"
}

object EnvVarRules {
    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun entriesOf(config: PagesDeploymentConfigValues?): List<EnvVarEntry> =
        config?.envVars.orEmpty().map { (name, variable) ->
            val type = EnvVarType.fromWire(variable?.type)
            EnvVarEntry(
                name = name,
                type = type,
                value = if (type == EnvVarType.SECRET) null else variable?.value,
            )
        }.sortedBy { it.name }

    fun isValidName(name: String): Boolean = NAME.matches(name)

    /**
     * True when a secret would be saved with no value: a new secret, or a plain variable turned into one. An existing
     * secret that stays a secret is the only case where a blank value means "keep the stored value".
     */
    fun needsSecretValue(type: EnvVarType, value: String, existing: EnvVarEntry?): Boolean =
        type == EnvVarType.SECRET && value.isEmpty() && existing?.type != EnvVarType.SECRET

    /**
     * The PATCH that saves one variable, or null when there is nothing to send: an existing secret saved with no new
     * value keeps its stored value, and the value is never sent back.
     */
    fun saveBody(
        environment: String,
        name: String,
        type: EnvVarType,
        value: String,
        existing: EnvVarEntry?,
    ): JsonObject? {
        if (type == EnvVarType.SECRET && value.isEmpty() && existing?.type == EnvVarType.SECRET) return null
        val variable = buildJsonObject {
            put("type", type.wire)
            put("value", value)
        }
        return envVarsPatch(environment, mapOf(name to variable as JsonElement))
    }

    /** The PATCH that removes one variable: its key is set to JSON null. */
    fun deleteBody(environment: String, name: String): JsonObject =
        envVarsPatch(environment, mapOf(name to JsonNull))
}
