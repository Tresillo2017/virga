package com.tomasps.virga.wearsync

import com.tomasps.virga.core.wearsync.WatchCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The phone is woken for the watch capability only if its listener's filter names that exact capability. */
class WatchCapabilityManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun listener(): Element {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val services = doc.getElementsByTagName("service")
        return (0 until services.length).map { services.item(it) as Element }
            .first { it.getAttributeNS(androidNs, "name").endsWith(".wearsync.WatchCapabilityListenerService") }
    }

    @Test
    fun listenerFiltersCapabilityChangesForTheVirgaWatchApp() {
        val service = listener()
        val action = service.getElementsByTagName("action").item(0) as Element
        val data = service.getElementsByTagName("data").item(0) as Element

        assertEquals("true", service.getAttributeNS(androidNs, "exported"))
        assertEquals("com.google.android.gms.wearable.CAPABILITY_CHANGED", action.getAttributeNS(androidNs, "name"))
        assertEquals("wear", data.getAttributeNS(androidNs, "scheme"))
        assertEquals("*", data.getAttributeNS(androidNs, "host"))
        assertEquals("/" + WatchCapability.NAME, data.getAttributeNS(androidNs, "path"))
    }

    @Test
    fun listenerClassExists() {
        val name = listener().getAttributeNS(androidNs, "name")
        assertTrue(Class.forName(name.removePrefix(".").let { if (name.startsWith(".")) "com.tomasps.virga.$it" else it }) != null)
    }
}
