# ADR-0019: Resumable HPC dataload via a stage-parameterised SLURM job over published artifacts

- **Status**: Accepted
- **Date**: 2026-06-24

## Context

The HPC dataload runs as a single SLURM batch job (`loadData.slurm`, submitted by `loadData.hpc`) that
executes every stage in order — download, SSSOM→JSON, the cross-set inference substages
(nquads → infer → trace → explain → merge, [ADR-0016](0016-single-pass-sssom-reasoning.md)), the Solr
loads, and the final `solr-data.tar.gz` archive. On the full OLS corpus the run takes many hours, and the
expensive step (`EXPLAIN_CROSS_SET_CHUNK`) dominates. When a late stage failed — a flaky Solr start, or a
bug in the cheap `merge` step that runs *after* the expensive explain — the only recovery was to re-run the
whole pipeline from scratch.

The stopgap was a hand-edited `>>> TEMP run-from-merge <<<` block in `loadData.slurm` that skipped earlier
stages and re-entered the inference DAG by spelunking Nextflow's transient work dir (`NXF_WORK`) for the
per-chunk explanation outputs. Those outputs were never published to a stable path, so the recovery
depended on `NXF_WORK` surviving intact and on the exact container/cache state of the failed run. It was
fragile, manual (a code edit per recovery), and easy to leave committed by accident.

For a production load that fails late, we want to resume from the last successful (sub)stage without
re-running earlier work and without editing the script.

## Decision

The HPC dataload is **resumable from a chosen (sub)stage** via a single `START_STAGE` parameter on one
SLURM job — not a job-per-stage DAG. Three constraints make this correct and durable:

1. **One stage-parameterised job.** `loadData.slurm` defines one ordered stage list and re-enters it at
   `START_STAGE` (default `download` = full run). `loadData.hpc` forwards `START_STAGE` via `--export`;
   resume is "re-submit with a different `START_STAGE`", never a script edit.

2. **Substage resume reads PUBLISHED artifacts, never `NXF_WORK`.** `inferSssomCrossSet.nf` exposes one
   `-entry` workflow per inference substage (`from_infer`, `from_trace`, `from_explain`, `from_merge`),
   each reading the previous substage's published output under `$OXO2_DATA` and re-using the composable
   tails (`inferThroughMerge`/`traceThroughMerge`/`explainThroughMerge`) shared with the default workflow.
   To make this possible, `SPLIT_CROSS_SET_TRACE` and `EXPLAIN_CROSS_SET_CHUNK` now publish their per-chunk
   outputs to `crossSet/chunks/` and `crossSet/chunkChains/` (previously only in `NXF_WORK`). Because resume
   never depends on `NXF_WORK`, `loadData.slurm` wipes the transient Nextflow dirs on every run.

3. **Stage-ownership cleanup.** Each stage "owns" the artifact path(s) it regenerates (`STAGE_OWNS` in
   `loadData.slurm`). A resume wipes only the owned paths of `START_STAGE` and every later stage, preserving
   everything earlier stages produced as the resume inputs. Solr's on-disk index is wiped only when the
   asserted load (`index-asserted`) is in scope; resuming at `explanations2json`/`index-inferred`/`archive`
   keeps the already-indexed asserted data that `explanations2json` queries.

A Jenkins **Freestyle** job drives this over SSH for CI-initiated production loads: its build step (the
`ssh` plugin's *Execute shell script on remote host using ssh*, pointed at a Jenkins-global SSH site so no
credentials live in the repo — the same SSH site the `solr-data.tar.gz` copy job uses) runs
`loadData.jenkins.sh`, with `START_STAGE` as a build parameter. A Freestyle job rather than a `Jenkinsfile`
because that build step has no pipeline DSL and the pipeline alternative (*Publish over SSH*) is not
installed on the controller and cannot be added without disrupting running pipelines. This is an operational
layer on top of, not a replacement for, [ADR-0003](0003-nextflow-as-sole-dataload-path.md) — Nextflow
remains the sole dataload path.

## Consequences

- Recovery from a late failure is "re-run with a different `START_STAGE`", no code edit. The run records the
  last completed stage in `$OXO2_DATA/.oxo2-last-completed-stage` (outside every stage's owned paths, so
  cleanup never removes it); an operator or Jenkins reads it to choose the resume point.
- The old manual `NXF_WORK`-spelunking hack is removed. Resume is independent of the container digest and of
  any cached work dir, because it consumes published artifacts under `$OXO2_DATA`. `NXF_WORK` is now treated
  as disposable and wiped every run.
- The default (full-run) path is unchanged in behaviour: the default `workflow {}` now calls the same
  composable `inferThroughMerge` tail the resume entries use, and the two new `publishDir`s are side-effect
  copies that do not alter any channel output. The integration goldens
  ([`oxo2-integration-tests`](../../oxo2-integration-tests/CONTEXT.md), 108 dynamic tests) still match — the
  local/integration pipeline (`loadData.nextflow`) used no `-entry` and was untouched at the time.
  **Extended by [ADR-0022](0022-resumable-local-dataload-shared-library.md) (2026-06-26):**
  `loadData.nextflow` now shares this same resumable contract via a sourced `loadData.lib.sh`, and the
  stage list / cleanup / checkpoint / Solr-decision logic this ADR put in `loadData.slurm` is the shared
  library both orchestrators source.
- Extra disk under `$OXO2_INFERENCES/crossSet/`: the per-chunk trace inputs (`chunks/`) and explanation
  chains (`chunkChains/`) are now persisted rather than living only in the work dir. They are the durable
  resume contract for `START_STAGE=explain` and `START_STAGE=merge`.
- New cross-cutting invariant for future stage changes: any new dataload stage must declare the path(s) it
  owns in `STAGE_OWNS` and slot into the ordered `STAGES` list, or stage-aware cleanup and resume will not
  reason about it correctly. A substage of the inference DAG that should be independently resumable also
  needs a published artifact and a matching `-entry` workflow.
- The Jenkins job stops at producing `solr-data.tar.gz`; copying it off-cluster and redeploying Solr to
  Kubernetes remains a separate job (the archive contract from the HPC run is unchanged).
