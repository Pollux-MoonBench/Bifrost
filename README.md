# bifrost-plugins

The plugin catalogue for [Bifrost](https://github.com/KuriGohan-Kamehameha/Bifrost).
Bifrost's **Plugin Store** (Settings → *Open Plugin Store*) fetches `catalog.json`
from this repo's `main` branch and installs the bundles it lists.

## How it works

A **plugin** is a Bifrost preset bundle (`.bfplugin` — a ZIP containing
`manifest.json`, the same format as an in-app preset export) plus the catalogue
metadata in `catalog.json`. Installing a plugin imports its preset(s) and wires
up the app→preset mapping, so the effect auto-activates for its target app.

Bifrost reads the catalogue from:

```
https://raw.githubusercontent.com/KuriGohan-Kamehameha/bifrost-plugins/main/catalog.json
```

(Override in the Plugin Store screen for a fork or local server.)

## `catalog.json`

```jsonc
{
  "schema": "bifrost_plugin_catalog",   // fixed
  "version": 1,                          // catalogue schema version
  "plugins": [
    {
      "id": "fallout4-pipboy",           // stable unique id (the update key)
      "name": "Fallout 4 Pip-Boy",
      "author": "MoonBench / Strip-Boy",
      "version": 1,                      // bump to publish an update
      "versionName": "1.0",              // display only
      "description": "…",
      "icon": "DISPLAY",                 // a Bifrost PresetIcon name
      "targetPackage": "com.bethsoft.falloutcompanionapp",
      "minBifrostVersionCode": 9,        // 0 = no floor
      "bundleUrl": "https://raw.githubusercontent.com/.../fallout4-pipboy-1.bfplugin",
      "bundleSha256": "…"                // integrity check (from build-bundle.sh)
    }
  ]
}
```

**Updates** are version-driven: Bifrost records the installed `version` per `id`
and offers an update when the catalogue's `version` is higher. Always ship a new
versioned bundle file (`…-2.bfplugin`) rather than overwriting an old one, so
clients that recorded the old bytes can still re-fetch them.

## Adding / updating a plugin

1. Create `plugins/<id>/manifest.json` (a `bifrost_preset_bundle` — export a
   preset from Bifrost and unzip it for a template).
2. Build the bundle and get its hash:
   ```
   ./build-bundle.sh plugins/<id> <version>
   ```
3. Add (or bump) the entry in `catalog.json` with the printed `bundleUrl` +
   `bundleSha256`.
4. Commit and push to `main`. The store picks it up on next refresh.

## Plugins

| id | name | for |
|----|------|-----|
| `fallout4-pipboy` | Fallout 4 Pip-Boy | Fallout 4 companion app |
