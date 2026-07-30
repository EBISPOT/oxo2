# ADR-0045: The typeahead counts only mappings a default search can reach

- **Status**: Accepted
- **Date**: 2026-07-30

## Context

[ADR-0041](0041-obsolete-terms-endpoint-property-hidden-by-default.md) modelled obsolescence as an
*endpoint* property, precisely because a term is obsolete on both sides of mappings across files.
The search honours that on the **mapping**: with `includeObsolete` false, `SolrQueryBuilder` adds

```
*:* -(subject_obsolete:true OR object_obsolete:true)
```

so a row is hidden when *either* end is obsolete. The typeahead honours it on the **entity**: the
fold sets one `obsolete` flag when the entity is itself an obsolete term, and the suggest excludes
those.

Those are different questions, and the gap between them is a whole class of dead suggestion: **a
live entity whose every mapping points *at* an obsolete term.** It is not obsolete, so the
`obsolete` filter passes it through; its count buckets are non-zero, so the bucket filter passes it
too; and every row it could return is hidden by the search.

Found by testing the ADR-0044 work on the worktree corpus. `EFO:0006471` was offered, and picking it
gave an empty table. Its entire mapping set is one row:

| | |
|---|---|
| mapping | `EFO:0006471 SKOS:exactMatch MONDO:0005603` |
| `object_obsolete` | `true` |
| entity `obsolete` on EFO:0006471 | absent — the term is live |
| default search | **0 rows** |
| `includeObsolete=true` search | 1 row |
| suggested? | yes, with `mapping_count: 1` |

The count made it worse than a bare false positive: the row advertised a number the table could not
produce.

The scale is not marginal:

| | distinct subjects |
|---|---|
| offered by the typeahead (has a strong subject-side mapping) | 3,710 |
| reachable by a **default** search | 1,006 |
| **offered but returning nothing** | **2,704 (73%)** |

This is ADR-0035's defect exactly — a read model that disagrees with the query it feeds — for the
third time, on the third dimension. ADR-0035 fixed the predicate dimension,
[ADR-0044](0044-set-scoped-typeahead.md) the mapping-set dimension, and both did it the same way:
put the thing the search filters on into the entity document, bucketed.

## Decision

**1. Every count bucket gains a `_live` twin, counting only sightings whose mapping has no obsolete
endpoint.** Twelve count fields where there were six:
`{subject,object}_count_{strong,subclassof,hasdbxref}` and
`{subject,object}_count_{strong,subclassof,hasdbxref}_live`. "Live" is defined to match
`obsoleteExclusionClause` exactly: neither `subject_obsolete` nor `object_obsolete`. A sighting
credits its base bucket always, and its live twin only when the mapping is live — so live is a
*subset*, not an additional sighting, and the display totals keep summing the base buckets only.

The fold therefore has to read **both** endpoints' obsolete flags per mapping, not just the one for
the side being folded. That is the whole substance of the change: this entity's own obsolescence
cannot tell you whether its mapping is visible.

**2. The suggest reads the live buckets by default and the unrestricted ones when `includeObsolete`
is set.** One `EntityConstants.bucketFor(bucket, includeObsolete)` picks the pair, and because the
existing `VisibleBucket` list drives the filter, the popularity boost, the displayed
`visible_mapping_count` *and* ADR-0044's `set_scope` clause, all four switch together. The displayed
count becomes true: it is the number of rows the search will return under the caller's actual
checkbox state.

Both bucket sets are kept rather than replacing the base six. With `includeObsolete` ticked the
search does show those rows, so the suggest must then be able to offer the entities behind them.

**3. `set_scope` carries the live variants in its bucket component**, so obsolescence composes with
the mapping set the same way side and predicate already do — one field, more tokens, and the reader
asks for the bucket names it needs. No second field, and no fourth token component.

