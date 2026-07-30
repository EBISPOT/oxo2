# ADR-0044: The typeahead honours a mapping-set restriction, and withholds the count when it does

- **Status**: Accepted — extended by [ADR-0045](0045-live-buckets-for-obsolete-endpoints.md), which
  adds the `_live` bucket variants that `set_scope` tokens and the count fields both carry
- **Date**: 2026-07-30

## Context

The search form lets a user narrow a search to specific mapping sets
([ADR-0036](0036-search-form-options-grouped-by-intent.md) surfaces it as the nested "Choose
specific mapping sets…" disclosure). The selection travels as repeated `mapping_set_id` URL params,
becomes `mappingSetIds` on `MappingSearchRequest`, and `SolrQueryBuilder` turns it into a
`mapping_set_id` filter query on `oxo2-mappings`.

**The entity typeahead ignored it completely.** Checking a single set — say
`https://www.ebi.ac.uk/oxo2/inferences` — and typing in the main box went on suggesting entities
from the whole corpus. The failure was structural, at three levels at once:

- `Search.tsx` held the selection in `selectedIds` and passed the ontology prefixes, the predicate
  checkboxes and the obsolete switch down to `EntitySuggest`, but not the mapping sets.
- `EntitySuggestQueryBuilder` added exactly three filter queries — visible count buckets, CURIE
  prefix, obsolete — and had no mapping-set clause to add.
- `oxo2-entities` had **no mapping-set field at all**. `Mappings2Entities` never read
  `mapping_set_id`, and `EntityDoc` bucketed sightings by side and predicate only. Set membership
  was folded away by design and there was nothing left to filter on.

This is the same defect [ADR-0035](0035-weak-predicates-as-a-user-visible-control.md) exists to
prevent — a suggestion must be a promise that searching for it returns rows — reappearing on a
dimension that ADR-0035 did not cover. It is also an unmet claim of
[ADR-0034](0034-entity-collection-for-typeahead.md), which rejected Solr's `SuggestComponent` partly
because it "cannot take an `fq`" and so could not honour "the ontology / corpus / inference /
mapping-set filters that every other read path in OxO2 respects". The entity tier that replaced it
could not honour the mapping-set one either.

The awkward part is the count. `oxo2-entities` stores one number per (side × predicate bucket),
summed over the whole corpus. Those numbers cannot answer "how many rows will this suggestion return
*within the sets I ticked*", and making them able to would mean a number per (set × side × bucket)
on every document.

## Decision

**1. The entity document records which (mapping set, side, predicate bucket) combinations the entity
participates in.** A new multi-valued, indexed, unstored `set_scope` field carries one token per
combination:

```
https://www.ebi.ac.uk/oxo2/inferences|S|strong
https://w3id.org/oxo2/test/minimal/rules/T1|O|hasdbxref
```

`|` is the delimiter because it cannot appear unescaped in an IRI, so it can never occur inside a
set id. The side markers are `S`/`O` and the bucket names are exactly the count-field suffixes
(`strong`, `subclassof`, `hasdbxref`), so `EntityConstants.setScopeToken` is the single place either
side of the contract spells a token. `Mappings2Entities` now reads `mapping_set_id` and records the
token in the same branch that increments the count, so the tokens and the counts cannot come to
disagree about how an entity is findable. The field is written only when non-empty: a corpus whose
mappings carry no set id folds to byte-identical documents.

**2. The three dimensions travel in ONE token, not three clauses.** This is the substance of the
decision. A plain multi-valued `mapping_set_id` field would have been cheaper and is wrong: the side
and predicate filters are separate `fq` clauses, so an entity that is a subject in set A and merely
an object in set B satisfies `mapping_set_id:B` and `subject_count_strong:[1 TO *]` independently —
and a subject-side search of set B returns nothing. Only by folding set, side and bucket into a
single term does the conjunction hold by construction. The suggest's filter is therefore an OR over
the **cross product** of the ticked sets and the currently visible buckets, as exact terms. The
product is small: a handful of buckets times the sets the user actually ticked.

**3. Under a restriction the count is withheld, not estimated.** `EntitySuggestQueryBuilder` omits
the `visible_mapping_count` pseudo-field when a set filter is present, `mapping_count` is then
absent from the response (`Long`, `NON_NULL`), and the suggestion row shows no count. Suppressing it
is the honest option: the filtering stays exact — the suggestion really does return rows — but the
only number available is corpus-wide and would overstate a narrowed selection. That would be
ADR-0035's broken promise one level down: not "these rows do not exist" but "there are fewer of them
than I told you". An absent field, rather than a zero, because a zero reads as "this returns
nothing", which is the one thing it does not mean. Ranking is unaffected: the popularity boost still
runs over the corpus-wide buckets, which orders a restricted list acceptably and is the only signal
there is.

**4. Every option that narrows the search is passed to the typeahead.** `Search.tsx` now hands
`selectedIds` to `EntitySuggest`, which puts it in the react-query key alongside the weak-predicate
and obsolete state — ticking a set changes *which* entities are suggestable, so a cached list from
another selection is wrong, not merely stale. The API param is a repeatable `mappingSetId`, matching
how `prefix` and `includeWeakPredicates` already travel.

## Consequences

- **The entity tier must be rebuilt for this to take effect.** `set_scope` is absent from every
  existing `oxo2-entities` document, and an absent field matches no set restriction — so against a
  pre-reindex collection a set-restricted typeahead returns *nothing* rather than too much. That is
  the safe direction of failure (it never offers a term the search cannot find) but it is still
  wrong, so the change is not live until `mappings2entities` has run. Per ADR-0034 that is a
  resumable stage: `START_STAGE=mappings2entities`, not a full reload.
- The suggestion row loses its count whenever a set is ticked. Accepted deliberately — see decision
  3.
- Index cost is one multi-valued string field whose per-document cardinality is (sets the entity
  appears in × sides × visible predicates in each). Entities typically appear in a handful of sets,
  so this is a few tokens per document; the distinct-term count across the index is bounded by sets
  × 2 × (1 + |WeakPredicate|).
- The set filter is added as its own `fq` even though it subsumes the visible-bucket filter, so Solr
  caches the two independently and the unrestricted case — the overwhelming majority of queries —
  keeps hitting the cheap one unchanged.
- A mapping with no `mapping_set_id` contributes no token, so its entities are unreachable under any
  set restriction. Correct by the same argument as above: without a set id there is no evidence the
  entity is in the chosen set.
- `EntitySuggestQueryBuilder` now derives the count fields from a `VisibleBucket` list rather than
  building field names directly, so one list drives the bucket filter, the boost, the displayed
  count **and** the set filter. Extending ADR-0035's "one list so they cannot drift apart" to the
  new dimension.
- The `oxo2-entities` integration goldens gain a `set_scope` array in every fixture, which is what
  pins the writer's behaviour: the `T1` fixture carries both an asserted set and `oxo2/inferences`,
  so its goldens show an entity scoped to two sets, and `HIDDEN_PREDICATES` shows the weak buckets
  appearing in tokens rather than only in counts.

## Alternatives considered

**A bare multi-valued `mapping_set_id` field.** Cheaper, and one obvious field instead of a
composite token. Rejected: it cannot express that the side and predicate restrictions must hold
*within* the chosen set, so it would still offer entities that complete to an empty table — a
quieter version of the bug being fixed, and the kind that looks right.

**Per-set count buckets, so the count survives a restriction.** A number per (set × side × bucket),
summed as a function query over whatever subset the user ticked. This is the only way to keep an
honest count, and it was rejected on cost and shape: the document grows a numeric field per set the
entity appears in, and the `fl` becomes a `sum()` over an unbounded, request-dependent field list.
The count is a nicety; the filter is the correctness requirement. Withholding one number is a
smaller price than a per-set numeric cross-product in every document.

**Route the suggest to `oxo2-mappings` when a set is ticked.** `oxo2-mappings` already carries
`mapping_set_id` alongside `subject_label_ngram`/`subject_id_ngram`, so a set-restricted suggest
could reuse `SolrQueryBuilder`'s existing filter and need no reindex at all — exactness for free, by
construction, from the very objects the search uses. Rejected on two counts. It needs a collapse or
facet on `subject_id` to dedupe a denormalised index, which is the shape that has already OOM'd a
local Solr over a whole set — and `oxo2/inferences`, the set that surfaced this bug, is the largest
one there is. And `oxo2-mappings` has no whole-string `label_prefix_ngram` (ADR-0034 put it on the
entity tier deliberately), so ranking would silently degrade — "malignant mel" would stop matching
as a leading-edge prefix — giving two suggest code paths with different relevance. The whole point
of the entity tier is that the typeahead has one.

**Hide or disable the typeahead while a set is ticked.** Zero backend work and never lies. Rejected
for the same reason ADR-0035 rejected shrinking the suggestions silently: the fix for "the search is
narrowed" should not be "so the autocomplete stops working".
