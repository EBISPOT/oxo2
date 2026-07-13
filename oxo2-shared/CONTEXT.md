# oxo2-shared — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this module 
specifically owns.

## Purpose

`oxo2-shared` defines the **SSSOM data model** as Java types and their Jackson serialization. It is the vocabulary library 
every other Java module depends on. It owns no logic beyond construction, validation, and (de)serialization of SSSOM entities — 
query, transformation, inference, and storage live in other modules.

## Vocabulary introduced here

None new. This module is the *implementation* of the SSSOM and OxO2 cross-cutting terms defined in `/CONTEXT.md` § Glossary. 
The concrete Java types that realise those terms are listed under § Exposes.

The one term worth noting at module level is `ChainRuleApplications` (a record of which `ChainRulesEnum` rules fired to 
derive a given `InferredMapping`). It is a code structure, not a new domain concept.

## Depends on

External:
- Jackson (databind, jdk8) — JSON (de)serialization of SSSOM types.
- SolrJ — `@Field` annotations on `Mapping` allow direct binding from Solr documents.
- SLF4J — logging.

OxO2 modules: none. `oxo2-shared` is the dependency root.

## Exposes

All Java types under `uk.ac.ebi.spot.oxo.model.sssom`:

- **Data records** — `Mapping`, `MappingSet`, `InferredMapping`, `EntityReference`, `Prefix`, `PrefixMap`, `CurieMap`, 
`ChainRuleApplications`.
- **`BioregistryPrefixMap`** — the Bioregistry prefix map, bundled as a resource snapshot
(`src/main/resources/bioregistry.context.jsonld`, ~2260 prefixes). It is the fallback `curie_map` for SSSOM sets that
declare no prefixes of their own — see [ADR-0015](../docs/adr/0015-default-prefix-map-and-metadata-synthesis-for-bare-sssom.md).
Refresh the snapshot with `refresh-bioregistry-context.sh`; keep `BioregistryPrefixMapTest` green.
- **SSSOM value wrappers** — `Uri`, `Date`, `Double` (SSSOM-shaped types with Jackson custom serialization).
- **Enumerations** — `ChainRulesEnum`, `MappingCardinalityEnum`, `MappingEnum`, `MappingSetConstants`/`MappingConstants` (string keys), 
`PredicateModifierEnum`, `EntityTypeEnum`, `SSSOMDataType`.
- **Utilities** — `StringUtils`, `KeyValuePairsAsString`.

`ChainRulesEnum` deserves special attention: it enumerates the SSSOM chaining-rule families (`RCE1-*`, `RCE2-*`, the `T*` transitivity rules, `RI1`–`RI5`) plus the `Asserted` baseline. Each rule carries its long-form and abbreviated 
Nemo representations. This is the bridge between the SSSOM chaining-rules spec and the Nemo rules in `oxo2-json2inferences/sssom.rls`.

## Module notes

`Mapping` is a Java `record` deserialized via a builder (`Mapping.Builder`). Field set follows the SSSOM spec closely 
(subject/predicate/object IDs and IRIs, label fields, justification, confidence, mapping set provenance, author/creator metadata). 
See the class Javadoc for the SSSOM Mapping spec link.

`InferredMapping` is a class (not a record) and carries:
- subject/predicate/object IDs, IRIs, and labels;
- `mapping_set_id` (per [ADR-0001](../docs/adr/0001-inference-scope-per-mapping-set.md) — every inferred mapping is scoped to one set);
- `distance` (default 1) — the mapping's ontology span: distinct CURIE prefixes across the explanation
  DAG minus one, floored at 1 ([ADR-0031](../docs/adr/0031-inferred-mapping-distance-as-ontology-span.md));
- `ChainRuleApplications` (optional) — the chain rules that produced this mapping.

Constants classes (`MappingConstants`, `MappingSetConstants`) hold the SSSOM field-name string keys used by both Jackson 
`@JsonProperty` annotations and Solr field references — making this module the canonical source of those names for backend and dataload alike.
