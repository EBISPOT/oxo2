# ADR-0052: Versioned software releases are cut automatically from the stable branch

- **Status**: Accepted
- **Date**: 2026-10-23

## Context

[ADR-0050](0050-production-data-release-channel.md) made "merge `dev` into `stable`" the release
act: every push to `stable` rebuilds the five container images and moves the mutable `:stable`
tags that the production HPC dataload and the production/failover clusters pull. That gave
production a channel, but not a release:

- **Nothing names a release.** The Maven reactor has said `1.0.0-SNAPSHOT` since the first
  commit, `oxo2-frontend/package.json` says `0.0.0`, the chart's `appVersion` is `"1.0"`, and the
  repository has no git tags and no GitHub releases. "Which OxO2 is production running?" is
  answered with a commit SHA read off an image digest, and "what changed since the last release?"
  with `git log` between two dates.
- **`:stable` can be half-moved.** `docker.yml` builds the five images as independent matrix jobs
  with `fail-fast: false`, and each job moves its own `:stable` tag the moment it finishes. A
  failed or slow `oxo2-nextflow` build leaves the prod dataload's digest probe pulling a
  `:stable` image from a different commit than the `oxo2-backend` that will serve its output.
- **Nothing is citable or traceable.** There is no software DOI, no release notes, and a running
  deployment or a `solr-data.tar.gz` on NFS cannot be tied to the software that produced it
  without the registry.

A release process has to keep the one gesture ADR-0050 established — merging into `stable` — and
must not add a second manual step (editing version strings, tagging by hand, writing a GitHub
release) that can be forgotten, done out of order, or done differently each time. Everything a
release needs can be derived from the merged pull request and the resulting push, so the process
should derive it. Three facts about OxO2 shape the design:

- OxO2's history is mostly **direct commits with conventional-commit subjects**, not pull
  requests; GitHub's PR-based release-notes generator would produce near-empty notes.
- OxO2 ships **five images** (`oxo2-dataload`, `oxo2-nextflow`, `oxo2-backend`, `oxo2-frontend`,
  `oxo2-solr`), today built for **linux/amd64 only**. The HPC and the clusters are x86_64, but
  OxO2 is also run on developers' and collaborators' machines through Docker Desktop, which on
  Apple-silicon Macs and ARM Windows runs `linux/arm64` containers. Everything in the images is
  architecture-neutral (Java jars, Solr, Node) except the Nemo binary, which the dataload images
  download as a fixed `x86_64-unknown-linux-gnu` tarball; Nemo also publishes an
  `aarch64-unknown-linux-gnu` build of the same version.
- The **data release is a separate pipeline** (ADR-0050) that consumes `:stable`. An
  ontology-mapping data update is not a software release and must not become one.

## Decision

