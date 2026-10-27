# OxO2 software releases

The process decided in [ADR-0052](adr/0052-versioned-software-releases-from-stable.md).

Every push to `stable` runs the checks and, when they pass, the same run creates **one numbered
software release**: a `vMAJOR.MINOR.PATCH` tag, a GitHub release with notes and a Zenodo DOI,
versioned container images, and finally the `:stable` image aliases. Merging `stable` back into
`dev`, feature branches and `dev` pushes never create a release.

Two things are deliberately **not** a software release:

- **A data release** is a run of the HPC dataload producing `solr-data.tar.gz`
  ([hpc-data-release.md](hpc-data-release.md)). It is identified by its run date and by the
  software version of the `:stable` image it pulled, creates no tag and bumps no version. The
  pipeline copies the image's bundled `oxo2-release.json` next to the tarball so the two can be
  tied together later.
- **A deployment** is the separate Jenkins job that copies a tarball to the clusters and
  redeploys the chart. It consumes a release; it does not make one.

## Releasing

1. **Open a pull request from `dev` into `stable`.** Append `?template=stable-release.md` to the
   GitHub compare URL to start from the release template, which has a `## Release notes` section
   with Added / Changed / Fixed headings.
2. **Write the release notes** in that section: what users and operators will notice, breaking
   changes, migration steps. If you leave the section out or untouched, notes are generated from
   the commit subjects since the previous release, grouped by conventional-commit type (`feat:` →
   Added, `fix:` → Fixed, the rest → Changed, dependency bumps collapsed into one line).
3. **Pick the bump with one label**: `release:patch` (default if you add none), `release:minor`
   or `release:major`. Two bump labels fail the release instead of choosing one.
4. **Merge.** Version selection, tagging, publication, archiving, image builds and the `:stable`
   promotion all follow from the push. Watch the run under *Actions*; its summary links the
   release.
5. **Wait for the whole run to finish before relying on it.** A published GitHub release may
   still be waiting for its DOI or its images; its body says "Container images are being prepared"
   until they are done, and `:stable` does not move until then.
6. **Merge `stable` back into `dev`** (a plain PR or push; it releases nothing). This keeps
   `stable` an ancestor of `dev`, which the next release's ordering check relies on.
