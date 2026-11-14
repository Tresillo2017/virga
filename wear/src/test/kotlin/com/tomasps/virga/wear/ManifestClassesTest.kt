package com.tomasps.virga.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The module namespace is com.tomasps.virga, not com.tomasps.virga.wear, so a relative android:name would resolve
 * to a class that does not exist. Every component must therefore be fully qualified and loadable.
 */
class ManifestClassesTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val componentTags = listOf("application", "activity", "service", "receiver", "provider")

    private fun declaredClassNames(): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        return componentTags.flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).mapNotNull { i ->
                (nodes.item(i) as Element).getAttributeNS(androidNs, "name").takeIf { it.isNotEmpty() }
            }
        }
    }

    @Test
    fun everyManifestComponentIsFullyQualifiedAndLoads() {
        val names = declaredClassNames()
        assertTrue("manifest declares no components", names.size >= 5)
        names.forEach { name ->
            assertFalse("$name must be fully qualified", name.startsWith("."))
            Class.forName(name)
        }
    }

    @Test
    fun surfaceServicesAreDeclaredWithTheirBindingPermissions() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val services = doc.getElementsByTagName("service")
        val byName = (0 until services.length).map { services.item(it) as Element }
            .associateBy { it.getAttributeNS(androidNs, "name") }

        val tile = byName.getValue("com.tomasps.virga.wear.surfaces.BuildTileService")
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.getAttributeNS(androidNs, "permission"))
        val complication = byName.getValue("com.tomasps.virga.wear.surfaces.BuildComplicationService")
        assertEquals(
            "com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER",
            complication.getAttributeNS(androidNs, "permission"),
        )
        val meta = complication.getElementsByTagName("meta-data")
        val values = (0 until meta.length).map { meta.item(it) as Element }
            .associate { it.getAttributeNS(androidNs, "name").substringAfterLast('.') to it.getAttributeNS(androidNs, "value") }
        assertEquals("SHORT_TEXT,LONG_TEXT", values["SUPPORTED_TYPES"])
        assertEquals("900", values["UPDATE_PERIOD_SECONDS"])
    }

    @Test
    fun tileFreshnessIsFifteenMinutes() {
        assertEquals(15L * 60 * 1000, com.tomasps.virga.wear.surfaces.TILE_FRESHNESS_MILLIS)
    }
}