**A software release is a pull request merged into `stable`.** The push to `stable` runs the
existing checks and then the release workflow. Nobody tags by hand, and no version string in
`pom.xml`, `package.json` or `Chart.yaml` changes: the git tag and the generated release manifest
identify the software release, and the Maven/npm/Helm identifiers keep their existing values (the
`oxo2-*-1.0.0-SNAPSHOT.jar` names are baked into the Dockerfiles' `COPY` paths).

- **Versions are `vMAJOR.MINOR.PATCH`.** The bump comes from exactly one label on the merged PR,
  `release:patch` (the default when no label is present), `release:minor` or `release:major`; two
  bump labels fail preparation rather than guess. The first release is the configured
  `firstVersion` in `.github/release/config.json`, **`2.0.0`**: OxO2 is the second OxO, and the
  major number says so to users who know the first. The `1.0.0-SNAPSHOT` in the Maven reactor
  was a placeholder, never a release. Thereafter the previous version is the highest existing
  `vX.Y.Z` tag.
- **Release notes come from the PR, else from the commits.** The PR template
  `.github/PULL_REQUEST_TEMPLATE/stable-release.md` carries a `## Release notes` section (Added /
  Changed / Fixed). If it is absent or left as the empty template, the notes are generated from
  the commit subjects between the previous tag and the release commit, grouped by
  conventional-commit type (`feat` → Added, `fix` → Fixed, everything else → Changed, with
  `chore(deps)` commits collapsed into one "Dependency updates" line) and ending with the GitHub
  compare link. GitHub's own release-notes generator is not the fallback: it only sees pull
  requests, and most OxO2 changes are not one.
- **The release workflow is `release.py`, run in four idempotent stages from the push to
  `stable`**, after the checks pass and gated so that only `EBISPOT/oxo2`'s `stable` runs it:
  1. *prepare* — refuse unless the checked-out SHA is on `stable`, no earlier release is
     unfinished, and release order follows `stable` history; choose version and notes; create a
     **draft** GitHub release whose body ends in a hidden JSON state comment, the durable state
     every later step and every re-run reads.
  2. *archive* — create a lightweight tag `vX.Y.Z` at the tested SHA, publish the release, and
     wait (polling Zenodo's public records API, no token) for the software DOI that Zenodo's
     native GitHub integration mints from the tagged source. `.zenodo.json` holds the static
     metadata only; DOI, version and date are never written into the repository. The step is
     skipped when `config.json` sets `"archiving": "none"`, so releases work before the Zenodo
     integration is enabled on the repository.
  3. *build* — one job per image, the five in parallel. Each job recovers the release state
     from GitHub, writes the release manifest into its working tree (see runtime visibility
     below), builds its image as a **multi-platform image for `linux/amd64` and
     `linux/arm64`**, pushes it as `ghcr.io/ebispot/<image>:vX.Y.Z` with OCI
     `version`/`revision`/`source` labels, and saves its manifest-list digest as a release
     asset. A job whose digest asset already exists builds nothing, so re-running the failed
     jobs rebuilds only what is missing, and the jobs pass nothing through the runner. Layers
     are cached in the registry (`:release-buildcache`), which unlike the Actions cache survives
     the weeks between releases; the architecture-neutral stages also read the `dev` builds'
     Actions cache. Docker Desktop on a Mac or on Windows then pulls the matching architecture
     from the same tag.
  4. *finish and promote* — upload `release-manifest.json` (version, date, notes, DOI, image
     digests) and an enriched source tarball; then, **only if `stable` still points at this
     SHA**, re-tag the five recorded digests as `:stable`. Promotion is digest-only (no rebuild)
     and happens after all five images exist, so `:stable` is never a mixed set and an old re-run
     can never move it backwards.
- **`docker.yml` no longer publishes on pushes to `stable`.** The `:stable` tags are owned by the
  release job alone. Pushes and PRs to `dev` keep publishing `:dev` and `:<sha>` exactly as now.
- **The version is visible at runtime without network calls.** The build step writes
  `oxo2-release.json` (current version, release date, release URL, DOI, concept DOI, history) into
  `oxo2-backend/src/main/resources/` and `oxo2-frontend/public/` before building, and copies it
  into the dataload images at `/opt/oxo/oxo2-release.json`. The backend serves it at
  `GET /api/v2/release`; the frontend footer shows the version linking to the release notes. The
  checked-in copies hold `"current": null`, so dev and local builds read "Development build".
  [ADR-0051](0051-deep-health-endpoint-for-traffic-manager-routing.md)'s health endpoint is
  untouched: it says whether the stack is up, this says what it is.
- **Data releases stay separate and unversioned.** A data release is one run of the HPC
  pipeline producing `solr-data.tar.gz` (ADR-0050); it creates no tag, no GitHub release and no
  version bump, and is identified by its run date and by the software version of the `:stable`
  image it pulled. To make that traceable, the dataload's `archive` stage copies the image's
  bundled `oxo2-release.json` next to the tarball, so an index on NFS or in a cluster can be tied
  to the software that built it.
- **Deployment stays separate.** The production chart's default stays `imageTag: stable` with
  `imagePullPolicy: Always` (ADR-0050); the Jenkins deploy job may instead pin
  `--set imageTag=vX.Y.Z` from the release, which is the recommended way to deploy an exact
  release and to roll back to one.
- **After a release, merge `stable` back into `dev`.** The PR merge commit exists only on
  `stable`; the back-merge restores "`stable` is an ancestor of `dev`" and is an ordinary `dev`
  push (it publishes `:dev`, never a release). A hotfix that cannot wait for `dev` is a branch off
  `stable` merged by PR into `stable` with `release:patch`, then back-merged the same way.
- **Recovery is "re-run the original workflow run", never "make another release".** The state
  comment, the tag and the saved assets let every step resume; an unfinished release blocks the
  next one until it is re-run to completion.

## Consequences

- **Every production artefact becomes traceable.** A deployment answers `GET /api/v2/release`, a
  `solr-data.tar.gz` sits next to the manifest of the software that built it, and every image
  carries the version in its OCI labels. Release notes and a DOI exist for each version.
- **The prod HPC dataload and the production chart change nothing** and gain a guarantee: because
  `:stable` moves only after all five versioned images exist, the digest probe in `loadData.hpc`
  can no longer pull an `oxo2-nextflow` from a different commit than the serving backend.
- **One-time repository setup is required before the first release**, by someone with admin
  rights: create the `release:patch`/`release:minor`/`release:major` labels; protect `stable` so it
  changes only by pull request; merge the release workflow into `dev` first, because the built-in
  `GITHUB_TOKEN` may not create releases from a workflow file that differs from the default
  branch's; and enable `EBISPOT/oxo2` in Zenodo's GitHub integration (or set `"archiving": "none"`
  until that is done). The first archived release starts a fresh DOI series; no existing OxO DOI
  is reused.
- **Releases serialise.** Pushes to `stable` are queued, not cancelled; an unfinished release
  blocks the next; do not merge a second release PR until the previous run has finished.
- **The software version and the Maven version diverge, deliberately.** Tags start at `2.0.0`
  while the reactor stays `1.0.0-SNAPSHOT`; the tag and the manifest are the version, the Maven
  string is a build identifier that nothing outside the reactor consumes (no module is published
  to a Maven repository). Two alternatives were rejected: committing version bumps on release
  (a second commit after the merge, so the tested SHA would no longer be the released SHA, plus
  a `dev` bump and pom conflicts on every back-merge and Dependabot PR), and injecting the
  version at build time via a `${revision}` property with fixed jar names (sound, but it touches
  every pom and the ~20 scripts and Dockerfiles that hard-code `oxo2-*-1.0.0-SNAPSHOT.jar`,
  which is not worth doing for a cosmetic mismatch). `firstVersion` is a one-line edit to
  `config.json`, free until the first tag exists.
- **Release images are built for two architectures, which touches the Dockerfiles.** The
  dataload Dockerfiles select the Nemo tarball by `TARGETARCH` (`amd64` → `x86_64`, `arm64` →
  `aarch64`) instead of hard-coding one, and their Maven builder stages run on `$BUILDPLATFORM`
  so the jars are compiled once natively rather than twice under emulation; only the thin
  runtime stages are built per architecture (under QEMU for arm64 on the x86 runner).
  `Dockerfile.solr` downloads and unpacks Solr, pure Java, in a `$BUILDPLATFORM` stage for the
  same reason. The frontend image has no native stage to hoist work into: it runs `vite build`
  when the container starts, because the public base URL is injected at runtime, so its `npm ci`
  and Caddy install produce architecture-specific files the running container needs and are
  built under emulation. That makes the frontend the slowest image and, since the first release
  built the five images one after another in one job (over forty minutes), the reason the build
  is now one parallel job per image: a release takes about as long as its slowest image. The `:dev`
  and `:<sha>` images from `docker.yml` stay `linux/amd64`: they are consumed by the dev cluster
  and the dev HPC dataload, both x86_64, and doubling every push's build time buys nothing there.
  Local development on a Mac builds natively through `docker compose`, which is unaffected.
- **A direct push to `stable` still releases**, with a patch bump and generated notes, because
  the job keys off the push, not the PR. Branch protection is what makes the label and the notes
  reviewable; it is part of the setup above.
- **ADR-0050 is refined, not superseded.** "Cutting a release is a manual act" remains true of the
  one gesture (merge the PR); everything after it is now automated, and "release pinning is the
  branch itself" becomes "the tag, with the branch as its mutable alias".
- **Affects** CI (`.github/workflows/release.yml`, `.github/release/`, `docker.yml`, the PR
  template, `.zenodo.json`), `oxo2-backend` (`ReleaseController`, bundled manifest),
  `oxo2-frontend` (footer, bundled manifest), `oxo2-dataload` (`TARGETARCH`-selected Nemo and
  manifest `COPY` in the Dockerfiles, the native Solr download stage in `Dockerfile.solr`, the
  `archive` stage), and the operator docs
  (`docs/releases.md`, `docs/hpc-data-release.md`).
