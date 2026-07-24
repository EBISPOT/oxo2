# Worktree test fixtures

Small, committed SSSOM slices so a worktree can run a real dataload of a *few* mapping sets instead of
the full corpus. Wired up by the repo-root [`oxo-config-test.json`](../../oxo-config-test.json), which
is the default `$OXO2_CONFIG` for every worktree (see `oxo2-env.sh`). Referenced by repo-relative
`url`, resolved against the config file's directory — see
[ADR-0039](../../docs/adr/0039-repo-relative-fixtures-and-single-gzip-in-downloader.md).

## What's here

| File | Ontology | Rows | Notes |
|------|----------|------|-------|
| `efo.sssom.tsv`    | EFO    | 665  | 51 exactMatch + weak sample |
| `mondo.sssom.tsv`  | MONDO  | 2417 | 1778 exactMatch (incl. 31 MONDO→EFO cross-set bridges) + weak sample |
| `uberon.sssom.tsv` | UBERON | 600  | weak predicates only (`hasDbXref`/`subClassOf`) |

Each slice keeps the full SSSOM metadata header (needed for the `curie_map` / prefix expansion), the
column header, **every** strong/semantic mapping row (`skos:*Match`, `owl:equivalentClass`,
`rdfs:subPropertyOf` — the predicates that drive cross-set inference), and a bounded sample (default
300 each) of the two weak predicates that dominate an OLS export (`oboInOwl:hasDbXref`,
`rdfs:subClassOf`, hidden by default in normal search) so the weak-predicate toggles still have
something to reveal. The MONDO↔EFO exactMatch overlap makes the fixture produce genuine cross-set
inferences, including a couple of multi-hop chains.

These are a snapshot of an OLS SSSOM export, not a live download.

## Regenerating

The source files are the per-ontology members of OLS's `sssom.tgz` (`<ontology>.ols.sssom.tsv`). They
are multi-MB full exports and are **not** committed. To refresh the slices from a directory of exports:

```bash
./trim-fixtures.sh /path/to/ols/exports        # or set OXO2_OLS_SSSOM
WEAK_SAMPLE=500 ./trim-fixtures.sh              # override rows per weak predicate
```

## The Mapping Commons sets

`oxo-config-test.json` also pulls one curated set live: `uniprot` from
`mapping-commons/ebi-text-mappings` (52 KB plain `.tsv`).

A second candidate, the biopragmatics `priority.sssom.tsv.gz` (Zenodo record 15826794), is **not**
included by default. It is 2.6M `skos:exactMatch` gene rows (569 MB decompressed) — the gene landscape,
with no overlap with these disease/anatomy ontologies and one giant exactMatch component (the cross-set
closure explosion [ADR-0017](../../docs/adr/0017-cross-set-inference-corpus-component-size-guard.md)
warns of). The downloader *can* ingest it — a single `.tsv.gz` `url` is gunzipped automatically
(ADR-0039) — so to opt in, add:

```json
{ "id": "priority", "url": "https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz", "category": "curated" }
```

and expect a much heavier load (raise `SOLR_HEAP`). For a curated set that actually overlaps MONDO /
UBERON, prefer a biopragmatics disease or anatomy landscape instead of the gene one.
