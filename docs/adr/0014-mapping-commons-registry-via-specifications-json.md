# ADR-0014: Mapping Commons is ingested via its aggregated `mapping-specifications.json`

- **Status**: Accepted
- **Date**: 2026-06-09

## Context

[Mapping Commons](https://mapping-commons.github.io/) is the community registry of FAIR mapping
specifications. We want it as a first-class source so OxO2 tracks the full curated catalogue rather
than a hand-maintained list of individual GitHub repositories.

`mapping-commons.github.io` is **not** a mapping repository. Its own `mappings/` directory holds only
two FAIR-schema *transform* TSVs (LinkML slot-mappings, not ontology mappings), so adding it as a
plain `github_repository` (per [ADR-0007](0007-github-registries-via-archive-tarball.md)) would ingest
the wrong data. It is a **registry of registries**: `mapping-server.yml` lists seven remote
registries (on GitHub *and* GitLab — e.g. biopragmatics, monarch, c-path), each with its own
`registry.yml`/`mappings.yml` pointing at SSSOM TSVs.

Crucially, the site already publishes the result of that aggregation as a single machine-readable
catalogue: `data/mapping-specifications.json`, a flat JSON array of mapping-set entries each carrying
`type`, `status`, `license`, a list of source `registries`, and a `content_url` that resolves
(through w3id / Zenodo / OBO PURL / raw GitHub redirects) to the SSSOM TSV.

Two characteristics of the live catalogue shape the decision:

- Filenames collide. The same basename appears under different registries (e.g.
  `mondo_hasdbxref_hp.sssom.tsv` in both Monarch and C-Path), and biopragmatics lists several Zenodo
  *versions* of the same file (5× `priority`/`processed`/`raw`).
- The biopragmatics `processed`/`raw` views are the SeMRA-assembled corpus whose 685k-node
  `exactMatch` component drives the phase-2 cross-set closure explosion
  ([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)). The curated `priority` view
  is the safe subset (though still ~600 MB decompressed; a component-size guard on the phase-2 pass
  remains separate, pending work).

## Decision

Add a fourth registry source type, `mapping_commons_registry`, whose value is the URL of an aggregated
`mapping-specifications.json` catalogue. A new `MappingCommonsRegistryDownloader` reads the catalogue
(no per-repo crawl, no GitLab special-casing — the site already did that aggregation) and downloads
the SSSOM mapping sets it selects.

Selection (the pure, unit-tested `select()` step) keeps an entry iff:

- `type == "sssom"` and `content_url` is non-empty;
- it does **not** belong to the FAIR-transform registry `https://w3id.org/mapping-commons/transforms`
  (those are slot-mappings, not ontology mappings);
- its `content_url` basename is not in the registry entry's optional `exclude` list.

Surviving entries are then **namespaced** into a per-source-registry subdirectory (so cross-registry
filename clashes stay distinct). Distinct sets that still share a filename *within* a registry are all
kept and **disambiguated** by a per-set subdirectory: the biopragmatics SeMRA landscapes (gene, cell,
protein, anatomy, disease) each publish a `priority.sssom.tsv.gz` under a *different* Zenodo record, so
they are different content — not versions.

The aggregated catalogue drops each set's `mapping_set_group`, so to name those subdirectories
meaningfully (`mapping-registry/gene/priority.sssom.tsv` rather than
`mapping-registry/15826794/priority.sssom.tsv`) the downloader does a **targeted, best-effort lookup**
of the owning source `registry.yml`: for each colliding group it derives the GitHub raw URL from the
entry's `registries[].id`, fetches `registry.yml` (then `mappings.yml`), and reads `mapping_set_group`
per `mapping_set_id` (which equals the `content_url`). The lookup runs *only* for colliding groups, and
any failure (non-GitHub registry, 404, unparseable YAML) degrades silently to the Zenodo record id — it
never blocks a download. This is the one place the design reaches past the aggregated catalogue back to
a source registry; it adds a `jackson-dataformat-yaml` dependency. Only an **exact-duplicate
`content_url`** (the same URL listed under two registries) is collapsed to a single download. Gzipped
entries (`*.gz`) are decompressed on download so the `.tsv` lands where the downstream `sssom2json.nf`
`**.tsv` glob (which recurses) can see it. Every on-disk path segment is validated against
[`SafeFilename`](../../oxo2-dataload/oxo2-downloader/src/main/java/uk/ac/ebi/spot/oxo/downloader/util/SafeFilename.java).

The default `oxo-config.json` replaces the five individually-listed `mapping-commons/*` GitHub
registries with a single `mapping_commons_registry` entry pointing at
`https://mapping-commons.github.io/data/mapping-specifications.json`, with `exclude` set to
`processed.sssom.tsv.gz`, `raw.sssom.tsv.gz`, and `mappings.sssom.tsv.gz` to keep the
closure-exploding SeMRA assemblies out of the corpus while still ingesting the biopragmatics
`priority`/`biomappings`/`bioregistry` sets.

## Consequences

- One config entry now tracks the whole Mapping Commons catalogue (≈51 SSSOM sets across mouse-human,
  monarch, c-path, ebi-text, disease, and the curated biopragmatics views), and auto-grows as the
  registry does — no per-repo edits.
- The corpus broadens beyond the `mapping-commons` GitHub org to whatever the registry aggregates
  (currently also monarch-initiative, biopragmatics, and c-path on GitLab).
- **Coverage dropped on purpose.** The catalogue does *not* list `microbial-trait-mappings`,
  `lmha-obo-mappings`, or `phenotype`, which were previously configured individually. Replacing the
  individual entries with the registry therefore removes those three sets. Re-add them as explicit
  `github_repository` entries if they are still wanted.
- `mapping-specifications.json` is published by an "under construction" server; the downloader logs
  and skips malformed/empty/unsafe entries and a failed catalogue fetch aborts only that registry,
  not the whole dataload.
- Both the catalogue fetch and each mapping-set download **retry transient failures** (HTTP
  429/500/502/503/504 and network-level I/O errors) with exponential backoff (4 attempts, 2 s→30 s);
  permanent failures (e.g. 404) are not retried. This matters because the large gzipped Zenodo views
  (e.g. the ~596 MB biopragmatics `priority`) intermittently return `504 Gateway Timeout` while the
  file is staged — a single attempt would silently drop the set. After exhausting retries the set is
  logged and skipped, leaving the rest of the corpus intact.
- The `exclude` list is the visible, reviewable guard against the phase-2 closure explosion. It is a
  blunt basename filter; the per-component size guard on the phase-2 pass (see project notes) is the
  complementary, still-pending safeguard.
- New maintenance coupling: `MappingCommonsRegistryDownloader.select()` assumes the catalogue's
  field names (`type`, `content_url`, `registries[].id`). Keep its tests
  (`MappingCommonsRegistryDownloaderTest`) green if that schema drifts.
