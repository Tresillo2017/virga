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

    private fun service(name: String): Element {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val services = doc.getElementsByTagName("service")
        return (0 until services.length).map { services.item(it) as Element }
            .first { it.getAttributeNS(androidNs, "name") == name }
    }

    private fun Element.actions(): List<String> {
        val nodes = getElementsByTagName("action")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
    }

    private fun Element.metaData(): Map<String, String> {
        val nodes = getElementsByTagName("meta-data")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttributeNS(androidNs, "name") to it.getAttributeNS(androidNs, "value") }
    }

    @Test
    fun tileServiceIsExportedWithItsPermissionAndAction() {
        val tile = service("com.tomasps.virga.wear.surfaces.BuildTileService")

        assertEquals("true", tile.getAttributeNS(androidNs, "exported"))
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.getAttributeNS(androidNs, "permission"))
        assertEquals(listOf("androidx.wear.tiles.action.BIND_TILE_PROVIDER"), tile.actions())
    }

    @Test
    fun summaryTileServiceIsExportedWithItsPermissionAndAction() {
        val name = "com.tomasps.virga.wear.summary.SummaryTileService"
        val tile = service(name)

        assertTrue(androidx.wear.tiles.TileService::class.java.isAssignableFrom(Class.forName(name)))
        assertEquals("true", tile.getAttributeNS(androidNs, "exported"))
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.getAttributeNS(androidNs, "permission"))
        assertEquals(listOf("androidx.wear.tiles.action.BIND_TILE_PROVIDER"), tile.actions())
        assertEquals("@string/summary_tile_label", tile.getAttributeNS(androidNs, "label"))
    }

    @Test
    fun complicationServiceIsExportedWithItsPermissionActionAndMetaData() {
        val complication = service("com.tomasps.virga.wear.surfaces.BuildComplicationService")

        assertEquals("true", complication.getAttributeNS(androidNs, "exported"))
        assertEquals(
            "com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER",
            complication.getAttributeNS(androidNs, "permission"),
        )
        assertEquals(
            listOf("android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"),
            complication.actions(),
        )
        val meta = complication.metaData()
        assertEquals("SHORT_TEXT,LONG_TEXT", meta["android.support.wearable.complications.SUPPORTED_TYPES"])
        assertEquals("900", meta["android.support.wearable.complications.UPDATE_PERIOD_SECONDS"])
    }

    /** The listener only hears the phone's item if its filter names exactly the path the phone writes. */
    @Test
    fun handoffListenerFiltersTheCredentialPath() {
        val listener = service("com.tomasps.virga.wear.session.HandoffListenerService")
        val data = listener.getElementsByTagName("data").item(0) as Element

        assertEquals(listOf("com.google.android.gms.wearable.DATA_CHANGED"), listener.actions())
        assertEquals("wear", data.getAttributeNS(androidNs, "scheme"))
        assertEquals("*", data.getAttributeNS(androidNs, "host"))
        assertEquals(com.tomasps.virga.core.wearsync.HandoffPaths.CREDENTIAL, data.getAttributeNS(androidNs, "path"))
    }

    @Test
    fun tileFreshnessIsFifteenMinutes() {
        assertEquals(15L * 60 * 1000, com.tomasps.virga.wear.surfaces.TILE_FRESHNESS_MILLIS)
    }

    private fun manifest() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun declaredPermissions(): Set<String> {
        val nodes = manifest().getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }.toSet()
    }

    @Test
    fun watchServiceIsAPrivateDataSyncForegroundService() {
        val name = "com.tomasps.virga.wear.alerts.WearWatchService"
        val service = service(name)

        assertTrue(android.app.Service::class.java.isAssignableFrom(Class.forName(name)))
        assertEquals("dataSync", service.getAttributeNS(androidNs, "foregroundServiceType"))
        assertEquals("false", service.getAttributeNS(androidNs, "exported"))
    }

    @Test
    fun alertPermissionsAreDeclared() {
        val declared = declaredPermissions()

        assertTrue(declared.containsAll(listOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.POST_NOTIFICATIONS",
        )))
    }
}
