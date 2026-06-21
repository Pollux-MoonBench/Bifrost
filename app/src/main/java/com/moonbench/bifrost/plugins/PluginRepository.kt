package com.moonbench.bifrost.plugins

import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Retrieval + update logic for the plugin store. Zero-dependency networking
 * over HttpURLConnection (no OkHttp/Retrofit added). All calls BLOCK — invoke
 * off the main thread.
 */
object PluginRepository {

    private const val DEFAULT_TIMEOUT_MS = 10_000
    private const val MAX_BUNDLE_BYTES = 8L * 1024 * 1024   // 8 MB sanity cap

    sealed class CatalogResult {
        data class Success(val catalog: PluginCatalog) : CatalogResult()
        data class Failure(val message: String) : CatalogResult()
    }

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Failure(val message: String) : DownloadResult()
    }

    /** Fetch + parse the catalogue. Never throws — folds errors into Failure. */
    fun fetchCatalog(url: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): CatalogResult =
        try {
            CatalogResult.Success(PluginCatalog.parse(httpGetText(url, timeoutMs)))
        } catch (e: CatalogParseException) {
            CatalogResult.Failure(e.message ?: "malformed catalogue")
        } catch (t: Throwable) {
            CatalogResult.Failure("could not reach catalogue: ${t.message}")
        }

    /**
     * Of the catalogue's plugins, those that are installed AND whose catalogue
     * version exceeds the installed version. The single source of "update
     * available" truth — used by the launch check and the store UI.
     */
    fun computeUpdates(catalog: PluginCatalog, prefs: SharedPreferences): List<PluginUpdate> =
        updatesFor(catalog, PluginPrefs.installedVersions(prefs))

    /** Pure update-detection core (no Android) — see [computeUpdates]. */
    fun updatesFor(catalog: PluginCatalog, installed: Map<String, Int>): List<PluginUpdate> =
        catalog.plugins.mapNotNull { entry ->
            val iv = installed[entry.id] ?: return@mapNotNull null
            if (entry.version > iv) PluginUpdate(entry, iv) else null
        }

    /**
     * Download a plugin bundle to [dest]. Verifies SHA-256 if the entry carries
     * one. Never throws — folds errors into Failure (and deletes a partial /
     * mismatched file).
     */
    fun downloadBundle(
        entry: CatalogEntry,
        dest: File,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): DownloadResult {
        return try {
            httpDownload(entry.bundleUrl, dest, timeoutMs)
            val expected = entry.bundleSha256
            if (expected != null) {
                val actual = sha256(dest)
                if (!actual.equals(expected, ignoreCase = true)) {
                    dest.delete()
                    return DownloadResult.Failure(
                        "integrity check failed (sha256 mismatch)")
                }
            }
            DownloadResult.Success(dest)
        } catch (t: Throwable) {
            dest.delete()
            DownloadResult.Failure("download failed: ${t.message}")
        }
    }

    // ---- HTTP ------------------------------------------------------------

    private fun openGet(url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("User-Agent", "Bifrost-PluginStore")
        }

    private fun httpGetText(url: String, timeoutMs: Int): String {
        val conn = openGet(url, timeoutMs)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownload(url: String, dest: File, timeoutMs: Int) {
        val conn = openGet(url, timeoutMs)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BUNDLE_BYTES) throw IOException("bundle too large")
                        output.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
