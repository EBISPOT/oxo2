# ADR-0035: Weak predicates become a user-visible control, and the typeahead honours it

- **Status**: Accepted — extended by [ADR-0044](0044-set-scoped-typeahead.md) (mapping-set
  dimension) and [ADR-0045](0045-live-buckets-for-obsolete-endpoints.md) (obsolete-endpoint
  dimension), both applying this ADR's rule that a suggestion is a promise the search returns rows
- **Date**: 2026-07-14

## Context

Two mapping predicates are hidden from search results by default: `rdfs:subClassOf` and
`oboInOwl:hasDbXref`. Neither asserts that two entities are the same thing, neither participates in
SSSOM inference, and on an OLS-derived corpus they are the overwhelming majority of all mappings — so
showing them by default buries the equivalences people are actually searching for. The exclusion was
introduced as a bare filter in `SolrQueryBuilder`, lifted only implicitly, when a caller happened to
filter on a predicate field.

The typeahead ([ADR-0034](0034-entity-collection-for-typeahead.md)) was then built against the
*unfiltered* `oxo2-mappings` index. Its `oxo2-entities` read model counted every mapping an entity
took part in, regardless of predicate, and offered any entity with `is_subject:true` — "is the subject
of *some* mapping". But the search means something narrower: "is the subject of some mapping the user
can *see*". Those two sets are not close. On the `oxo-config-test.json` corpus:

| | entities |
|---|---|
| offered by the typeahead (`is_subject:true`) | 46,783 |
| reachable by a default search | 3,714 |

**92% of suggestions returned an empty table.** Picking `MONDO:0003847` ("hereditary disease") was the
report that surfaced it: 1,586 mappings, every one weak — 7 subject-side `hasDbXref` and 1,579
object-side `subClassOf` — so the search correctly found nothing while the typeahead had confidently
offered it. The popularity boost was wrong in the same way: it ranked that entity on all 1,586
mappings, 1,579 of which have it as the *object* and which a subject-side search would never show.

A read model that disagrees with the query it feeds is worse than no read model, because it *looks*
right.

## Decision

**1. The exclusion becomes an explicit, per-predicate parameter.** `MappingSearchRequest` carries
`includeWeakPredicates`; empty (the default) hides both, exactly as before. The frontend surfaces it as
**two independent checkboxes** on the search page, both unticked by default. Two, not one: ontology hierarchy and loose cross-references are different
questions, and wanting one is no reason to be shown the other. The implicit bypass stays — filtering
explicitly on a predicate field still shows whatever matches, so a filter can never return nothing.

> Update (2026-07-22): the popover that also carried these checkboxes in the result table's Predicate
> column header was removed, so there is one control for one setting. The search form is the single
> home; it still writes the `wp` URL param the results table reads, so a selection made before
> searching is honoured unchanged.

**2. `oxo2-entities` counts are bucketed by predicate and by side.** Six new fields:
`{subject,object}_count_{strong,subclassof,hasdbxref}`, where *strong* is every predicate that is not
weak. The fold reads `predicate_iri` (it previously did not fetch it at all) and buckets each sighting.
The old totals (`mapping_count`, `subject_count`, `is_subject`, …) remain, for display only.

**3. The typeahead filters, ranks and labels on the buckets the checkboxes currently make visible.**
One list drives all three, so they cannot drift:

- **filter** — suggestable iff at least one visible bucket is non-zero, i.e. iff the search returns a
  row;
- **rank** — the popularity boost sums the visible buckets *for the side being searched*, so a
  subject-side typeahead is never ranked on object-side mappings;
- **label** — the count shown on the suggestion row is a `visible_mapping_count` pseudo-field over the
  same sum, not the stored total, so it cannot promise rows the search will hide.

The consequence is deliberate: with both boxes unticked the typeahead offers only 3,714 of the corpus's
46,783 subject-side entities. That is the point. Every one of them returns rows, and the missing 43,069
are one checkbox away.

**4. `WeakPredicate` (in `oxo2-shared`) is the single source of truth** for the pair. The search filter
and the entity fold must agree on them by construction; if they ever disagreed, we would be back to a
typeahead that suggests what the search hides.

## Consequences

- **The read model must be rebuilt** to gain the buckets: `START_STAGE=mappings2entities`, which
  re-derives `oxo2-entities` from the already-indexed mappings without re-running inference or
  explanation. That is exactly the property ADR-0034 bought.
- **`copySolrConfig.sh entities-only` now wipes the entity core** rather than skipping it when present.
  It had to: skipping kept the *old* managed-schema, so the new fields would never have existed and the
  suggest would have queried fields Solr does not have. Wiping a read model costs nothing — every
  document in it is a pure function of `oxo2-mappings` — and it also clears entities the current
  mappings no longer derive.
- **The suggest and the search must be given the same checkbox state.** They are separate HTTP calls,
  so nothing in the type system enforces it; `fetchEntitySuggestions` and `buildSearchRequest` are both
  fed from the one `wp` URL param to keep it true in practice.
- The Advanced tab's entity suggest passes no weak predicates, which matches the Advanced search's own
  default. Consistent, but it means the Advanced tab has no way to reveal them; a checkbox there is
  future work.
- v1's `hideWeakPredicates` is untouched (v1 was built on xrefs and shows them by default).
- **"Weak" is now overloaded.** The `RCE_WEAK_NOCHAIN` fixture means *inference*-weak — `skos:closeMatch`,
  which the RCE role chains do not propagate ([ADR-0016](0016-single-pass-sssom-reasoning.md)). The
  weak predicates here are *visibility*-weak. They are different sets: `skos:closeMatch` is
  inference-weak but perfectly visible, and so belongs in the **strong** entity bucket. `WeakPredicate`
  is the definitive list of the visibility sense; nothing else should hard-code the pair.
- A new `HIDDEN_PREDICATES` integration fixture asserts one `subClassOf` and one `hasDbXref`, because no
  existing fixture did — every weak bucket in every golden was zero, so a fold that credited weak
  sightings to the strong bucket would have left all of them byte-identical and passed.

## Alternatives considered

**Shrink the suggestions silently** — filter the typeahead to strong-predicate entities and say nothing.
Correct, and it is what the default now does, but as the *only* behaviour it makes the typeahead nearly
useless on an xref-heavy corpus: `MONDO:0003847` would simply be unfindable, with nothing on screen to
hint that it exists. Rejected: the fix for "the search hides most of the corpus" should not be "so does
the autocomplete, invisibly".

**Widen the search on an exact pick** — treat picking a suggestion the way an explicit predicate filter
is already treated, and lift the exclusion for that one entity. Attractive (it preserves discovery and
touches no schema), but it makes the rules the user is subject to depend on *how they got there* —
typing "hereditary disease" and picking "hereditary disease" would give different results. Rejected as
too clever. The checkbox says the same thing out loud.

**One combined "show weak predicates" toggle** — simpler, one count bucket instead of two. Rejected: it
forces a user who wants cross-references to also accept the entire subclass hierarchy.
