# ADR-0028: Precompute explanations again, via component-sharded chase+trace

- **Status**: Accepted
- **Date**: 2026-07-08
- **Supersedes**: [ADR-0020](0020-defer-explanations-to-on-demand.md)
- **Relates to**: [ADR-0018](0018-out-of-core-cross-set-explanation.md) (identity-independence of an
  explanation), [ADR-0021](0021-on-demand-explanation-resident-nemo-engine.md) (on-demand service —
  see Consequences)

## Context

[ADR-0020](0020-defer-explanations-to-on-demand.md) stopped precomputing explanations because the
dataload's `EXPLAIN_CROSS_SET_CHUNK` fan-out ran the pipeline up to ~48 h: each of the K chunk tasks
re-imported the whole `assertedCorpus.nq` and re-applied `sssom.rls`, so the full reasoning ran once
per chunk. Inferred mappings have shipped **bare** since — no explanation chain, no asserted
evidence, no source-set union.

[ADR-0021](0021-on-demand-explanation-resident-nemo-engine.md) then measured, on the real dev corpus
(7.2M quads, 55.9M derived facts), that a *resident* Nemo engine traces one conclusion in ~1.2 s
typical and ~6 s worst-case, and concluded that bulk precompute of all 14.9M conclusions would cost
~341 h even on 16 engines — worse than the 48 h it replaced. That verdict is correct **for the
architecture it measured**, and it is why explanations stayed off the dataload.

The premise hidden inside it is that a trace must run against the whole materialisation. It need
not. nmo's backward search (`execution_engine/tracing/simple.rs`) re-joins against the *global*
tables at every derivation step, so **per-trace cost scales with the size of the store being
traced against, not with the size of the conclusion's own proof**. Shrink the store and the same
proof comes out orders of magnitude faster.

It can be shrunk, because an OxO2 explanation is **component-local**. Every `sssom.rls` rule joins
its head's subject to its head's object through shared body variables, and every body atom is a
`mapping` over one of the nine strong predicates (`identityPredicate` ∪ `nonIdentityStrong`).
Therefore, for any derived `mapping(<nil>, s, p, o)`:

- `s` and `o` lie in the same connected component of the corpus's strong-predicate edges; and
- by induction over the same argument, so does every atom in its proof DAG.

A shard corpus holding every asserted quad whose subject *and* object are in one component thus
admits exactly the same derivations for that component's conclusions as the full corpus does. And
because `mapping` is derived monotonically — the only negation, `~assertedTriple`, is over an EDB
and feeds only `inferredMapping`, never the traced `mapping` facts — a sub-corpus can never invent a
conclusion either. The shard is neither missing a premise nor able to add one.

The dev corpus decomposes into **1,442,981 components over 4,353,186 entities, the largest just 434
nodes**. So the store a trace runs against drops from 55.9M facts to a few thousand.

## Decision

Restore precomputed explanations to the dataload, built by **component-sharded chase+trace**.

Two new stages sit between `infer` and `index-asserted` (they need no Solr), and
`explanations2json` replaces `inferences2json`:

```
download → sssom2json → nquads → infer → shard → explain
         → index-asserted → explanations2json → index-inferred → archive
```

- **`shard`** — `ShardConclusions` (a `MainDispatcher` subcommand) union-finds `assertedCorpus.nq`
  over `OXOInferenceConstants.STRONG_PREDICATES`, packs whole components into shards, routes each
  asserted quad to the shard holding both its endpoints, and writes every conclusion of
  `inferences.ttl` to its component's shard as a semicolon-separated `--trace-input-file`.
  Self-mappings (`s == o`) are skipped: the indexer already drops them, and they are 4,397,710 of
  the 19,300,716 conclusions.
- **`explain`** — one `nmo` process per shard (`EXPLAIN_SHARD`): chase the shard's tiny corpus once,
  then trace every conclusion it owns against that warm engine.
- **`explanations2json`** — `ExplainInferredMappings`, unchanged in substance, now consuming a
  *bundle* of shard traces per JVM (default 100) so that process startup and the Solr connection
  amortise. Each bundle emits `inferences-explained-NNNNN.json`; `MERGE_INFERRED_MAPPING_SETS`
  unions the per-bundle `mapping_set_source` into the one cross-set `MappingSet`.

**Shards are capped by entity count, not conclusion count.** Per-trace cost is linear in the shard's
dictionary size — measured `ms_per_trace ≈ 0.95 + 3.47e-4 × entities` (R² = 0.93) — so capping by
entities rather than conclusions nearly halves CPU per conclusion (2.429 → 1.325 ms in a controlled
A/B over the same components). Hence `--maxShardEntities`, default 1200. Components are never split;
a component larger than the cap becomes a shard of its own.

`distance` is **not** repopulated. See Consequences.

## Measurements

All on the real dev corpus (7,224,812 quads, 19,300,716 inferred triples, 14,903,006 after dropping
self-mappings), on a 14-core / 30 GB desktop.

