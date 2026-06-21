#!/usr/bin/env bash
# Build a .bfplugin bundle from a plugin's manifest.json (+ optional icons/),
# emit its SHA-256, and print the catalog.json snippet to paste.
#
#   ./build-bundle.sh plugins/fallout4-pipboy 1
#
# A .bfplugin is just the PresetArchiveTransfer ZIP: manifest.json at the root,
# plus an optional icons/ directory. Versioned filename so old installs can
# still fetch the exact bytes they recorded.
set -euo pipefail

DIR="${1:?usage: build-bundle.sh <plugin-dir> <version>}"
VER="${2:?usage: build-bundle.sh <plugin-dir> <version>}"
[ -f "$DIR/manifest.json" ] || { echo "no manifest.json in $DIR" >&2; exit 1; }

ID="$(basename "$DIR")"
OUT="$DIR/${ID}-${VER}.bfplugin"

python3 -c "import json,sys; json.load(open('$DIR/manifest.json'))" \
  || { echo "manifest.json is not valid JSON" >&2; exit 1; }

rm -f "$OUT"
( cd "$DIR" && zip -q -X "${ID}-${VER}.bfplugin" manifest.json $( [ -d icons ] && echo "icons" ) )

SHA="$(shasum -a 256 "$OUT" | awk '{print $1}')"
echo "built: $OUT"
echo "sha256: $SHA"
echo "size:   $(wc -c < "$OUT") bytes"
