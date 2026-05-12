---
name: Inference scope is per mapping set
description: Nemo inference must run per SSSOM mapping set because the SSSOM spec defines chaining rules over a single mapping set
type: project
---

Inference (INFER_MAPPINGS / EXPLAIN_INFERENCES_TO_TRACE_CHUNK in `inferAndExplainMappings.nf`) must be executed on a per-mapping-set basis — one `nmo` run per SSSOM mapping set (or per chunk within a set, after the within-set chunked-tracing change). Do not propose merging TTL inputs across mapping sets to amortise rule materialisation, even when it would be faster.

**Why:** The SSSOM specification defines chaining rules as operating over a *given mapping set*, with rule metadata recorded against that set. See https://mapping-commons.github.io/sssom/dev/chaining-rules/ — "The idea is to provide the functionality to apply these chaining rules over a given mapping set, and record the appropriate metadata for that rule." Merging mapping sets before inference would violate the spec, not just blur provenance.

**How to apply:** When suggesting performance improvements for the infer/explain stages, stick to within-file optimisations: reducing redundant nmo invocations per file (e.g., collapsing INFER + EXPLAIN via Nemo API), right-sizing SLURM resources, widening `queueSize`, or skipping empty-chain files. Do not suggest consolidating inputs across files.
