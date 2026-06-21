package com.moonbench.bifrost.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update-detection rule: a plugin is "out of date" iff it is installed AND
 * the catalogue version strictly exceeds the installed version. Guards against
 * off-by-one regressions (equal = no update; downgrade = no update) and against
 * surfacing updates for plugins that were never installed.
 */
class PluginUpdateTest {

    private fun entry(id: String, version: Int) = CatalogEntry(
        id = id, name = id, author = "", version = version, versionName = "$version",
        description = "", icon = "LIGHT", targetPackage = null, minBifrostVersionCode = 0,
        bundleUrl = "https://x/$id-$version.bfplugin", bundleSha256 = null
    )

    private fun catalog(vararg entries: CatalogEntry) =
        PluginCatalog(schemaVersion = 1, plugins = entries.toList())

    @Test fun installedOlderYieldsUpdate() {
        val updates = PluginRepository.updatesFor(catalog(entry("a", 2)), mapOf("a" to 1))
        assertEquals(1, updates.size)
        assertEquals("a", updates[0].entry.id)
        assertEquals(1, updates[0].installedVersion)
    }

    @Test fun installedSameYieldsNoUpdate() {
        assertTrue(PluginRepository.updatesFor(catalog(entry("a", 2)), mapOf("a" to 2)).isEmpty())
    }

    @Test fun installedNewerYieldsNoUpdate() {
        assertTrue(PluginRepository.updatesFor(catalog(entry("a", 2)), mapOf("a" to 5)).isEmpty())
    }

    @Test fun notInstalledYieldsNoUpdate() {
        assertTrue(PluginRepository.updatesFor(catalog(entry("a", 9)), emptyMap()).isEmpty())
    }

    @Test fun mixedFleetReportsOnlyOutdatedInstalled() {
        val cat = catalog(entry("a", 2), entry("b", 1), entry("c", 4))
        // a outdated, b current, c not installed.
        val updates = PluginRepository.updatesFor(cat, mapOf("a" to 1, "b" to 1))
        assertEquals(listOf("a"), updates.map { it.entry.id })
    }
}
