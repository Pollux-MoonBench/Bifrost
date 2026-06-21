package com.moonbench.bifrost.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the plugin-catalogue wire format. Strip-Boy's bifrost-plugins repo and
 * this parser are a cross-repo contract: a silent rename here, or a malformed
 * catalogue there, would just make the store come up empty with no build error.
 */
class PluginCatalogTest {

    /** Mirrors the real catalog.json shipped in the bifrost-plugins repo. */
    private val realCatalog = """
        {
          "schema": "bifrost_plugin_catalog",
          "version": 1,
          "plugins": [
            {
              "id": "fallout4-pipboy",
              "name": "Fallout 4 Pip-Boy",
              "author": "MoonBench / Strip-Boy",
              "version": 1,
              "versionName": "1.0",
              "description": "Sync your stick LEDs to the Pip-Boy.",
              "icon": "DISPLAY",
              "targetPackage": "com.bethsoft.falloutcompanionapp",
              "minBifrostVersionCode": 9,
              "bundleUrl": "https://example.invalid/fallout4-pipboy-1.bfplugin",
              "bundleSha256": "D190DA0116BEB0E2424A91A9E488E96DAF98B584A3E38BBCD43385B8F6DAF0B7"
            }
          ]
        }
    """.trimIndent()

    @Test fun parsesRealCatalogShape() {
        val cat = PluginCatalog.parse(realCatalog)
        assertEquals(1, cat.schemaVersion)
        assertEquals(1, cat.plugins.size)
        val e = cat.plugins[0]
        assertEquals("fallout4-pipboy", e.id)
        assertEquals("Fallout 4 Pip-Boy", e.name)
        assertEquals(1, e.version)
        assertEquals(9, e.minBifrostVersionCode)
        assertEquals("com.bethsoft.falloutcompanionapp", e.targetPackage)
        assertTrue(e.bundleUrl.endsWith(".bfplugin"))
        // sha is normalised to lower-case for the case-insensitive compare.
        assertEquals("d190da0116beb0e2424a91a9e488e96daf98b584a3e38bbcd43385b8f6daf0b7", e.bundleSha256)
    }

    @Test fun rejectsWrongSchema() {
        try {
            PluginCatalog.parse("""{"schema":"something_else","version":1,"plugins":[]}""")
            fail("expected CatalogParseException")
        } catch (e: CatalogParseException) { /* expected */ }
    }

    @Test fun rejectsNewerSchemaVersion() {
        try {
            PluginCatalog.parse("""{"schema":"bifrost_plugin_catalog","version":99,"plugins":[]}""")
            fail("expected CatalogParseException")
        } catch (e: CatalogParseException) { /* expected */ }
    }

    @Test fun rejectsMalformedJson() {
        try {
            PluginCatalog.parse("not json at all {")
            fail("expected CatalogParseException")
        } catch (e: CatalogParseException) { /* expected */ }
    }

    @Test fun skipsEntriesMissingRequiredFields() {
        // First entry has no id, second no bundleUrl, third no version — all
        // dropped; only the complete fourth survives.
        val json = """
            {
              "schema": "bifrost_plugin_catalog",
              "version": 1,
              "plugins": [
                {"name":"no id","version":1,"bundleUrl":"https://x/y.bfplugin"},
                {"id":"no-url","version":1},
                {"id":"no-version","bundleUrl":"https://x/y.bfplugin"},
                {"id":"good","version":2,"bundleUrl":"https://x/good.bfplugin"}
              ]
            }
        """.trimIndent()
        val cat = PluginCatalog.parse(json)
        assertEquals(1, cat.plugins.size)
        assertEquals("good", cat.plugins[0].id)
    }

    @Test fun toleratesEmptyAndMissingPlugins() {
        assertEquals(0, PluginCatalog.parse("""{"schema":"bifrost_plugin_catalog","version":1,"plugins":[]}""").plugins.size)
        assertEquals(0, PluginCatalog.parse("""{"schema":"bifrost_plugin_catalog","version":1}""").plugins.size)
    }

    @Test fun appliesDefaultsForOptionalFields() {
        val cat = PluginCatalog.parse("""
            {"schema":"bifrost_plugin_catalog","version":1,
             "plugins":[{"id":"minimal","version":3,"bundleUrl":"https://x/m.bfplugin"}]}
        """.trimIndent())
        val e = cat.plugins[0]
        assertEquals("minimal", e.name)        // falls back to id
        assertEquals("3", e.versionName)       // falls back to version
        assertEquals("LIGHT", e.icon)          // default icon
        assertEquals(0, e.minBifrostVersionCode)
        assertNull(e.targetPackage)
        assertNull(e.bundleSha256)
    }
}
