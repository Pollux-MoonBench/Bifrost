package com.moonbench.bifrost.plugins

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The plugin store screen. Lists the catalogue, installs/updates/removes
 * plugins, and exposes the "check for updates at launch" setting. Built
 * programmatically (no XML) to stay self-contained and not touch the main UI.
 *
 * Network + install run on background threads; the UI is rebuilt on the main
 * thread after each action.
 */
class PluginStoreActivity : AppCompatActivity() {

    private val prefs by lazy { PluginPrefs.prefs(this) }
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView

    private var catalog: PluginCatalog? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Plugin Store"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(buildRoot())
        refreshCatalog()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ---- UI scaffold ----------------------------------------------------

    private fun buildRoot(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Plugins"
            textSize = 22f
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Install LED packs for your games and apps. Updates are " +
                "applied on demand; nothing is downloaded automatically."
            textSize = 13f
            alpha = 0.7f
            setPadding(0, 0, 0, dp(12))
        })

        // "Check for updates at launch" toggle (mirrors the persisted setting).
        root.addView(CheckBox(this).apply {
            text = "Check for plugin updates at launch"
            isChecked = PluginPrefs.checkUpdatesAtLaunch(prefs)
            setOnCheckedChangeListener { _, checked ->
                PluginPrefs.setCheckUpdatesAtLaunch(prefs, checked)
            }
        })

        root.addView(Button(this).apply {
            text = "Refresh catalogue"
            setOnClickListener { refreshCatalog() }
        })

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(statusText)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        return scroll
    }

    // ---- catalogue load + render ----------------------------------------

    private fun refreshCatalog() {
        if (loading) return
        loading = true
        statusText.text = "Loading catalogue…"
        listContainer.removeAllViews()
        val url = PluginPrefs.catalogUrl(prefs)
        Thread {
            val result = PluginRepository.fetchCatalog(url)
            runOnUiThread {
                loading = false
                when (result) {
                    is PluginRepository.CatalogResult.Success -> {
                        catalog = result.catalog
                        renderCatalog(result.catalog)
                    }
                    is PluginRepository.CatalogResult.Failure -> {
                        statusText.text = "Couldn't load catalogue:\n${result.message}\n\nSource: $url"
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderCatalog(catalog: PluginCatalog) {
        listContainer.removeAllViews()
        if (catalog.plugins.isEmpty()) {
            statusText.text = "No plugins in the catalogue yet."
            return
        }
        statusText.text = "${catalog.plugins.size} plugin(s) available."
        catalog.plugins.forEach { listContainer.addView(pluginRow(it)) }
    }

    private fun pluginRow(entry: CatalogEntry): View {
        val installed = PluginPrefs.installedVersion(prefs, entry.id)
        val hasUpdate = installed != null && entry.version > installed

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
        }

        card.addView(TextView(this).apply {
            text = "${entry.name}  ·  v${entry.versionName}"
            textSize = 17f
        })
        if (entry.author.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = "by ${entry.author}"
                textSize = 12f
                alpha = 0.6f
            })
        }
        if (entry.description.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = entry.description
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        card.addView(TextView(this).apply {
            text = when {
                hasUpdate -> "Installed v$installed — update available"
                installed != null -> "Installed"
                else -> "Not installed"
            }
            textSize = 12f
            alpha = 0.7f
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (installed == null) {
            buttons.addView(actionButton("Install") { doInstall(entry) })
        } else {
            if (hasUpdate) buttons.addView(actionButton("Update to v${entry.versionName}") { doInstall(entry) })
            buttons.addView(actionButton("Uninstall") { doUninstall(entry) })
        }
        card.addView(buttons)
        return card
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    // ---- actions --------------------------------------------------------

    private fun doInstall(entry: CatalogEntry) {
        statusText.text = "Installing ${entry.name}…"
        Thread {
            val result = PluginInstaller.install(this, prefs, entry)
            runOnUiThread {
                when (result) {
                    is PluginInstaller.Result.Success ->
                        toast("Installed ${entry.name}")
                    is PluginInstaller.Result.Failure ->
                        toast("Install failed: ${result.message}")
                }
                catalog?.let(::renderCatalog)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun doUninstall(entry: CatalogEntry) {
        val result = PluginInstaller.uninstall(prefs, entry)
        when (result) {
            is PluginInstaller.Result.Success -> toast("Removed ${entry.name}")
            is PluginInstaller.Result.Failure -> toast("Uninstall failed: ${result.message}")
        }
        catalog?.let(::renderCatalog)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
