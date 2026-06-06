# ADR-0012: Inference sets are identified by resolvable IRIs under the OxO2 base

- **Status**: Accepted
- **Date**: 2026-06-06
- **Supersedes**: [ADR-0005](0005-inferences-stored-in-own-per-mapping-set.md)

## Context

[ADR-0005](0005-inferences-stored-in-own-per-mapping-set.md) identified per-source inference sets by
`https://www.ebi.ac.uk/spot/oxo/inferences/<encoded source id>` — an opaque, non-resolving id under the OxO
**v1** namespace. The two-phase redesign
([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)) adds a single cross-set SSSOM-inference
set, and OxO2 will eventually **replace** OxO. New ids should therefore live in OxO2's own resolvable
namespace, mirroring OLS (`https://www.ebi.ac.uk/ols4/`).

## Decision

Inference mapping sets are identified by **resolvable IRIs** under the OxO2 base
`https://www.ebi.ac.uk/oxo2/`:

- **Phase-2 single SSSOM-inference set**: `https://www.ebi.ac.uk/oxo2/inferences`
- **Phase-1 per-source OWL-inference sets**: `https://www.ebi.ac.uk/oxo2/inferences/<URLEncoded(source id)>`

These IRIs resolve to an OxO2 mapping-set view: a new frontend route (`/inferences` and an `/inferences/*`
splat, so an encoded source id survives as one path segment) backed by a new `GET /api/v2/mapping-sets/{id}`
endpoint. The phase-2 set's `mapping_set_source` is the **union** of all contributing source sets; per-mapping
`mapping_source` follows each mapping's own explanation leaves.

## Consequences

- Inference-set ids are dereferenceable and live under OxO2's canonical, version-stable namespace; the v1
  `spot/oxo/inferences/` scheme retires (the full reindex replaces existing ids).
- Adds a **mapping-set resolution surface** (frontend route + by-id endpoint) OxO2 did not previously have.
  Asserted sets keep their own source-file IRIs, which are outside our base and not OxO2-resolvable.
- The phase-2 id is the **prefix** of every phase-1 id; code that derives a source set from an inference-set
  id must treat the bare `/inferences` (no suffix) as the cross-set set with no single source.
- Supersedes [ADR-0005](0005-inferences-stored-in-own-per-mapping-set.md).
