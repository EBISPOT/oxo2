# ADR-0017: Component-size guard on the cross-set inference corpus

- **Status**: Proposed — not implemented (no code shipped; recorded for the register)
- **Date**: 2026-07-10 (design scoped 2026-06-17)

## Context

Cross-set SSSOM reasoning ([ADR-0016](0016-single-pass-sssom-reasoning.md)) materialises the identity
closure over the strong predicates `skos:exactMatch`, `owl:equivalentClass`, `owl:equivalentProperty`
and `owl:sameAs`. The `INFER_CROSS_SET` stage in `inferSssomCrossSet.nf` runs the rules over the full
concatenated corpus with **no bound** on that corpus.

On SeMRA-assembled corpora the identity graph can contain a single giant connected component. Measured
on the curated Mapping Commons corpus (2026-06-08, read-only): 52.7M nodes with **one component of
≈685,700 nodes** (next largest only 1,265). The transitive closure minus the asserted edges is on the
order of **~30 billion** directed net-new mappings (≈470B as a symmetric upper bound). Closure over a
685k-node cross-resource component is both explosive and low-quality — `exactMatch` drift across
heterogeneous resources is precisely why SeMRA ships curated *priority* views rather than the raw
closure.

[ADR-0014](0014-mapping-commons-registry-via-specifications-json.md)'s downloader `exclude` list keeps
the raw/processed/mega assemblies out and retains the near-closed priority views, but that guard is
source-side and blunt: the gene priority view alone decompresses to ~569–600 MB, and once
[ADR-0015](0015-default-prefix-map-and-metadata-synthesis-for-bare-sssom.md) recovered the bare
priority views into the cross-set pass, that view actually reaches the corpus. A structural guard on
the inference corpus itself was wanted as a backstop.

The currently-loaded dev corpus is safe only *incidentally*: its largest strong-predicate component is
~434 nodes, so closure stays cheap (first HPC run measured 15 min / 11.5 GB). That is a property of
what happens to be loaded, not an enforced bound.

## Decision

Before the cross-set inference pass, union-find the connected components over the identity union
(`skos:exactMatch ∪ owl:equivalentClass ∪ owl:equivalentProperty ∪ owl:sameAs`) and drop every mapping
edge incident to any node in a component larger than `max_component_size` (proposed default 10000; `0`
disables). Dropped edges are excluded from **inference only** — they are still served as asserted
mappings, since the Solr index is built independently of the inference corpus. The guarded corpus is
the input to **both** inference and tracing, so precomputed explanations match what inference produced.
A sidecar report records every drop — no silent removal.

## Status — not implemented

This design was scoped on 2026-06-17 but **never built**. No pipeline stage, Nextflow parameter,
subcommand, or class implementing it was committed on any branch (`GUARD_CORPUS`, `componentGuard`,
`ComponentGuard`, `max_component_size`, `assertedCorpus.guarded.nq` exist nowhere in the history). This
ADR is recorded so the design and its rationale survive, and so the gap is visible: today's cross-set
pass has no closure safety net.

## Consequences

- Until this (or an equivalent) is built, loading any corpus that forms a large strong-predicate
  component would blow up the cross-set closure at `INFER_CROSS_SET`. The only guards actually in place
  are ADR-0014's source-side `exclude` (keeps out the raw/processed/mega assemblies) and the incidental
  smallness of the loaded corpora's components.
- ADR-0028's explanation sharding (`ShardConclusions` union-find) is **not** a substitute. It partitions
  the already-materialised corpus into per-component shards to bound per-*trace* cost; it runs after
  inference and does nothing to bound the closure inference produces. A giant component would still
  explode at inference, and would additionally degenerate the sharding into one unbounded shard.
- If reinstated, the guard is a structural safety net, not a tuning knob — on today's corpora it drops
  nothing and copies the corpus verbatim. Its memory (the union-find interns every strong-predicate IRI)
  would need calibration against a real large-corpus run.
- Reconsidering chain rule RG2 ([ADR-0006](0006-chainrules-limited-to-those-not-copying-tree.md)), which
  would fuse the entire subclass hierarchy into one strong-predicate component, would require this same
  guard on the chase before it could be safe.
