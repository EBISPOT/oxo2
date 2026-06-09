# ADR-0015: Bare SSSOM sets — synthesise metadata from columns and fall back to a bundled Bioregistry prefix map

- **Status**: Accepted
- **Date**: 2026-06-09

## Context

Adding Mapping Commons as a source ([ADR-0014](0014-mapping-commons-registry-via-specifications-json.md))
surfaced published SSSOM sets that the converter (`oxo2-sssom2json`) silently dropped. The drops
were invisible: `sssom2json.nf` declares its outputs `optional: true`, so a TSV that produced no
JSON simply vanished with the task still exiting 0. Three distinct causes, all in metadata handling:

1. **No metadata at all.** The five biopragmatics SeMRA landscape `priority` views
   (gene/cell/protein/anatomy/disease) ship a bare TSV: the first line is the column header, there
   is no `#`-commented YAML block and no external `.yml` sidecar. The catalogue itself flags these
   `"status": "no_metadata"`. `TSV2JSON` logged *"Both external and embedded metadata are missing"*
   and emitted nothing. The set-level slots (`mapping_set_id`, `mapping_set_title`, `license`)
   are present, but as **per-row columns**; and the prefixes (`umls`, `mesh`, `doid`, …) are not
   declared anywhere in the file.

2. **`mapping_set_source` as a YAML sequence.** `mapping_set_source` is multivalued in SSSOM, so a
   header may give it as a sequence. `MappingSet.Builder` modelled it with a `String`-only Jackson
   setter, so a sequence threw *"Cannot deserialize value of type String from Array value"* and
   dropped the set (`gene_mappings`, `mesh_chebi_biomappings`, and the two `*_invert.cpath` sets).
   `creator_id` — also multivalued — already had an `Object` setter and parsed fine, which is why
   otherwise-identical files diverged.

3. **Filename collisions at the flat output.** `sssom2json.nf` named each output after the input's
   bare basename. The five landscape views are all `priority.sssom.tsv` (distinguished only by their
   `mapping-registry/<landscape>/` sub-directory, [ADR-0014](0014-mapping-commons-registry-via-specifications-json.md)),
   so they would all publish as `priority.sssom.json` (last-wins) even once they parsed.

A separate, **by-design** drop also became visible: the `ebi-text-mappings` sets map free-text
(`subject_label`, no `subject_id`) to ontology terms via `skos:closeMatch`. They are indexed as
asserted mappings but cannot form N-Quads (no subject IRI / non-inference predicate), so they never
enter the inference corpus. That is correct; it was just silent.

## Decision

**Prefix source.** When a set declares no `curie_map`, expand its CURIEs against the **Bioregistry**
— the convention SSSOM tooling (`sssom-py`) already follows, and the registry SeMRA itself mints
its CURIEs from (confirmed by each landscape's `configuration.json`: every input is a
`pyobo`/`wikidata`/`bioregistry` prefix). A snapshot of Bioregistry's published prefix map
(`bioregistry.context.jsonld`, ~2260 prefixes) is **bundled** in `oxo2-shared`
(`src/main/resources/`) and exposed via `BioregistryPrefixMap`. Bundling keeps the dataload
reproducible and offline; `oxo2-shared/refresh-bioregistry-context.sh` re-fetches it. The fallback
applies **only** to sets that declare no `curie_map` — sets shipping their own map are untouched
(narrow blast radius; a globally-applied fallback could silently change expansions for every set).

**Metadata synthesis.** When a TSV has neither an embedded header nor an external `.yml`,
`TSV2JSON` synthesises the `MappingSet` from the first data row's set-level columns
(`mapping_set_id` / `mapping_set_title` / `mapping_set_version` / `license`) and applies the bundled
Bioregistry map as the `curie_map`. The distinct per-row `mapping_set_id` (a SeMRA UUID, different
per landscape) keeps the five views as distinct sets in Solr. This is logged at WARN, not dropped.

**Multivalued metadata.** `MappingSet.Builder.mappingSetSource` now takes `Object` and accepts both
a scalar (pipe-delimited) and a sequence, mirroring `creatorId`.

**Output naming.** `sssom2json.nf` derives each output's stem from the input path **relative to the
sssom root**, flattened to a safe filename (`mapping_commons/mapping-registry/gene/priority.sssom.tsv`
→ `mapping_commons.mapping-registry.gene.priority.sssom`). Distinct sets that share a basename across
sub-directories no longer collide. Downstream stages already treat the stem as an opaque unique key
(they match by `*.json` glob / read content), so the new names need no other change.

**Visibility.** `JSON2NQuads` logs a WARN naming any set for which it produces zero quads (so the
by-design `ebi-text-mappings` skips are explained, not silent).

## Consequences

- The five SeMRA landscape `priority` views and the four `mapping_set_source`-list sets now load
  (verified: `cell/priority` → 43,698 mappings with `umls:`/`mesh:` expanded to IRIs;
  `gene_mappings` → 1,165,273 mappings). Previously all nine produced no JSON.
- **Phase-2 load is now real, not theoretical.** Before this fix the five `priority` views never
  reached the cross-set corpus (they failed conversion); they now do. The gene view alone is
  ≈569 MB / ~1.16M mappings, so the phase-2 cross-set pass processes materially more, and its
  component-size guard ([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md) /
  project notes) becomes the gating concern for a full load. (The 685k-node `exactMatch`
  component and ~30B-net-new closure-explosion figures were measured on the *raw/processed*
  SeMRA assemblies, which `exclude` keeps out; the curated `priority` views are the near-closed,
  intended-safe subset — heavier, but not that blow-up.) The guard remains pending.
- The bundled snapshot can drift from upstream Bioregistry. It is a point-in-time copy; refresh via
  the script. A missing/unreadable resource degrades to "no fallback expansion" (logged), never a
  crash.
- Synthesised sets embed the full ~2260-prefix map in their mapping-set JSON. This is consistent with
  existing sets (which already serialise their own `curie_map`) and is bounded to the few header-less
  sets.
- New maintenance coupling: `BioregistryPrefixMap` assumes the snapshot's `@context` shape, and
  `TSV2JSON` assumes the SSSOM column names. Keep `BioregistryPrefixMapTest` and
  `MappingSetDeserializationTest` green.
