# ADR-0050: Production data releases come from the stable branch

- **Status**: Accepted
- **Date**: 2026-08-28

## Context

The HPC dataload had exactly one target: the dev environment. `loadData.hpc` hardcoded the dev
NFS/HPS trees, the dev checkout's config, and the `:dev` container image; `cleanup.hpc` hardcoded
the same trees; and the GHCR digest probe hardcoded the `dev` tag. The hardcoding also silently
clobbered the environment variables the Jenkins Freestyle job exports (`OXO2_CONFIG`,
`NF_CONTAINER`), leaving those job parameters dead.

Production OxO2 now needs its own data release. The serving side deploys the release to two
Kubernetes clusters (a failover cluster first, then prod), but that is invisible to the dataload:
both consume the same `solr-data.tar.gz`. A `stable` branch exists (a strict ancestor of `dev`),
and a checkout of it is already provisioned at `/nfs/production/parkinso/spot/oxo2/prod/oxo2`.
Nothing built container images from `stable`, so there was no image a production dataload could
run.

## Decision

Production data releases are produced by the same HPC dataload, parameterised by environment:

- **`loadData.env.sh`** is the single derivation of "where an environment lives", sourced by all
  three login-node entry points (`loadData.hpc`, `loadData.jenkins.sh`, `cleanup.hpc`) — the same
  anti-drift pattern `loadData.lib.sh` uses for the stage contract. `loadData.slurm` stays
  environment-agnostic (everything reaches it via `--export`).
- **`OXO2_ENV` selects the environment** from a whitelist — `dev` (the default; behaviour
  unchanged) or `prod` — and an unknown value hard-fails before anything is created or deleted.
  Every derived variable is a `${VAR:-...}` default, so an explicitly exported variable still
  overrides it: `OXO2_ENV` picks the set, a named variable overrides a member. This revives the
  Jenkins job parameters.
- **`prod` is the `s/dev/prod/` mirror**: `/nfs/production/parkinso/spot/oxo2/prod` (logs, digest
  file, checkout) and `/hps/nobackup/parkinso/spot/oxo2/prod` (data, Nextflow dirs, Solr home).
- **Production runs the `stable` branch**: the prod checkout on NFS is of `stable`, prod uses that
  checkout's `oxo-config.json` (full corpus), and prod pulls the mutable
  `ghcr.io/ebispot/oxo2-nextflow:stable` image, which CI now builds on every push to `stable`.
  Release pinning is the branch itself; the existing digest probe handles the tag moving. The
  probe now parses repository and tag out of `NF_CONTAINER` instead of hardcoding `dev`.
- **Cutting a release is a manual act**: a pull request merging `dev` into `stable`, then pulling
  the prod checkout on NFS. Pushing `stable` publishes the `:stable` images — it is a release
  gesture, never routine.
- **The archive contract is unchanged**: each environment writes
  `$OXO2_DATA/inferences/solr-data.tar.gz`; the trees are disjoint, so dev and prod never collide.
  Retention/rollback of production tarballs belongs to the copy-to-NFS deploy side, not the
  dataload.

## Consequences

- The dev workflow is untouched: no exported variables means dev, exactly as before.
- The prod HPS tree does not need manual provisioning — the entry points `mkdir -p` what they
  need on first run.
- The prod Jenkins Freestyle job is a clone of the dev job whose build step additionally exports
  `OXO2_ENV=prod`, hardcoded in the step rather than offered as a parameter so a misclick cannot
  run prod from the dev job. See `oxo2-dataload/CONTEXT.md` § Resumable dataload.
- A third environment is a two-line extension of the whitelist in `loadData.env.sh` — plus a
  decision about which branch/tag feeds it, which is exactly why the whitelist maps environment
  name to image tag explicitly (`prod` → `:stable`; the names deliberately differ).
- Dev and prod dataloads may now run concurrently. Each batch job starts its own Solr on :8983 on
  its allocated node; if Slurm ever places both main jobs on the same node, the second Solr fails
  to bind and that run aborts loudly at the readiness probe — resume it with `START_STAGE` once
  the node frees. Accepted as rare and fail-loud rather than plumbing per-environment ports
  through the pipeline.
- `loadData.env.sh` is login-node-only and is deliberately NOT in the Dockerfile COPY allowlists —
  no container script sources it.
