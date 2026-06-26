# ADR-0022: Resumable local dataload via a shared stage-contract library

- **Status**: Accepted
- **Date**: 2026-06-26
- **Extends**: [ADR-0019](0019-resumable-hpc-dataload.md)

## Context

[ADR-0019](0019-resumable-hpc-dataload.md) made the HPC orchestrator (`loadData.slurm`) resumable from a
chosen `START_STAGE` over published artifacts. The local / integration orchestrator
(`loadData.nextflow`) was left as a run-from-scratch script: it began with an unconditional
`rm -R $OXO2_DATA/*`, ran every stage unconditionally, and wrote no checkpoint.

Both orchestrators run the same logical stages — `download → sssom2json → nquads → infer →
index-asserted → inferences2json → index-inferred`, plus an HPC-only `archive` — but expressed them
independently. The resumability logic (ordered stage list, `should_run` gating, stage-aware cleanup via
`STAGE_OWNS`, the resume checkpoint, the Solr wipe / needed decisions) lived only in `loadData.slurm`.
A full local load over the OLS corpus also takes hours, so the same late-failure recovery problem
applies locally; but simply copying `loadData.slurm`'s stage logic into `loadData.nextflow` would create
two copies of the stage list and `STAGE_OWNS` map that must be kept in lock-step.

Separately, `loadData.slurm` gated the Solr-config copy — `copySolrConfig.sh`, which **deletes and
recreates** the `oxo2-mappings` / `oxo2-mappingsets` core dirs (config *and* data) — on `SOLR_NEEDED`
rather than on the wipe decision. So a resume at `inferences2json` / `index-inferred` would have wiped
the asserted index it then queries for entity CURIEs/labels. Latent only because that resume path had
not yet had a real run.

## Decision

The resumable-pipeline contract is extracted into one sourced shell library,
`oxo2-dataload/loadData.lib.sh`, used by **both** orchestrators; `loadData.nextflow` becomes resumable on
the same `START_STAGE` model as `loadData.slurm`.

1. **Single source of truth.** `loadData.lib.sh` owns the canonical ordered stage list, `should_run`, the
   stage-aware cleanup (`oxo2_clean_owned_artifacts` over `OXO2_STAGE_OWNS`), the resume checkpoint
   (`$OXO2_DATA/.oxo2-last-completed-stage`), and the `should_wipe_solr` / `solr_needed` decisions. Both
   `loadData.slurm` (HPC) and `loadData.nextflow` (local/integration) source it. Neither orchestrator
   defines the stage sequence or cleanup rules itself.

2. **Orchestrators keep only environment-specific mechanism.** Stage *bodies* differ — local runs
   `nextflow` on the host with Solr via `$SOLR_SCRIPT/solr` + `copySolrConfig.sh`; HPC wraps each step in
   `singularity exec` / `module load` — as does the transient-dir wipe. The library hands each script the
   booleans; each acts with its own mechanism.

3. **One canonical list, `archive` included.** `archive` (writing `solr-data.tar.gz`) stays in the
   canonical stage list. `loadData.nextflow` has no body for it, so locally the archive stage is a no-op
   and its owned tarball path never exists. This keeps a single sequence both orchestrators reason over,
   rather than per-script lists that could diverge.

4. **The Solr-config copy is gated on the wipe decision.** Both orchestrators gate `copySolrConfig.sh` on
   `should_wipe_solr` (not `solr_needed`), so a resume that preserves the asserted index keeps the cores.
   This corrects the latent `loadData.slurm` gating described above.

Local substage resume mirrors HPC: `determineInferences.nextflow` forwards `-entry from_infer` when
`START_STAGE=infer`, reading the published `assertedCorpus.nq` (the `from_infer` entry that
`inferSssomCrossSet.nf` already exposes for HPC). This **extends, not supersedes**, ADR-0019 — the HPC
decision stands — and does not touch [ADR-0003](0003-nextflow-as-sole-dataload-path.md): Nextflow remains
the sole dataload path.

## Consequences

- `loadData.nextflow` is resumable: `START_STAGE` default `download` = full run, behaviour identical to
  before — the integration harness's per-fixture isolation is unchanged, since every fixture defaults to a
  clean full pass. Any later `START_STAGE` resumes over preserved artifacts. The old unconditional
  `rm -R $OXO2_DATA/*` is replaced by stage-aware cleanup plus the always-wiped transient Nextflow dir.
- The cross-cutting invariant from ADR-0019 tightens: a new dataload stage must be declared in
  `loadData.lib.sh` (the stage list **and** `OXO2_STAGE_OWNS`), not in either orchestrator, or the two
  paths drift. A new independently-resumable inference substage still needs a published artifact and a
  matching `-entry` workflow.
- `loadData.lib.sh` must ship in the dataload images — `loadData.nextflow` sources it and runs inside the
  container under docker-compose — so it is added to the `COPY` allowlists in `Dockerfile.dataload` and
  `Dockerfile.nextflow`.
- The `copySolrConfig` gating fix makes `START_STAGE=inferences2json` / `index-inferred` correct on both
  paths (asserted cores preserved for the entity-detail lookups `inferences2json` performs).
- `loadData.slurm` shrinks to HPC-specific mechanism (singularity Solr lifecycle, image pull, archive)
  plus the sourced contract; the local and HPC stage sequences can no longer diverge silently.
