# Worktree test fixtures

Small, committed SSSOM slices so a worktree can run a real dataload of a *few* mapping sets instead of
the full corpus. Wired up by the repo-root [`oxo-config-test.json`](../../oxo-config-test.json), which
is the default `$OXO2_CONFIG` for every worktree (see `oxo2-env.sh`). Referenced by repo-relative
`url`, resolved against the config file's directory — see
[ADR-0039](../../docs/adr/0039-repo-relative-fixtures-and-single-gzip-in-downloader.md).

The EFO and MONDO exports are each split into two mapping sets by **subject obsolescence** — one for
live terms and one for obsolete terms — so a worktree can exercise how obsolete subjects are handled end
to end. A fifth slice, `hca.sssom.tsv`, carries the opposite kind of subject: free text with no
identifier at all (see below). Each obsolete set carries `"obsolete": true` in `oxo-config-test.json`, which drives the
obsolete-terms
feature ([ADR-0041](../../docs/adr/0041-obsolete-terms-endpoint-property-hidden-by-default.md)): the
dataload stamps `subject_obsolete`/`object_obsolete` on every mapping (so a live MONDO→EFO row whose EFO
object is obsolete is flagged too) and `obsolete` on the set and its entities, and the frontend hides
them by default behind a "Show obsolete terms" switch.

## What's here

| File | Ontology | Rows | Notes |
|------|----------|------|-------|
| `efo.ols.sssom.tsv`            | EFO (live)       | 619  | Non-obsolete subjects. 19 strong (9 `skos:exactMatch` + 3 `owl:equivalentClass` + 7 `rdfs:subPropertyOf`) + 300 `rdfs:subClassOf` + 300 `oboInOwl:hasDbXref` sample. |
| `efo.ols.obsolete.sssom.tsv`   | EFO (obsolete)   | 377  | Obsolete subjects. 46 strong (42 `skos:exactMatch` + 4 `skos:closeMatch`) + 331 `oboInOwl:hasDbXref` (300 sample + 31 hand-added self-reference rows so the 31 EFO terms that appear only as obsolete MONDO objects are registered as obsolete subjects). The export has no `rdfs:subClassOf`. |
| `mondo.ols.sssom.tsv`          | MONDO (live)     | 1128 | Non-obsolete subjects. 528 strong (490 `skos:exactMatch` incl. 23 MONDO→EFO cross-set bridges + 33 `skos:relatedMatch` + 2 `skos:narrowMatch` + 1 `skos:broadMatch` + 2 `rdfs:subPropertyOf`) + 300 `rdfs:subClassOf` + 300 `oboInOwl:hasDbXref` sample. |
| `mondo.ols.obsolete.sssom.tsv` | MONDO (obsolete) | 1587 | Obsolete subjects. 1287 strong (1286 `skos:exactMatch` incl. 9 MONDO→EFO cross-set bridges + 1 `skos:relatedMatch`) + 300 `oboInOwl:hasDbXref` sample. The export has no `rdfs:subClassOf`. |
| `hca.sssom.tsv`                | — (literal subjects) | 209 | **Literal mappings**: free text with no `subject_id` at all, `skos:closeMatch` to CL / UBERON / EFO / MONDO. See below. |

### `hca.sssom.tsv` — the literal-subject fixture

The other four fixtures all have identified subjects. This one has none: it is a trimmed slice of the
Human Cell Atlas curated mappings, whose subjects are strings of free text that have no CURIE assigned
yet (`subject_type: rdfs literal`, declared once in the set metadata — the file has **no `subject_id`
column**). It is here so a worktree can exercise
[ADR-0042](../../docs/adr/0042-literal-subject-identity-in-spo-key.md), which keys such a subject's
mapping group on its text rather than on an absent id.

The trim first drops rows that differ *only* in `subject_category`: `mapping_id` hashes the set and the
subject/predicate/object slots but not that column, so those rows collapse into one document at
`sssom2json` and would pad the file without adding anything to the index. Of the 1444 that remain it
keeps:

| Rows | Kept because |
|------|--------------|
| 120  | An even sample across the remainder, for bulk. |
|  77  | They belong to a (predicate, object) group of 21 or more — the rows that overflow the 21-member display cap. The largest is 34 free-text subjects on `skos:closeMatch CL:0011020`. |
|  12  | Their object is also a **subject** in one of the EFO / MONDO fixtures, so a literal mapping meets the rest of the corpus. One of those objects (`MONDO:0015144`) is an obsolete subject, which stamps `object_obsolete` on a literal mapping (ADR-0041). |

Every kept row is one document, and those 209 documents form **113** mapping groups before ADR-0042 and
**209** after — the gap is the conflation, and the 34-member group is what rendered as "+13 more not
shown". What this fixture does *not* exercise is the other half of ADR-0042, that the same text asserted
in two sets still collapses into one row: no literal triple here is shared with `uniprot.sssom.tsv`, the
only other set with literal subjects. The `literal-subject` integration fixture
(`testcases/minimal/crossset/`) covers that case.

Regenerate by re-running the same rule over the full file at
`https://raw.githubusercontent.com/mapping-commons/ebi-text-mappings/main/mappings/hca.sssom.tsv`
(1505 rows); it is not produced by `trim-fixtures.sh`, which is specific to the OLS exports and their
obsolescence split.

Each slice keeps the full SSSOM metadata header (needed for the `curie_map` / prefix expansion), the
column header, **every** strong/semantic mapping row (`skos:*Match`, `owl:equivalentClass`,
`rdfs:subPropertyOf` — the predicates that drive cross-set inference), and a bounded sample (default
300 each) of the two weak predicates that dominate an OLS export (`oboInOwl:hasDbXref`,
`rdfs:subClassOf`, hidden by default in normal search) so the weak-predicate toggles still have
something to reveal.

Each ontology's live and obsolete subject sets are disjoint. The MONDO→EFO exactMatch bridges make the
fixture produce genuine cross-set inference: 23 live-MONDO and 9 obsolete-MONDO subjects `skos:exactMatch`
an EFO term. Two of the obsolete-MONDO bridges (`MONDO:0006361`, `MONDO:0006465`) target `EFO:1000466` /
`EFO:1000592` — live EFO subjects that carry their own onward `skos:exactMatch` to NCIt (both in
`efo.ols.sssom.tsv`) — yielding two multi-hop obsolete-MONDO→EFO→NCIt chains that also exercise how an
obsolete subject still bridges the closure (the replacement-discovery case, ADR-0041). Most of the 23
live-MONDO bridges instead target *obsolete* EFO terms, so those live-MONDO→obsolete-EFO rows are stamped
`object_obsolete` — object-side obsolescence that can only be known globally, once EFO's obsolete export
is seen.

These are a snapshot of an OLS SSSOM export, not a live download.

## Regenerating

The source files are the per-ontology members of OLS's `sssom.tgz`, each split by subject obsolescence
into a live export (`<ont>.ols.sssom.tsv`) and an obsolete-terms export (`<ont>.ols.obsolete.sssom.tsv`)
— for both EFO and MONDO. They are multi-MB full exports and are **not** committed. To refresh the slices
from a directory of exports:

```bash
./trim-fixtures.sh /path/to/ols/exports        # or set OXO2_OLS_SSSOM
WEAK_SAMPLE=500 ./trim-fixtures.sh              # override rows per weak predicate
```

For all four fixtures the committed basename equals the source basename, so `trim-fixtures.sh`
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
