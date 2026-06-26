# ADR-0023: Collapse + Expand for same-SPO rows, replacing result grouping

- **Status**: Accepted
- **Date**: 2026-06-26
- **Supersedes**: the *grouping mechanism* of [ADR-0013](0013-group-same-spo-mappings-in-result-views.md)
  (its same-SPO collapse behaviour and `spo_key` design still stand)

## Context

[ADR-0013](0013-group-same-spo-mappings-in-result-views.md) collapses same-SPO documents into one
expandable row in the `NormalResultsTable` (Search + Inferences). It implemented this with **Solr
result grouping** — `group=true&group.field=spo_key&group.ngroups=true&group.limit=20` — and
explicitly rejected the CollapsingQParserPlugin, reasoning that result grouping returns members and
the group count in one response. It noted `group.ngroups` as "a modest query cost … revisit only if
a broad query is slow."

A broad query is slow. Measured against the full local index (28.2M docs / 25.4GB), a normal search
for a high-frequency term spends almost all of its time in `group.ngroups`, which computes the exact
distinct-group count by enumerating **every** group across the whole match set:

| Query for `"disease"` (180,018 matches → 160,666 groups) | Solr QTime |
|---|---|
| ungrouped | ~30 ms |
| result grouping **with `group.ngroups=true`** (ADR-0013) | **~18,800 ms** |
| result grouping without `ngroups` | 25 ms |
| `CollapsingQParserPlugin` | ~2,944 ms cold / **1 ms warm** |

The cost scales with term frequency (`cancer` ~3.5 s, `kidney` ~2.7 s), so common single-word
searches — the main use case — are the worst hit. The bottleneck is not the query, the index size,
Solr resourcing, the backend, or the frontend; it is solely `group.ngroups`.

## Decision

Render the same-SPO row with the **CollapsingQParserPlugin + ExpandComponent** instead of result
grouping. The ADR-0013 user-facing decision is unchanged (one expandable row per `spo_key`,
representative = highest inference tier, members on expand, a page is N groups); only the Solr
mechanism changes.

- **Collapse as a post-filter.** Add `fq={!collapse field=spo_key sort='score desc'}`. Collapse keeps
  one representative document per `spo_key` and discards the rest before they reach the main
  collector, reading `spo_key` from its existing `docValues`. `numFound` on the collapsed set **is**
  the exact group count — the value `group.ngroups` paid ~19 s to compute is now free.
- **Representative selection is unchanged.** The collapse `sort='score desc'` reuses the ADR-0011
  boost, so the representative is still the highest inference-tier member, exactly as `group.sort`
  did.
- **Members via Expand.** `expand=true&expand.field=spo_key&expand.sort='score desc'&expand.rows=20`
  returns, only for the ~10 representatives on the current page, their other members — replacing
  `group.limit`'s inlined members. The backend leads each representative's `group_members` JSON with
  the representative itself, then the expanded members, preserving the
  `{"total":N,"members":[...]}` contract the frontend already parses. True group size is
  `1 + expanded.numFound`, so the "+N more" total remains accurate beyond the inlined cap.
- **`spo_key` added to `fl` on the grouped path.** `spo_key` is `docValues`-only (`stored=false`);
  it is requested so each representative carries the key used to join its expanded members, and is
  appended as the total-order paging tiebreaker (as before).
- **Collapse ANDs with existing filters**, so a group's members still reflect only what passed the
  inference-type filter (ADR-0011) — same as ADR-0013's "grouping on top of every filter".
- **Cold-start warming.** Collapse builds per-segment structures on the first query after a commit
  (~3 s). A `newSearcher`/`firstSearcher` warming query
  (`q=*:*&fq={!collapse field=spo_key sort='score desc'}`) in the `oxo2-mappings` solrconfig keeps
  the first user search warm.

## Consequences

- High-frequency normal searches drop from ~19 s to ~1 ms warm; the exact group total is retained.
- No schema or reindex change — `spo_key` already has the `docValues` collapse needs.
- Collapse is a post-filter, so were facets ever enabled on the grouped path, counts would reflect
  the **collapsed** (per-group) set rather than documents. The normal/inferences tables render no
  facets today, so this is latent; it is arguably the more correct behaviour for grouped results.
- The detail view now inlines the representative plus up to `expand.rows` members (≤ 21) rather than
  ≤ 20; the displayed total is unaffected.
- Collapse requires a single-valued `spo_key` present on every document (it is). Documents missing
  the key would each form their own group under the default null policy.

## Considered options

- **Keep result grouping, drop `group.ngroups`** — 19 s → 25 ms, but loses the exact group total
  that drives pagination. Rejected: pagination would have to fall back to the document/match count or
  an estimate.
- **Precompute a representative flag at dataload and filter to it** — fastest, but moves
  representative selection (which depends on the runtime inference-type filter) into the index, where
  it cannot respond to a user hiding a tier. Rejected: breaks ADR-0013's "members reflect only what
  passed the active filter".
- **CollapsingQParserPlugin + ExpandComponent** (chosen) — what ADR-0013 rejected on the assumption
  that `ngroups` was cheap; the measurement reverses that trade-off.
