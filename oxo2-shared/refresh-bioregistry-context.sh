#!/bin/bash
# Refresh the bundled Bioregistry prefix-map snapshot.
#
# The snapshot at src/main/resources/bioregistry.context.jsonld is the @context object of
# Bioregistry's published JSON-LD context. It is the fallback curie_map for SSSOM sets that
# ship no curie_map of their own (see BioregistryPrefixMap and ADR-0015). Bundling it keeps
# the dataload reproducible and offline; re-run this script periodically to pick up new
# prefixes.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/src/main/resources/bioregistry.context.jsonld"
SOURCE_URL="https://raw.githubusercontent.com/biopragmatics/bioregistry/main/exports/contexts/bioregistry.context.jsonld"

echo "Fetching $SOURCE_URL"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
curl -fsSL "$SOURCE_URL" -o "$TMP"

# Sanity-check it parses and has a non-trivial @context before overwriting the bundled copy.
ENTRIES="$(python3 -c "import json,sys; print(len(json.load(open('$TMP'))['@context']))")"
if [ "$ENTRIES" -lt 1000 ]; then
  echo "Refusing to update: only $ENTRIES prefixes parsed (expected >= 1000)." >&2
  exit 1
fi

mv -- "$TMP" "$TARGET"
trap - EXIT
echo "Updated $TARGET with $ENTRIES prefixes."
