package com.moonbench.bifrost

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the StartupGuideActivity crash (2026-06):
 *
 * MainActivity.onCreate launched StartupGuideActivity, but it was never
 * declared in AndroidManifest.xml. With minifyEnabled=true the release build's
 * R8 renamed the undeclared class (→ s2.j1) and startActivity threw
 * ActivityNotFoundException, crashing the app the instant the UI opened — and
 * ONLY in release builds, so debug testing never caught it.
 *
 * This test fails fast (no device, no minification) if any Activity launched
 * via `startActivity(Intent(ctx, X::class.java))` is missing from the manifest.
 */
class ManifestActivitiesTest {

    @Test
    fun every_launched_activity_is_declared_in_the_manifest() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        val srcRoot = locate("src/main/java")

        // Inline explicit-Activity launches: startActivity(Intent(ctx, X::class.java))
        val launchRegex =
            Regex("""startActivity\(\s*Intent\(\s*[\w@.]+\s*,\s*(\w+)::class\.java""")

        val missing = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> launchRegex.findAll(file.readText()).map { it.groupValues[1] to file.name } }
            // Conservative: only flag when the class name appears NOWHERE in the
            // manifest (the clear regression). Avoids false positives on FQN /
            // ".Name" declaration styles.
            .filter { (cls, _) -> !manifest.contains(cls) }
            .map { (cls, file) -> "$cls (launched in $file)" }
            .distinct()
            .toList()

        assertTrue(
            "Activities launched via startActivity(Intent(ctx, X::class.java)) but " +
                "absent from AndroidManifest.xml. R8 renames undeclared activities in " +
                "release builds → ActivityNotFoundException on launch. Declare them: $missing",
            missing.isEmpty()
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
