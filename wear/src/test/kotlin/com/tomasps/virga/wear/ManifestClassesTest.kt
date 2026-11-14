package com.tomasps.virga.wear

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
        assertTrue("manifest declares no components", names.size >= 3)
        names.forEach { name ->
            assertFalse("$name must be fully qualified", name.startsWith("."))
            Class.forName(name)
        }
    }
}
