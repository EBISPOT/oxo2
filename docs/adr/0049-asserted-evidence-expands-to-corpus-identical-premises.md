# ADR-0049: Asserted evidence expands to every corpus-identical premise

- **Status**: Accepted
- **Date**: 2026-08-27
- **Amends**: the provenance recovery of
  [ADR-0028](0028-component-sharded-explanation-precompute.md) (its "source-set union recovered
  from the per-leaf `mapping_id` provenance" now reads "from the corpus quads each leaf stands
  for")

## Context

The inference corpus is N-Quads whose graph term carries the asserted mapping's `mapping_id`
([ADR-0010](0010-carry-mapping-provenance-via-nquads.md)). When the same triple is asserted in
more than one mapping set — routine in the loaded corpora, and the reason the same-SPO collapse
exists ([ADR-0013](0013-group-same-spo-mappings-in-result-views.md)) — the corpus holds several
quads that are byte-identical in subject/predicate/object and differ only in that graph term.
To Nemo these premises are indistinguishable: any of them supports the same derivation, and its
trace records whichever one the chase happened to walk.

The explanation stage took that arbitrary choice at face value. Each asserted leaf was resolved
by the traced `mapping_id` alone, so the emitted document's `asserted_mappings`, the leaf shown
in its explanation chain, and the inferred set's `mapping_set_source` union all depended on a
choice Nemo makes non-deterministically. Two dataloads over identical inputs could produce
different output.

The `prefix-case-drift` fixture (added alongside [ADR-0048](0048-spo-key-uses-the-normalised-id.md))
made this visible: its two sets assert one triple under different prefix casing, which expands to
identical IRIs, and its goldens flipped between `setA` and `setB` across runs — observed directly
as a 2-in-3 integration failure. The defect predates that fixture; on the production corpus it had
simply never been pinned by a golden, only quietly making releases non-reproducible.

There is also a truthfulness gap distinct from the flakiness: when two sets assert a premise, the
evidence for a conclusion drawn from it *is* both mappings. A single-element `asserted_mappings`
under-reports provenance even when it happens to be stable.

## Decision

An asserted leaf is resolved against the **shard corpus**, not just the trace:

- Each explanation bundle loads the `.nq` files behind its chain files into an index of
  (subject, predicate, object) → all `mapping_id`s asserting that exact triple
  (`ShardAssertedIndex`). Shards are disjoint strong-predicate components
  ([ADR-0028](0028-component-sharded-explanation-precompute.md)), so no triple can occur in two
  shards and one merged index serves a whole bundle.
- **The chain shows one canonical leaf** — the duplicate with the lowest `mapping_id` — instead
  of the trace's arbitrary pick. The duplicates are interchangeable premises, so the proof is
  valid with any of them; lowest-id makes the choice deterministic.
- **`asserted_mappings` carries the full duplicate set**, deduplicated and ordered by
  `mapping_id`. Deduplication keys on `mapping_id`, never on `InferredMapping` equality — that is
  (s, p, o) identity, which would conflate the very duplicates being collected.
- **`mapping_set_source` follows**: the union over the expanded evidence, so every set that
  asserted a premise is credited.
- The corpus `.nq` is the **ground truth for participation**. Every inclusion rule — applicable
  predicate, no predicate modifier, valid IRIs, the
  [ADR-0037](0037-confidence-gate-on-inference-corpus.md) confidence gate — was applied when the
  quad was written, so reading the corpus back needs no second copy of those rules and cannot
  drift from them. In particular, a mapping the confidence gate kept out of the corpus is never
  credited as evidence, even though it is indexed as asserted.
- The shards directory reaches the explanation JVM as `OXO2_SHARDS_DIR` (set by
  `explanations2json.nf`); without it, resolution falls back to the chain file's own directory
  and then the `shards` sibling of its directory (the local on-disk layout). If no `.nq`
  resolves, leaves keep the trace's own `mapping_id` — the pre-ADR-0049 behaviour — so older
  layouts degrade gracefully rather than fail.

## Consequences

- **Dataload output is deterministic for indistinguishable premises**: two runs over the same
  corpus emit byte-identical explanation documents. The `prefix-case-drift` goldens stop
  flipping; its `asserted_mappings` now lists both sets' mappings and its inferred
  `mapping_set_source` is `[setA, setB]` — the truthful union.
- **Scope**: this eliminates the arbitrary choice *among identical (s, p, o) premises*. Where a
  conclusion has genuinely different derivations — different triples proving the same conclusion —
  Nemo's choice of derivation path remains its own, and remains arbitrary. No golden has ever
  pinned such a case; if one appears, it is a different problem (enumerating Nemo's proof forest)
  and out of scope here.
- The other 23 fixtures contain no duplicate (s, p, o), so expansion is a no-op there and their
  goldens are unchanged — which doubles as the regression check on this ADR.
- On the production corpus, `asserted_mappings` grows wherever a premise is multiply asserted;
  the explanation chain itself does not grow (one canonical leaf per premise, and the duplicate
  list is never serialised into it).
- The bundle's bulk Solr prefetch covers every corpus `mapping_id`, not just the traced ones, so
  duplicate resolution stays a cache hit.
- A full reload changes inferred-mapping documents (evidence and set metadata) wherever
  duplicates exist — see the reindex note in `oxo2-dataload/CONTEXT.md`.

## Considered options

- **Query Solr for asserted docs sharing the leaf's IRIs** — rejected: it needs its own copy of
  the corpus inclusion rules (confidence gate, modifier exclusion) and silently drifts when they
  change. Mirroring what another component computes, imperfectly, is the exact failure mode
  ADR-0048 just repaired in `spo_key`.
- **Canonicalise the pick without expanding** — rejected: determinism requires enumerating the
  duplicates anyway, so it costs the same as full expansion and still under-reports evidence.
- **Deduplicate the corpus at build time** (one quad per triple, with a sidecar mapping back to
  all ids) — rejected for now: it also removes the non-determinism at its source and shrinks the
  reasoning input, but it touches corpus construction, sharding, and the sidecar plumbing at
  once. Worth revisiting if corpus-size pressure returns.
- **Leave it and re-baseline the golden to whichever set wins locally** — rejected: the golden
  would pin a coin flip, and production releases would stay non-reproducible.