**4. The entity-level `obsolete` filter stays**, though the live buckets now subsume it: an obsolete
term is an obsolete endpoint of every mapping it takes part in, so all its live buckets are zero and
the bucket filter already excludes it. It is kept because it states a different rule ("do not offer
obsolete terms") and is the one that still holds if a future dataload ever stamps the entity flag
and the mapping flags inconsistently. One cached filter query is a cheap guard.

## Consequences

- **Needs a `START_STAGE=mappings2entities` rebuild**, like ADR-0035 and ADR-0044 before it. Until
  it runs, the live fields are absent from every document, and an absent numeric field makes the
  suggest's `sum()` unusable rather than zero — so a default suggest returns nothing at all. Failing
  closed, and still wrong: do not ship the backend ahead of the reindex.
- **Suggestions get dramatically fewer, and that is the point** — on the worktree corpus the
  subject-side offer drops from 3,710 to 1,006. Every one of the survivors returns rows. Same trade
  ADR-0035 made when it cut 46,783 to 3,714, and the same answer: the missing ones are one checkbox
  away.
- The entity document grows six numeric fields, and `set_scope` up to twice the tokens. Both are
  bounded by (sides × predicate buckets × 2) and are small per document.
- `EntityDoc` now keys its counters by **bucket name** rather than by `WeakPredicate`, because the
  live twins are not enum values. `EntityConstants.baseBuckets()` is the single list the fold walks,
  so adding a weak predicate still adds all of its buckets — base and live — in one place.
- The display totals (`mapping_count`, `subject_count`, `object_count`) are unchanged and still
  count every sighting. They sum the base buckets only; summing all keys would double-count every
  live mapping.
- All 22 entity goldens move again, gaining twelve count fields and the live `set_scope` tokens.
- **A new `obsolete-endpoint` cross-set fixture**, because none of the existing 22 has an obsolete
  registry — so every `_live` bucket equalled its base bucket, and a fold that credited an
  obsolete-endpoint sighting to the live twin would have left all of them byte-identical and passed.
  Exactly the hole `HIDDEN_PREDICATES` was added to close for the weak-predicate buckets. The flag
  lives on the registry, not in the TSV, so `ConfigGenerator` writes `"obsolete": true` for any set
  whose base name ends `-obsolete` (`OBSOLETE_SET_SUFFIX`). Its `ex:A` is this bug in miniature: a
  live entity whose one mapping points at the obsolete `ex:DEAD`, so `subject_count_strong` is 1 and
  `subject_count_strong_live` is 0, with `setLive|S|strong` in `set_scope` and no `strong_live`
  twin. `ex:P`/`ex:Q` are the live control, `ex:DEAD` shows an obsolete term with every live bucket
  zero (evidence for decision 4's redundancy claim), and `ex:R` covers the weak×obsolete cross.
  Across the goldens, `live != base` now holds for 5 entity-sides where it previously held for none.

## Alternatives considered

**Fold the obsolete check into the existing buckets — count only live sightings, and drop the base
six.** Half the fields, and the default path stays exactly as fast. Rejected: it makes
`includeObsolete=true` unanswerable. The user can tick "show obsolete terms", the search then
returns those rows, and a suggest with no unrestricted buckets could not offer the entities that
reach them — trading a false-positive bug for a false-negative one.

**Stamp a second entity flag, "has an obsolete partner".** One boolean instead of six longs.
Rejected as too coarse: an entity with a thousand live mappings and one obsolete-partner mapping
would carry the flag and be treated identically to one whose only mapping is dead. The question is
not "does this entity ever touch an obsolete term" but "how many rows does it have that the search
will show" — which is a count, not a flag, and the boost and the displayed count both need the
number.

**Filter the suggest by joining to `oxo2-mappings` at query time.** Exact by construction, and no
new fields. Rejected for the reason ADR-0034 built the entity tier at all: a per-suggestion join
against the denormalised index is precisely the cost the read model exists to precompute away, and
it would put a join on the latency path of every keystroke.

**Leave it, and treat the obsolete registries as the problem.** Tempting, since the corpus that
exposed this has an unusually obsolete-heavy registry. Rejected: the corpus is the one users get,
73% of suggestions were dead, and ADR-0035 already settled the principle — a suggestion is a promise
the search returns rows, whatever makes it not.
