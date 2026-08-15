package com.moonbench.bifrost.services

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TileManifestTest {

    private val manifest: String by lazy { locate("src/main/AndroidManifest.xml").readText() }

    private val tileBlock: String by lazy {
        val start = manifest.indexOf("BifrostTileService")
        assertTrue("BifrostTileService is not declared in AndroidManifest.xml", start >= 0)
        val end = manifest.indexOf("</service>", start)
        assertTrue("BifrostTileService declaration is not closed", end >= 0)
        manifest.substring(start, end)
    }

    @Test fun tileIsExported() {
        assertTrue("the tile service must be exported", tileBlock.contains("android:exported=\"true\""))
    }

    @Test fun tileIsGuardedByTheBindPermission() {
        assertTrue(
            "the tile must require BIND_QUICK_SETTINGS_TILE so only the system can bind it",
            tileBlock.contains("android.permission.BIND_QUICK_SETTINGS_TILE")
        )
    }

    @Test fun tileDeclaresTheQuickSettingsAction() {
        assertTrue(
            "without the QS_TILE action the tile never appears in the panel",
            tileBlock.contains("android.service.quicksettings.action.QS_TILE")
        )
    }

    @Test fun tileHasAnIconAndALabel() {
        assertTrue("the tile needs an icon to render", tileBlock.contains("android:icon="))
        assertTrue("the tile needs a label to be found in the picker", tileBlock.contains("android:label="))
    }

    private fun locate(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            dir?.let {
                File(it, rel).takeIf(File::exists)?.let { f -> return f }
                File(it, "app/$rel").takeIf(File::exists)?.let { f -> return f }
            }
            dir = dir?.parentFile
        }
        error("could not locate '$rel' from ${System.getProperty("user.dir")}")
    }
}
