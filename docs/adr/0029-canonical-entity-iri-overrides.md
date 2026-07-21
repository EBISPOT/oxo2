# ADR-0029: Canonical entity-IRI overrides for divergent prefixes

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

OxO2 normalises every entity to a CURIE (upper-cased prefix; see `EntityReference`), and everything
downstream — Solr `_id`, same-SPO grouping (ADR-0023), search — keys on that CURIE. The one place
raw IRIs carry meaning is the N-Quads fed to Nemo (ADR-0010): the rules engine reasons over full
IRIs.

SSSOM sets each ship their own `curie_map`, and different sets legitimately declare the same prefix
with **different IRI stems**. For example, thirteen sets expand `MESH:` to
`http://id.nlm.nih.gov/mesh/` while one uses `http://identifiers.org/mesh/`. A single set's
`curie_map` expands its own CURIEs, so `MESH:D020176` becomes two different IRIs depending on which
set it came from. To Nemo these are two distinct nodes for one entity, so cross-set inference
derives the same conclusion twice, and the explanation reconstruction (ADR-0028) — which folds IRIs
back to one CURIE for display — turns an acyclic Nemo derivation into a self-referential
(circular-looking) proof: a premise, once folded, restates an ancestor conclusion. This produced a
duplicate inferred mapping for `HP:0003231 SKOS:exactMatch MESH:D020176` with a 13-step circular
explanation alongside the correct 2-step one.

A survey of the loaded corpus (via the new `PrefixDivergenceDetector`) found **70 prefixes** whose
sets disagree on the stem, 60 of them genuine host-aliases (UMLS, SCTID/SNOMED, OMIM, ICD10CM, FMA,
MEDGEN, HGNC, MedDRA, SO, MeSH, …); the rest are `curie_map` bugs (double-expanded OBO PURLs,
`unknown_prefix` placeholders). The Bioregistry snapshot (ADR-0015) already offers a preferred IRI
per prefix, but its preferred form is often a third variant we do not want to emit
(`MESH:` → `https://meshb.nlm.nih.gov/record/ui?ui=`), and it is only consulted as a fallback for
sets that ship no `curie_map`.

## Decision

Leave the Bioregistry snapshot as the fallback curie_map it already is (ADR-0015), and add a
separate, curated **override table** (`oxo2-shared/src/main/resources/iri-prefix-overrides.json`,
keyed by upper-case prefix) that pins one canonical IRI stem for known-divergent prefixes.
`EntityReference.toUri` applies an override ahead of the set's own curie_map and the Bioregistry
fallback, and rewrites full IRIs that arrive on a declared alias stem to the canonical one (longest
alias wins) — so an entity always expands to a single IRI regardless of how a source spelled it. The
table is populated from `PrefixDivergenceDetector` evidence and holds, at introduction, thirteen
prefixes: `MESH`, `UMLS`, `SCTID`, `ICD10CM`, `MEDGEN`, `MEDDRA`, `OMIM`, `OMIMPS`, `HGNC`, `FMA`,
`MA`, `EMAPA`, `SO`. The canonical stem for each is its identity IRI (the OBO PURL for OBO-native
prefixes; the standard registry IRI otherwise — e.g. `MESH → http://id.nlm.nih.gov/mesh/`,
`SCTID → http://snomed.info/id/`), chosen to match the form the loaded corpus already uses where that
is known.

`ENSEMBL`, `NCBIGENE` and `CHEBI` were added later (2026-07-21) once they bit — found by scanning the
materialised `curie_map`s and then confirming, against emitted usage in the asserted N-Quads, that
both stems actually reach the data (a check that pared 58 declared-divergent prefixes down to the ~9
that genuinely split; the rest declare inconsistently but only ever emit one form). The three:

- `ENSEMBL` splits three ways — the EBI RDF platform stem `http://rdf.ebi.ac.uk/resource/ensembl/`
  (dominant by row count), the Ensembl-site `https://www.ensembl.org/id/`, and its `http://` variant.
  Canonical is `https://www.ensembl.org/id/`: the standard registry (Bioregistry) IRI and OxO2's own
  bare-set fallback, chosen over the corpus-dominant EBI RDF stem so bare and future sets converge on
  it rather than on an EBI-internal resolver namespace.