| step | result |
|---|---|
| `shard` | 3,607 shards from 1,442,981 components; **29 s**, 4.7 GiB RSS |
| `explain` | all 3,607 shards, **0 failures**: **29.4 min wall on 13 cores, 6.35 CPU-h**, 20.3 GB of trace JSON |
| coverage | **14,903,006 conclusions requested, 14,903,006 explained — zero shortfall** |
| per-trace, sharded | **0.3–0.9 ms** |
| per-trace, full 55.9M-fact store | **~6.2 s** (reproduces ADR-0021's 6.25 s) |

The `explain` wall is bounded below by its one longest shard — the one holding the 434-node clique,
~7 min — because a component cannot be split across processes. More cores shorten the total but not
that floor.

Correctness was checked against the full corpus rather than argued. 25 conclusions traced against
the complete 7.2M-quad `assertedCorpus.nq` (a 1,030 s chase deriving 55,923,798 facts at 11.59 GiB
peak, matching ADR-0021's HPC job 62712918) have **byte-identical full proof DAGs**, not merely
identical top steps, to their component-sharded traces. Separately, 1,873/1,873 facts shared
between two differently-sized shard stores agreed on rule *and* premises.

Two independent checks on the model:

- Σk³ over all components predicts a 14.3 min full-corpus chase; the real `INFER_CROSS_SET` takes
  14–15 min. The chase *is* the clique-closure work.
- `ShardConclusions` routed all 14,903,006 conclusions without once finding one whose endpoints
  straddled two components — the component-locality claim, checked on every conclusion in the corpus
  rather than on a sample.

## Consequences

- **Explanations return to the index with no backend or frontend change.** The Solr schema
  (`explanation`, `asserted_mappings`, `explanation_length`), the `@Field(EXPLANATION)` binding on
  `Mapping.Builder`, the frontend's `fieldList` request and its `InferredMappingGraph` renderer were
  all left intact by ADR-0020 and simply light up on reindex.
- **The inferred set's `mapping_set_source` union is restored.** ADR-0020 left it empty because
  recovering it needs the per-leaf `mapping_id` provenance, which only the trace exposes.
- **Reindex required, and `asserted_mappings` becomes `indexed="false"`.** Like `explanation`, it
  holds a JSON document escaped into one value and is retrieve-only — nothing queries, facets or
  sorts on it; the frontend parses the stored string client-side. Inverting it for ~14.9M inferred
  mappings would cost a large index for no query.
- **`distance` stays at its inert default of 1, deliberately.** ~~`calculateMappingDistance` counts
  *distinct CURIE prefixes minus one*, not chain depth, and returns `-1` when no endpoint has an
  underscore-bearing local name.~~ **Superseded by [ADR-0031](0031-inferred-mapping-distance-as-ontology-span.md):**
  `distance` is now populated as the ontology span (distinct CURIE prefixes minus one, floored at 1),
  which removes the `-1`/`0` underflow that made this unsafe. The original concern still explains
  *why* it was left inert here: `SolrQueryBuilder`'s inferred-tier boost is
  `div(INFERRED_BOOST, pow(5, distance-1))` — rewritten by
  [ADR-0027](0027-config-driven-mapping-set-category.md) from the additive form ADR-0020
  documented — so a `distance = -1` doc would have been boosted **25× above an asserted mapping**.
  `explanation_length` *is* populated and is well-defined.
- **Disk.** The dev corpus produces 3.4 GB of shard corpora + targets, 20.3 GB of shard trace JSON,
  and an estimated ~85 GB of `inferences-explained-*.json` (measured mean proof DAG 10.4 nodes, p99
  36, at ~550 B per serialized node ⇒ ~5.7 kB/doc). The last figure is not new — it is what the
  pre-ADR-0020 pipeline wrote — but the two intermediates are, so an HPC run needs ~24 GB of
  headroom it did not previously need. `EXPLAIN_SHARD` publishes with `mode: 'move'` so the 20 GB
  is not transiently doubled.
- **Failure is loud.** Nextflow exits 0 when a *workflow operator* (as opposed to a task) throws, so
  a silently empty explain stage would index every inferred mapping unexplained. Both orchestrators
  therefore assert `#chain files == #shards` after the stage rather than trusting the exit status,
  and `ExplainInferredMappings` fails a bundle if any explanation has a dangling premise — a node
  whose `chainRuleApplications` is *absent*, as opposed to an asserted leaf's present-but-empty one.
- **`STRONG_PREDICATES` must stay in sync with `sssom.rls`.** Adding a predicate to a rule *body*
  without adding it to `OXOInferenceConstants.STRONG_PREDICATES` would split proofs across shards.
  The dangling-premise guard turns that into a hard failure rather than silently-unexplained
  mappings.
- **The bare inferred-mapping path is removed.** `BareInferredMappings`, `inferences2json.nf`,
  `inferences2jsonNextflow.sh`, and `DataloadSolr.prefetchEntityDetailsByIris` (which existed only
  because the bare path had no chains to drive a `mapping_id` prefetch) are deleted.
- **[ADR-0021](0021-on-demand-explanation-resident-nemo-engine.md) keeps its latency numbers but
  loses its motivation.** A resident engine over the whole corpus still costs ~1.2 s typical / ~6 s
  worst per trace; sharding is a *precompute* technique, not a fix for synchronous request latency.
  What this ADR removes is ADR-0021's premise that precompute was infeasible. A resident engine
  holding only component shards would be a different, unexplored service design.
- **Supersedes [ADR-0020](0020-defer-explanations-to-on-demand.md).** ADR-0020's reasoning was sound
  given its cost model; that model measured a whole-materialisation trace and does not bound a
  per-component one. ADR-0018's core insight — that an explanation is *identity-independent*, a
  pure function of a conclusion's reachable sub-DAG — is exactly what licenses sharding, and
  survives.
