package com.tomasps.virga.wear

import com.tomasps.virga.core.wearsync.WatchCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The phone gates the token on this capability, so the watch must advertise exactly the shared name. */
class WatchCapabilityResourceTest {

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(path))

    @Test
    fun watchAdvertisesTheSharedCapabilityName() {
        val arrays = parse("src/main/res/values/wear.xml").getElementsByTagName("string-array")
        val capabilities = (0 until arrays.length).map { arrays.item(it) as Element }
            .single { it.getAttribute("name") == "android_wear_capabilities" }
        val items = capabilities.getElementsByTagName("item")

        assertEquals(listOf(WatchCapability.NAME), (0 until items.length).map { items.item(it).textContent.trim() })
    }

    @Test
    fun resourceShrinkingKeepsTheCapabilityArray() {
        val root = parse("src/main/res/raw/keep.xml").documentElement
        val keep = root.getAttributeNS("http://schemas.android.com/tools", "keep")

        assertTrue(keep.split(',').map(String::trim).contains("@array/android_wear_capabilities"))
    }
}
