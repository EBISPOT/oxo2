# ADR-0003: Nextflow is the sole dataload execution path

- **Status**: Accepted
- **Date**: 2026-05-12

## Context

The OxO2 dataload was originally implemented with two parallel execution paths: per-stage `.sh` scripts for local sequential runs, 
and Nextflow `.nf` workflows for production/HPC runs. The two paths diverged over time — stage logic existed in two places, 
optimisations landed in one and not the other, and the inference stage in particular grew chunked-tracing parallelism that 
only makes sense under a workflow engine.

Maintaining both paths cost more than it saved. Local debugging of individual stages remains valuable, but the *production* 
and *integration-test* dataload should have one definition.

## Decision

The production dataload runs via Nextflow only. `loadData.nextflow` (which calls `downloadMappings.nf`, `sssom2json.nf`, 
`determineInferencesAndExplanations.nextflow` → `inferAndExplainMappings.nf`, `json2solr.sh`) is the single execution 
path used by Docker, HPC (`loadData.hpc` / `loadData.slurm`), and local runs.

The per-stage `.sh` scripts (e.g. `inferMappings.sh`, `traceAndExplainMappings.sh`, `explanations2json.sh`) remain in the 
tree but are *debug-only* — they are not part of the production pipeline.

## Consequences

- Nextflow is a hard runtime dependency. README's "running locally from the commandline" path lists Nextflow as required prerequisite.
- New dataload stage logic is implemented in `.nf` files. Per-stage `.sh` scripts may shadow `.nf` logic for debug purposes 
but are not the source of truth.
- Within-stage parallelism (e.g. per-set chunked tracing in `inferAndExplainMappings.nf`) lives in `.nf` files; 
the equivalent `.sh` scripts are sequential and slower.
- A docs/CONTEXT.md or README description of "the pipeline" describes the `.nf` flow, not the `.sh` chain.