- `NCBIGENE` splits between NCBI's own `https://www.ncbi.nlm.nih.gov/gene/` and UniProt's PURL for the
  same gene id `http://purl.uniprot.org/geneid/`. Canonical is the NCBI form — both dominant and
  registry-standard, so no tension.
- `CHEBI` is the case where the *dominant* emitted form is the wrong one: `http://purl.obolibrary.org/obo/chebi/`
  (the ontology-namespace spelling, ~5x the correct form) versus the proper OBO term PURL
  `http://purl.obolibrary.org/obo/CHEBI_`. Canonical is the OBO PURL regardless of count — the
  OBO-native identity rule — with the EBI search-resolver stem folded in as a third alias. As defence-in-depth, the explanation stage (`ExplainInferredMappings`) detects any
explanation whose folded S-P-O restates an ancestor and logs it with the prefix to add, retiring the
prior assumption that "nmo never emits a cycle".

### Why not adopt Bioregistry's preferred IRI as the definitive source?

Bioregistry already ships a preferred IRI per prefix, so the obvious move is to canonicalise every
entity against it. We deliberately do not, for four reasons:

1. **Its preferred form is often a third variant absent from our data.** Bioregistry prefers
   `MESH → https://meshb.nlm.nih.gov/record/ui?ui=`, where our corpus (and OLS) use
   `id.nlm.nih.gov/mesh/`. Adopting Bioregistry wholesale would rewrite `subject_iri`/`object_iri`
   corpus-wide — and in the API — to forms neither the sources nor OLS use, for **every** non-OBO
   prefix, not just the aliased ones.
2. **It carries no signal about which stem our corpus actually uses.** The curated table lets us pick
   the stem already dominant in the loaded data, minimising IRI churn and keeping the entity-details
   cache (keyed on the asserted IRIs) aligned.
3. **It cannot tell an alias from a collision.** Some divergent prefixes are not aliases of one
   entity but the *same prefix naming two different things* — `GEO` is the Geographical Entity
   Ontology (`obo/GEO_`) in some sets and NCBI Gene Expression Omnibus datasets (`GDSbrowser`) in
   others. A blanket registry canonicalisation would silently merge them; a curated table only pins
   cases a human has verified are true aliases.
4. **Scope discipline.** Bioregistry is authoritative for *prefix → some IRI*, not for *which IRI OxO2
   should emit*. Keeping it as the bare-set fallback (ADR-0015) and making canonical identity an
   explicit, reviewed choice keeps a corpus-wide, API-visible decision out of an auto-refreshed
   upstream snapshot.

## Consequences

- One entity ⇒ one IRI node in Nemo for overridden prefixes: no duplicate cross-set conclusions and
  no folding-induced circular explanations. Because the override applies at the shared expansion
  chokepoint, the asserted mapping docs, the N-Quads, and the IRI-keyed entity-details cache
  (`DataloadSolr`) stay consistent — no split-brain — and `JSON2NQuads` needs no change.
- The override is **unconditional**, unlike the Bioregistry fallback: it changes the `subject_iri` /
  `object_iri` emitted (and thus shown in the API) for overridden prefixes. This is deliberate; the
  canonical stem is chosen by us, not by Bioregistry, precisely to avoid its preferred form.
- The table is **curated, not exhaustive** — the detector found ~60 genuine candidates; thirteen
  disease/anatomy/clinical prefixes are pinned. The rest are deliberately left out: prefix
  **collisions** (`GEO`), curie_map corruption (`AFO`, `*_2` duplicate prefixes, double-expanded OBO
  PURLs, `unknown_prefix` placeholders), and resolver-URL / structure / publication identifiers
  outside the disease-mapping domain. Un-pinned divergent prefixes still alias until added, but the
  ADR-0028 stage now emits a loud, actionable warning naming the folded S-P-O when one bites, so they
  surface in dataload logs rather than as silent bad explanations.
- Physical de-duplication of same-CURIE-SPO inferred docs is intentionally **not** added here: it
  fights the streaming/sharding design (ADR-0028) and same-SPO rows are already collapsed at query
  time (ADR-0023). The override removes the duplication at its source instead.
- New operational tool: `PrefixDivergenceDetector` (in `oxo2-sssom2json`) scans the materialised
  `mappingSet/` curie_maps and reports divergent prefixes; run it after a corpus refresh to keep the
  override table current. Complements ADR-0015; motivated by the ADR-0028 explanation guarantee.
