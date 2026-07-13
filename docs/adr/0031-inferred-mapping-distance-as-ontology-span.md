# ADR-0031: Inferred-mapping distance is the ontology span

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Every mapping carries a `distance` field. `SolrQueryBuilder`'s inferred ranking tier decays with it —
`div(INFERRED_BOOST, pow(5, distance-1))` — so distance is the lever that ranks a short inference
above a long one. Asserted mappings are fixed at `distance = 1` (`TSV2JSON`).

For inferred mappings the field was left at its inert model default of `1`
([ADR-0028](0028-component-sharded-explanation-precompute.md)), because the only implementation that
ever existed was wrong in two ways:

- **It only recognised OBO `PREFIX_NUMBER` IRIs.** `extractParts` split each IRI on `/`, took the
  last segment, and kept the text before its first `_`. A MeSH (`…/D020176`), UMLS (`…/C0006142`) or
  any non-underscore IRI contributed *nothing*. Since the result was `distinctParts − 1`, a mapping
  whose endpoints were both non-OBO underflowed to `0`, and one whose endpoints yielded no part at
  all underflowed to **`−1`** — which the decay turns into `div(100, pow(5, −2)) = 2500`, boosting an
  inferred mapping **above** the curated (1000) and asserted tiers.
- **It descended only one premise level**, so an ontology reached only deeper in the proof DAG was
  never counted.

The field the user actually wants is a count of ontologies: an asserted mapping, or an inference
whose entities lie in at most two ontologies, is distance 1; three ontologies is 2, and so on. OxO2
already has an exact, consistent notion of "the ontology a term belongs to" — its CURIE prefix
([ADR-0024](0024-cross-ontology-mapping.md), emitted as `subject_prefix` / `object_prefix`).

## Decision

Populate `distance` for inferred mappings as **the number of distinct CURIE prefixes across every
subject and object in the explanation DAG (conclusion plus all premises, transitively), minus one,
floored at 1.** Predicates are not counted. The prefix of an entity is
`EntityReference.getCuriePrefix()` — the single source of truth now shared with `subject_prefix` /
`object_prefix`, so the same ontology never splits into two buckets across differently-cased sources.

This makes an asserted or ≤2-ontology mapping distance 1, a 3-ontology explanation distance 2, a
4-ontology one distance 3, and so on.

## Consequences

- **Ranking now degrades multi-ontology inferences as intended.** A 2-ontology inference stays at the
  `INFERRED_BOOST` of 100; 3 ontologies → 20; 4 → 4. Direct inferences continue to sit just below the
  curated/asserted tiers, longer chains fall further, and asserted mappings are unaffected.
- **The floor at 1 is load-bearing, not cosmetic.** It is what unblocks the ADR-0028 concern: because
  distance can never drop below 1, `pow(5, distance-1)` is always ≥ 1 and the inferred tier can never
  exceed the asserted/curated tiers. The `−1`/`0` underflow that made populating the field unsafe is
  structurally impossible. A wholly intra-ontology chain (one prefix) therefore ranks as a direct
  mapping rather than scoring `distance = 0`.
- **This supersedes the "`distance` stays inert" decision in ADR-0028.** That ADR's other decisions
  stand; only its deliberate non-population of `distance` is reversed here.
- **The OxO v1 API is unaffected.** `V1SearchController` derives v1's coarse `distance` from a
  `asserted ? 1 : 2` sentinel (ADR-0024) and never reads the stored field, so the richer value is a
  v2-ranking-only change.
- **Existing indexed data reads as distance 1 until reloaded.** The stored value only changes on a
  fresh `loadData.nextflow` pass; `SolrQueryBuilder` already treats a missing/1 distance as one hop,
  so pre-reload behaviour is exactly today's.
- **Scope: the top-level mapping and its explanation root carry the span; nested premises keep the
  model default of 1.** Distance is computed for the conclusion — the doc that gets ranked — not
  recomputed independently for each interior proof node. The interior `distance` fields in the
  serialised explanation are display artefacts and are not used for ranking.
- **Coverage.** `MappingDistanceTest` and `EntityReferenceCuriePrefixTest` pin the calculation and
  the shared prefix helper; the `DISTANCE_MULTI_HOP` integration fixture (three synthetic ontologies
  `ex`/`ey`/`ez`) proves the span flows end-to-end — its 3-ontology conclusions capture as
  `distance = 2`, its 2-ontology ones as `distance = 1`. The single-prefix fixtures are unchanged
  (all legitimately distance 1).
