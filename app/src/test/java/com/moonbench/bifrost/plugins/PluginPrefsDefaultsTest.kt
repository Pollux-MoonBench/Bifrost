package com.moonbench.bifrost.plugins

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the baked-in default catalogue URL. The store came up empty once because
 * this pointed at a repo that didn't exist; this guards against silently
 * regressing it to a dead/wrong host again.
 */
class PluginPrefsDefaultsTest {

    @Test fun defaultCatalogUrlPointsAtUpstreamServingBranch() {
        val url = PluginPrefs.DEFAULT_CATALOG_URL
        assertTrue("must be https", url.startsWith("https://"))
        assertTrue("must be a raw GitHub URL", url.startsWith("https://raw.githubusercontent.com/"))
        assertTrue("must be the Pollux upstream repo", url.contains("/Pollux-MoonBench/Bifrost/"))
        assertTrue("must be served from the plugin-catalog branch", url.contains("/plugin-catalog/"))
        assertTrue("must resolve to the catalogue document", url.endsWith("/catalog.json"))
    }
}
