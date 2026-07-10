# ADR-0018: Out-of-core cross-set explanation

- **Status**: Superseded by [ADR-0020](0020-defer-explanations-to-on-demand.md)
- **Date**: 2026-06-24

## Context

Under single-pass SSSOM reasoning ([ADR-0016](0016-single-pass-sssom-reasoning.md)) the explanation
step (`EXPLANATIONS_TO_JSON`, `ExplainInferredMappings`) runs over a single merged
`inferences-chains.json` that is the union of every set's inference chains. The original
implementation loaded that whole file into a `NemoInferences` object and then reconstructed the
complete `InferredMapping` DAG in heap before it could emit anything, so resident memory scaled with
the entire cross-set closure.

On the dev corpus the merged chains file reached ~20 GB on disk, whose parsed object graph runs well
past 100 GB. The first cross-set HPC run OOM'd; raising the heap is a brute-force dead end (we briefly
sized the SLURM task to 320 GB just to attempt a verification run). The closure size is the binding
constraint, and it grows with the corpus even with the priority-view `exclude`
([ADR-0014](0014-mapping-commons-registry-via-specifications-json.md)) keeping the raw SeMRA assemblies
out upstream (the further component-size guard on the inference corpus this assumed,
[ADR-0017](0017-cross-set-inference-corpus-component-size-guard.md), was never built).

On-demand explanation (compute a chain only when a user inspects a mapping) was rejected: `distance`
is a user-facing filter facet, so it must be materialised for every inferred mapping at load time.

## Decision

`ExplainInferredMappings` materialises every inferred mapping's explanation, but **out-of-core**: a
first streaming pass indexes every inference into an on-disk `conclusion -> (ruleName, premises)`
store (`OnDiskChainStore`); a second pass builds and writes one inferred mapping per final conclusion,
resolving premise chains through that store against a bounded LRU. Heap holds only an in-memory
`hash64 -> file-offset` directory (~16 bytes per inference) plus the bounded cache; the record bytes
live on disk. The store is a hand-rolled pure-Java index — no embedded-DB dependency (RocksDB's
native library and MapDB's `sun.misc.Unsafe` internals are both fragile in the Singularity / Java 25
runtime).

This is correct because the explanation is **identity-independent**: `explanationLength`,
`determineAssertedMappingsForExplanation`, `distance`, and the serialised explanation tree are pure
functions of a conclusion's reachable sub-DAG, so sharing sub-chains is a performance optimisation
only — an LRU eviction that forces a sub-chain to be rebuilt yields an equal result, never a
different one.

## Consequences

- Heap for `EXPLANATIONS_TO_JSON` is flat in the closure size; the SLURM tier drops from a 320 GB
  brute force to 16 GB. The new RAM driver is the directory (~16 bytes/inference), observable from the
  `On-disk chain store: ... directory capacity` log line; recalibrate only if it approaches the heap.
- A new **disk** requirement: a temp records file ~the size of the chains file is written under the
  task work dir, so the dataload scratch volume must have room for it. It is deleted on completion.
- The chains file must keep unique conclusions (`MergeChainFiles` already de-duplicates), and its
  inference records must carry `ruleName` + `premises`; the store persists nothing else.
- `EXPLANATIONS_TO_JSON` now runs with `errorStrategy = 'terminate'` (overriding the pipeline default
  `'ignore'`): with a single cross-set output, a swallowed failure would silently drop *all* inferred
  mappings.
- Dead code removed: `NemoInferenceReader`, `NemoHelper.fromNemoInferencesToInferredMappings` /
  `prefetchAssertedMappingsForChains`, and `NemoInferences`' in-heap `findNemoInferenceForConclusion`
  lookup. The chain walk now goes through the `InferenceLookup` seam.
- If the in-memory directory itself ever becomes the limit, the next tightening is a sparse
  block-index (sorted SST) so directory memory drops to O(inferences / block); not needed yet.
