---
name: Inference scope is per mapping set
description: Nemo inference must run per SSSOM file; merging files to share materialisation is explicitly disallowed
type: project
---

Inference (INFER_MAPPINGS / EXPLAIN_INFERENCES_TO_TRACE in `inferAndExplainMappings.nf`) must be executed on a per-file basis — one `nmo` run per SSSOM mapping set. Do not propose merging TTL inputs across mapping sets to amortise rule materialisation, even when it would be faster.

**Why:** This is an intentional design decision — inferences must stay contained within a single mapping set so that derived facts can be attributed to / filtered by their source set. Cross-set chains would conflate provenance.

**How to apply:** When suggesting performance improvements for the infer/explain stages, stick to within-file optimisations: reducing redundant nmo invocations per file (e.g., collapsing INFER + EXPLAIN via Nemo API), right-sizing SLURM resources, widening `queueSize`, or skipping empty-chain files. Do not suggest consolidating inputs across files.
