# Worktree test fixtures

Small, committed SSSOM slices so a worktree can run a real dataload of a *few* mapping sets instead of
the full corpus. Wired up by the repo-root [`oxo-config-test.json`](../../oxo-config-test.json), which
is the default `$OXO2_CONFIG` for every worktree (see `oxo2-env.sh`). Referenced by repo-relative
`url`, resolved against the config file's directory — see
[ADR-0039](../../docs/adr/0039-repo-relative-fixtures-and-single-gzip-in-downloader.md).

The EFO export is split into two mapping sets by **subject obsolescence** — one for live terms and one
for obsolete terms — so a worktree can exercise how obsolete subjects are handled end to end.

## What's here

| File | Ontology | Rows | Notes |
|------|----------|------|-------|
| `efo.ols.sssom.tsv`          | EFO (live)     | 619  | Non-obsolete subjects. 19 strong (9 `skos:exactMatch` + 3 `owl:equivalentClass` + 7 `rdfs:subPropertyOf`) + 300 `rdfs:subClassOf` + 300 `oboInOwl:hasDbXref` sample. |
| `efo.ols.obsolete.sssom.tsv` | EFO (obsolete) | 346  | Obsolete subjects. 46 strong (42 `skos:exactMatch` + 4 `skos:closeMatch`) + 300 `oboInOwl:hasDbXref` sample. The export has no `rdfs:subClassOf`. |
| `mondo.sssom.tsv`            | MONDO          | 2417 | 1778 `skos:exactMatch` (incl. 31 MONDO→EFO cross-set bridges) + weak sample. |

Each slice keeps the full SSSOM metadata header (needed for the `curie_map` / prefix expansion), the
column header, **every** strong/semantic mapping row (`skos:*Match`, `owl:equivalentClass`,
`rdfs:subPropertyOf` — the predicates that drive cross-set inference), and a bounded sample (default
300 each) of the two weak predicates that dominate an OLS export (`oboInOwl:hasDbXref`,
`rdfs:subClassOf`, hidden by default in normal search) so the weak-predicate toggles still have
something to reveal.

The live and obsolete EFO subject sets are disjoint. The MONDO→EFO exactMatch bridges make the fixture
produce genuine cross-set inference: two of those bridge targets (`EFO:1000466`, `EFO:1000592`) carry
their own onward `skos:exactMatch` to NCIt, yielding two multi-hop MONDO→EFO→NCIt chains. Both live in
`efo.ols.sssom.tsv`.

These are a snapshot of an OLS SSSOM export, not a live download.

## Regenerating

The source files are the per-ontology members of OLS's `sssom.tgz`. For MONDO that is the single
`mondo.ols.sssom.tsv`; for EFO the loader splits the export into `efo.ols.sssom.tsv` (live terms) and
`efo.ols.obsolete.sssom.tsv` (obsolete terms). They are multi-MB full exports and are **not** committed.
To refresh the slices from a directory of exports:

```bash
./trim-fixtures.sh /path/to/ols/exports        # or set OXO2_OLS_SSSOM
WEAK_SAMPLE=500 ./trim-fixtures.sh              # override rows per weak predicate
```

For the two EFO fixtures the committed basename equals the source basename, so `trim-fixtures.sh`
refuses to run when `SOURCE_DIR` points at this directory — keep the untrimmed exports elsewhere.

## The Mapping Commons sets

`oxo-config-test.json` also pulls one curated set live: `uniprot` from
`mapping-commons/ebi-text-mappings` (52 KB plain `.tsv`).

A second candidate, the biopragmatics `priority.sssom.tsv.gz` (Zenodo record 15826794), is **not**
included by default. It is 2.6M `skos:exactMatch` gene rows (569 MB decompressed) — the gene landscape,
with no overlap with these disease ontologies and one giant exactMatch component (the cross-set
closure explosion [ADR-0017](../../docs/adr/0017-cross-set-inference-corpus-component-size-guard.md)
warns of). The downloader *can* ingest it — a single `.tsv.gz` `url` is gunzipped automatically
(ADR-0039) — so to opt in, add:

```json
{ "id": "priority", "url": "https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz", "category": "curated" }
```

and expect a much heavier load (raise `SOLR_HEAP`). For a curated set that actually overlaps MONDO /
EFO, prefer a biopragmatics disease landscape instead of the gene one.
