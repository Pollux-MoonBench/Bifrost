package com.moonbench.bifrost

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the manifest wipe shipped in 1.3.0.
 *
 * A collaborator branch built on an older manifest was merged, and the merge
 * silently dropped three declarations. Nothing failed to compile, no test
 * failed, and the app started fine — but the accessibility service was absent
 * from the system's accessibility list, so the setup flow asked the user to
 * enable something that wasn't there, and the live wallpaper had no service to
 * bind to.
 *
 * These declarations are reachable only from the manifest, so only the manifest
 * can be checked.
 */
class ManifestServicesTest {

    private val manifest: String by lazy { locate("src/main/AndroidManifest.xml").readText() }

    @Test fun accessibilityServiceIsDeclaredAndDiscoverable() {
        assertTrue(
            "BifrostAccessibilityService missing — it will not appear in Android's accessibility settings",
            manifest.contains("BifrostAccessibilityService")
        )
        assertTrue(
            "the accessibility service must require BIND_ACCESSIBILITY_SERVICE",
            manifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        )
        assertTrue(
            "the AccessibilityService action is what lists it in the settings screen",
            manifest.contains("android.accessibilityservice.AccessibilityService")
        )
        assertTrue(
            "without the config meta-data the service is rejected at bind time",
            manifest.contains("@xml/accessibility_service_config")
        )
    }

    @Test fun liveWallpaperServiceIsDeclared() {
        assertTrue(
            "VideoLiveWallpaperService missing — the wallpaper cannot be selected",
            manifest.contains("VideoLiveWallpaperService")
        )
        assertTrue(
            "the wallpaper service must require BIND_WALLPAPER",
            manifest.contains("android.permission.BIND_WALLPAPER")
        )
        assertTrue(
            "without the wallpaper meta-data the system ignores the service",
            manifest.contains("@xml/live_wallpaper")
        )
    }

    @Test fun ledServiceCanRunWithoutAProjection() {
        // The accessibility capture path holds no projection token. From Android
        // 14 on, a foreground service claiming only mediaProjection while holding
        // no token is refused, so specialUse must stay declared alongside it.
        assertTrue(
            "LEDService must declare specialUse as well as mediaProjection",
            manifest.contains("mediaProjection|specialUse")
        )
        assertTrue(
            "specialUse requires FOREGROUND_SERVICE_SPECIAL_USE",
            manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
        )
        assertTrue(
            "specialUse requires the subtype property",
            manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        )
    }

    /** Walk up from the test working dir to find a project-relative file. */
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
