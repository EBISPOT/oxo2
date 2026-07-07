# ADR-0026: Configurable label match mode for normal search (default case-insensitive exact)

- **Status**: Accepted
- **Date**: 2026-07-07

## Context

A normal search (`POST /api/v2/mappings/search`, the classified-by-shape path) routes a free-text
term to the analyzed `subject_label` / `object_label` / `predicate_label` (`text_general`) fields and
matches it as a **phrase**: lowercased, stopword-stripped, synonym-expanded, and matching a token
*subsequence*. So `"diabetes"` matches `subject_label = "diabetes mellitus type 2"`. This is good for
discovery but gives no way to ask *"return the mapping whose label is exactly this"* — a common need
when the user already knows the label and wants its mappings, not every label that contains the word.

The mappings schema already carries an exact-match string copy of each label — `*_label_str`
(`solr.StrField`, case-sensitive, `indexed`, fed by `copyField`) — currently used only to give the
`text_general` label fields a sortable twin. `StrField` is byte-for-byte case-sensitive, which is
rarely what a user means by "exact label".

The requirement: the API and the UI normal search should default to **case-insensitive exact match**
on labels, and let the user choose between partial, case-insensitive exact, and case-sensitive exact.

## Decision

Introduce a **label match mode** with three values (`LabelMatchType` in `oxo2-shared`):
`PARTIAL`, `EXACT_CASE_INSENSITIVE` (the default), `EXACT_CASE_SENSITIVE`. It affects **only** the
free-text branch of the classified/normal search and its `POST …/search` API. IRI and CURIE terms
remain exact `*_iri` / `*_id` lookups regardless of the mode; the Advanced tab and the batch-map / v1
`/api/search` paths are unchanged.

Each mode selects which label field the subject/object/predicate clause targets (the clause shape —
an OR over the three fields, value quoted and `ClientUtils`-escaped — is otherwise unchanged):

- **`PARTIAL`** → `*_label` (`text_general`): the existing analyzed subsequence/phrase match.
- **`EXACT_CASE_INSENSITIVE`** → new `*_label_ci` (`string_ci` = `KeywordTokenizer` + `LowerCase` +
  `Trim`): the whole label must equal the case-folded, trimmed query.
- **`EXACT_CASE_SENSITIVE`** → `*_label_str` (`string`): the whole label must equal the query
  byte-for-byte.

Add the `string_ci` field type, the `subject/object/predicate_label_ci` fields, and their
`copyField`s to `oxo2-mappings`. Populating `*_label_ci` for existing data **requires a reindex**.

The default is `EXACT_CASE_INSENSITIVE` on both the API (the `labelMatch` request field defaults to it
when omitted) and the UI. The frontend carries the choice in the URL as `?match=<MODE>`, omitted when
it is the default (mirroring how `from`/`to` prefixes are carried, ADR-0024), read on the results page
and threaded into the search request and TSV export.

## Consequences

- **Default search semantics change** from partial-phrase to case-insensitive-exact. Typing
  `"diabetes"` now returns the mapping(s) whose label *is* `"diabetes"` (any case), not everything
  containing the word. Discovery-style partial matching is one dropdown away (`PARTIAL`).
- **Requires a schema change + full reindex** to populate `*_label_ci`. The backend default must not
  go live before the reindexed schema is deployed, or the default would match nothing (the
  `*_label_ci` fields would be empty). Ship the schema + reindex first. A normal `loadData.nextflow`
  run recreates the collection from `managed-schema.xml` and indexes fresh, so it picks the fields up.
- **256-char truncation.** Both exact-match copies (`_ci` and `_str`) are fed by `copyField
  maxChars="256"`, so a label longer than 256 chars can only be exact-matched on its first 256 chars —
  negligible for ontology labels, and consistent with the existing `_str` / `_ngram` copies.
- **No new stored data.** `*_label_ci` is `indexed`-only (`stored="false"`), like `*_label_str` and
  `*_label_ngram`.
- **Unaffected paths.** Column-filter "contains" matching (`*_ngram`) and label sorting
  (`*_label_str`) are untouched. Batch-map and the v1 `/api/search` adapter keep matching labels via
  `subject_label` (partial) — they take lists of terms to *map*, where partial matching is the
  intended behaviour, and do not expose the mode.

## Considered options

- **Reuse `*_label_str` as the only exact mode** — case-sensitive only, surprising for a label search.
  Rejected as the sole exact mode; kept as the `EXACT_CASE_SENSITIVE` option.
- **Lowercase the query and match `*_label_str`** — `StrField` stores original case, so a lowercased
  query cannot match mixed-case stored values. A lowercased *indexed* field (`*_label_ci`) is required;
  there is no query-time-only route to case-insensitive exact match.
- **Make it a results-page view toggle** (like the inference-type filter, ADR-0011) rather than a
  search-bar option — deferred. The mode changes what the typed query *means*, so it lives with the
  query (URL `?match=`), following the `from`/`to` prefix precedent (ADR-0024). It can be promoted to
  a live results-page control later without changing the wire contract.