7. **Deploy and load data as before**: the prod HPC dataload pulls `:stable` (see
   [Cutting a Production Release](hpc-data-release.md#cutting-a-production-release)), and the
   Jenkins deploy job redeploys the chart. To deploy an exact release, or to roll back to
   one, pass `--set imageTag=vX.Y.Z`; the chart's default stays `stable`.

### Hotfixes

A fix production needs before `dev` is ready: branch from `stable`, open the PR into `stable`
with `release:patch`, merge, then merge `stable` back into `dev` so the fix is not lost.

## Versions

Versions are `MAJOR.MINOR.PATCH`: patch for compatible fixes, minor for compatible features, major
for breaking changes to the API, the index layout or the deployment contract. The first release
is `firstVersion` from `.github/release/config.json`, `2.0.0` (OxO2 is the second OxO); from
then on the previous version is the highest existing `vX.Y.Z` tag.

Maven modules, the npm package and the Helm chart keep their existing identifiers
(`1.0.0-SNAPSHOT`, `0.0.0`, `0.1.0`/`"1.0"`). The git tag and the release manifest identify the
software release, and the `oxo2-*-1.0.0-SNAPSHOT.jar` names stay valid in the Dockerfiles.

## What a release produces

| Artefact | Where |
|---|---|
| Lightweight tag `vX.Y.Z` at the tested commit | the repository |
| GitHub release "OxO2 software X.Y.Z" with notes, DOI line and a hidden state comment | *Releases* |
| Zenodo software record and version DOI (one concept DOI for the whole series) | zenodo.org, via the GitHub integration |
| `ghcr.io/ebispot/oxo2-{dataload,nextflow,backend,frontend,solr}:vX.Y.Z`, multi-platform (`linux/amd64` and `linux/arm64`, so Docker Desktop on Mac and Windows pulls the right one), OCI `version`/`revision`/`source` labels | GHCR |
| `image-<component>.json` (one digest each), `release-manifest.json`, `oxo2-vX.Y.Z-source.tar.gz` | release assets |
| `:stable` aliases re-pointed at the five recorded digests | GHCR, last step only |

The enriched source tarball is the tagged source plus `RELEASE_NOTES.md`, the manifest and a
`.zenodo.json` with the DOI, version and date filled in. It is distinct from the plain source ZIP
Zenodo archives itself.

## Website and API

Versioned images carry `oxo2-release.json`: the current version, release date and URL, DOI,
concept DOI and release history. The backend serves it at `GET /api/v2/release`; the frontend
footer shows the version and links to the release notes. Neither makes GitHub or Zenodo requests
at runtime. Development and local builds, whose checked-in copies hold `"current": null`, show
"Development build" and no DOI. The data content and its load date remain what the landing page
reports from the index ([ADR-0043](adr/0043-data-content-summary-on-landing-page.md)); they are
independent of the software release date.

## One-time setup

Needed once, by someone with admin rights on `EBISPOT/oxo2`, before the first release:

- **Labels**: create `release:patch`, `release:minor` and `release:major`.
- **Protect `stable`** so it changes only through reviewed pull requests. The release job keys
  off the push, so a direct push would still release, with a patch bump and generated notes.
- **Merge the release workflow into `dev` first.** GitHub restricts release creation by the
  built-in `GITHUB_TOKEN` when the workflow file differs from the default branch's; `dev` is the
  default branch. `GITHUB_TOKEN` needs `contents: write` and `packages: write`, which the workflow
  declares.
- **Zenodo**: connect a GitHub account in Zenodo and enable `EBISPOT/oxo2`. `.zenodo.json` is the
  single archival metadata source (title, description, creators, licence, keywords) and
  deliberately omits DOI, version and date, which Zenodo supplies per release. There is no Zenodo
  API token anywhere: the native webhook archives the tagged source and the workflow only reads
  public records to find the DOI. Until the integration is enabled, set `"archiving": "none"` in
  `.github/release/config.json`; releases then skip the DOI wait. Grant a second maintainer
  management access to the Zenodo record after the first release.

## Publication and recovery

*Prepare* verifies the checked-out SHA is on `stable`, that no earlier release is unfinished and
that release order follows `stable` history, chooses the version and notes, and creates a draft
GitHub release whose body ends in a hidden JSON state comment. That comment is the durable state:
every later step, and every re-run, reads it.

*Archive* creates the tag at the tested SHA and publishes the draft. Publication is the only thing
that triggers Zenodo. The workflow polls Zenodo's public API for a software record whose related
identifier is this exact release URL and whose version matches, for up to `archiveWaitSeconds`
(30 minutes); ambiguous matches fail rather than pick a DOI, and after the first release the
concept DOI must match the previous release's.

*Build* runs as five parallel jobs, one per image. Each job recovers the release state from
GitHub (`release.py prepare-image`), writes `oxo2-release.json` into the backend, frontend and
dataload trees, builds its image for `linux/amd64` and `linux/arm64`, pushes it as `:vX.Y.Z` and
saves the digest as a release asset. A job whose component already has its digest asset builds
nothing, so re-running the failed jobs rebuilds only the images that are missing; the jobs share
nothing through the runner. The build layers are cached in
`ghcr.io/ebispot/oxo2-<component>:release-buildcache` from one release to the next, and the
architecture-neutral stages (Maven, npm) also read the Actions cache that the `dev` builds refresh
on every push. The whole build takes about as long as the slowest image, the frontend, whose
`npm ci` and Caddy install run under emulation for arm64 because the container compiles the site
when it starts.

*Finish* uploads the manifest and the enriched source tarball and marks the release complete.
*Promote* then re-tags the recorded digests as `:stable`, but only if `stable` still points at this
SHA; otherwise the versioned release stands and the aliases are left for the newer run.

**If anything fails, re-run the failed jobs of the original run.** The state comment, the tag and
the assets recover the same version, DOI and finished images; a failed image build re-runs on its
own and the finish job then picks up the digests the other builds recorded. Do not create another
release, and do not delete or edit the tag, the state comment or the assets to get past a pending
release. If the DOI never appears, open `EBISPOT/oxo2` in Zenodo's GitHub settings, inspect that
release's errors, fix the integration, and re-run the original workflow.

Stable pushes are queued (`queue: max`), never cancelled; queue order is arrival order, and the
ancestry check stops an out-of-order publication. Do not merge another release PR until the
previous run has finished.

## Validation

- `python3 -m unittest discover -s .github/release -p 'test_*.py' -v` exercises the lifecycle
  (publication, DOI discovery, successive releases, delayed indexing, timeouts, partial image
  builds, image builds split across runners, lost responses) against fake GitHub and Zenodo
  providers and a temporary git repository.
  It makes no real releases. CI runs it on every push and PR.
- `ReleaseControllerTest` (`mvn -pl oxo2-backend test`) covers the development placeholder the
  backend serves and the released manifest shape `release.py` writes.
- End-to-end Zenodo archiving can only be verified by a real release.
