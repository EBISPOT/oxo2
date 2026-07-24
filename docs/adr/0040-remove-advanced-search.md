# ADR-0040: Remove the Advanced search surface from the frontend

- **Status**: Accepted
- **Date**: 2026-07-24
- **Amends**: [ADR-0034](0034-entity-collection-for-typeahead.md) (retires its Advanced-tab suggest
  tier; the entity collection and the other two suggest surfaces are unchanged),
  [ADR-0036](0036-search-form-options-grouped-by-intent.md) (the search form no longer has tabs)

## Context

The Advanced tab offered one query box per SSSOM field — 40+ boxes across seven groups — backed by
its own flat, wide results table (`AdvancedResultsTable`) and the `/search/_advanced` route. The
premise was a corpus rich in per-field SSSOM metadata worth querying field-by-field:
authors/reviewers, curation rules, match strings, similarity measures, preprocessing steps.

The loaded corpora do not hold that metadata. The OLS-derived sets populate little beyond the
subject/predicate/object triple and provenance basics, and ADR-0034's field-classification pass had
already found the pattern: seven vocabulary fields empty across the corpus, two fields that were
never searchable at all. Most Advanced boxes either match nothing or duplicate what the results
table's column filters (subject/predicate/object, justification, provider, distance) already do over
a live search. Meanwhile the tab carried a disproportionate share of the frontend: a second results
table, a field-definition model, a per-field typeahead component, its own URL state hooks, and a
second submit path through the shared form.

## Decision

Remove the Advanced search surface from the frontend entirely: the Advanced tab (the search form no
longer has tabs), the `_advanced` route sentinel and its `af=` params, `AdvancedResultsTable`, the
`AdvancedFields` model, the per-field `SuggestField`/`VocabSuggest` typeahead, and the
`advancedFieldQueries` plumbing in the frontend request builder.

The backend is unchanged: `advancedFieldQueries` and `queryFields` on `POST /api/v2/mappings/search`
remain for API clients, as does the global distinct-values suggest
(`GET /api/v2/suggest/values`, now frontend-unused).

## Consequences

- Per-field narrowing in the UI is now the results table's column filter popovers only, scoped to a
  live search. A field the compact table does not filter on (e.g. `mapping_tool`, `license`) is
  reachable only through the API.
- A same-SPO group's expanded panel shows the members embedded in `group_members`; the "+N more"
  overflow that deep-linked into the Advanced table is now a plain count. The full member list of an
  oversized group is reachable via the API or the mapping-set view.
- Old `/search/_advanced?af=…` links no longer resolve to a field query; the term `_advanced` is
  searched literally and returns nothing.
- ADR-0034's typeahead reduces to two surfaces (main box entity suggest, contextual column-filter
  values). The cardinality-tier reasoning stands wherever a per-field suggest is next needed.
- `tableUrlState` loses `useUrlColumnFilters` and the per-caller "reset page on sort" switch — with
  one results table, sorting always resets to the first page.
- Reinstating a metadata-rich query surface later is a product decision to take fresh, against a
  corpus that actually carries the metadata — not a revert of this removal.
