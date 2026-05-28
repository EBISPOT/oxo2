# ADR-0007: GitHub mapping registries are fetched via archive tarball, not the Contents API

- **Status**: Accepted
- **Date**: 2026-05-28

## Context

For each `github_repository` registry, the downloader listed the configured directory by calling the
GitHub Contents REST API (`GET https://api.github.com/repos/{owner}/{repo}/contents/{directory}`) and
then fetched each listed file from its `raw.githubusercontent.com` URL.

The listing call is the problem. Unauthenticated `api.github.com` is rate-limited to 60 requests per
hour per IP. On EBI HPC many users share one outbound NAT egress IP, so the quota is exhausted
quickly and unpredictably. When the quota is gone the API returns a JSON *object*
(`{"message": "API rate limit exceeded ...", ...}`) where the code expects a JSON *array*, and the
download fails with a Jackson `MismatchedInputException`. The failure is intermittent (depends on
other tenants behind the same IP) and affected every GitHub registry at once, while non-GitHub
registries (direct `url`/`ftp_server`) were unaffected.

Authenticating the API with a token would raise the limit but introduces secret management we want to
avoid, and still couples the dataload to a quota. We want the GitHub dependency off the API entirely.

## Decision

GitHub registries are fetched as the repository's **default-branch archive tarball over plain HTTP**:
`https://github.com/{owner}/{repo}/archive/HEAD.tar.gz` (which 302-redirects to `codeload.github.com`
and resolves `HEAD` to the default branch server-side, so `main` vs `master` need not be known). The
downloader streams the tarball to a temp file and extracts **only the configured `directory`**,
flattened to basenames, into the registry's destination. No `api.github.com` call and no token are
involved. The shared `TgzExtractor` utility performs the extraction with the existing per-segment
`SafeFilename` validation and canonical-path containment guard.

## Consequences

- GitHub registry downloads no longer depend on the GitHub API quota; the shared-NAT rate-limit
  failure mode is gone.
- Only the configured `directory` is extracted. This is required, not cosmetic: archives contain
  repo-root files (e.g. `mappings.yml`, `.github/workflows/*.yml`) that the recursive `**.tsv` /
  `**.yml` globs in `sssom2json.nf` would otherwise ingest.
- One bulk tarball per repo is downloaded instead of N per-file requests — more raw bytes, but a
  single un-throttled HTTP fetch. The downloader streams to a temp file and extracts by streaming;
  it never buffers the whole archive in memory.
- There is no `branch`/`ref` config field: `HEAD` always tracks the default branch. If a future
  registry needs a non-default ref, add an optional field then.
- `git` is deliberately **not** required at runtime (it is not in `Dockerfile.dataload`), which is
  why a tarball fetch is preferred over `git clone`.
- The GitHub and HTTP downloaders now share `TgzExtractor`, so the tar-extraction security guard
  lives in one place. Changes to it affect both paths; keep its tests
  (`TgzExtractorTest`, `HTTPDowloaderExtractTgzTest`) green.
