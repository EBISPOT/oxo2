#!/usr/bin/env bash
#
# Regenerate the trimmed OLS SSSOM fixtures used by the worktree test config
# (../../oxo-config-test.json). See README.md for why these live in the repo.
#
# Each fixture keeps the full SSSOM metadata block (needed for the curie_map /
# prefix expansion), the column header, EVERY semantic mapping row (skos:*Match,
# owl:equivalentClass, rdfs:subPropertyOf — the strong predicates that drive
# cross-set inference), and a bounded sample of the two weak predicates that
# dominate an OLS export (oboInOwl:hasDbXref, rdfs:subClassOf, hidden by default
# in normal search) so the weak-predicate toggles still have something to reveal.
#
# The EFO fixtures come in two flavours split by subject obsolescence — a live
# export (efo.ols.sssom.tsv) and an obsolete-terms export (efo.ols.obsolete.sssom.tsv)
# — so a worktree can exercise how obsolete subjects are handled. For those two the
# committed fixture basename is identical to the source basename.
#
# The source files are the per-ontology OLS SSSOM exports (the extracted members
# of OLS's sssom.tgz). They are NOT committed — they are multi-MB full exports.
# Point SOURCE_DIR at a directory holding the source basenames listed below.
#
# Usage:
#   ./trim-fixtures.sh [SOURCE_DIR]
#   OXO2_OLS_SSSOM=/path/to/exports ./trim-fixtures.sh
#   WEAK_SAMPLE=500 ./trim-fixtures.sh            # rows per weak predicate per fixture
#
set -euo pipefail

SOURCE_DIR="${1:-${OXO2_OLS_SSSOM:-$HOME/ebi-dev/oxo2/sssom}}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEAK_SAMPLE="${WEAK_SAMPLE:-300}"

# source-basename  ->  committed-fixture-basename
FIXTURES=(
  "efo.ols.sssom.tsv|efo.ols.sssom.tsv"
  "efo.ols.obsolete.sssom.tsv|efo.ols.obsolete.sssom.tsv"
  "mondo.ols.sssom.tsv|mondo.sssom.tsv"
)

for fixture in "${FIXTURES[@]}"; do
  source_file="$SOURCE_DIR/${fixture%%|*}"
  output_file="$OUT_DIR/${fixture##*|}"
  if [ ! -f "$source_file" ]; then
    echo "ERROR: source export not found: $source_file" >&2
    echo "       set SOURCE_DIR / OXO2_OLS_SSSOM to a directory holding the OLS exports" >&2
    exit 1
  fi
  if [ "$source_file" -ef "$output_file" ]; then
    echo "ERROR: source and output are the same file: $source_file" >&2
    echo "       point SOURCE_DIR at the untrimmed exports, not the committed fixtures" >&2
    exit 1
  fi
  awk -v weak="$WEAK_SAMPLE" '
    BEGIN { FS = "\t" }
    { sub(/\r$/, "") }                                  # normalise CRLF exports to LF
    /^#/          { print; next }                       # SSSOM metadata (curie_map, ids, ...)
    !seen_header && /^subject_id/ { print; seen_header = 1; next }
    {
      predicate = $2
      if (predicate == "oboInOwl:hasDbXref" || predicate == "rdfs:subClassOf") {
        if (kept[predicate] < weak) { print; kept[predicate]++ }
      } else {
        print                                            # keep every strong/semantic row
      }
    }
  ' "$source_file" > "$output_file"
  rows=$(grep -vcE '^(#|subject_id)' "$output_file" || true)
  echo "${fixture##*|}: $rows data rows"
done
