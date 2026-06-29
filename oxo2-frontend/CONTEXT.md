# oxo2-frontend — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this module 
specifically owns.

## Purpose

`oxo2-frontend` is the React single-page application users interact with. It provides search by CURIE, mapping-results browsing
with paging, mapping-detail views, and inferred-mapping graph visualisation. It is a consumer of `oxo2-backend`'s REST API 
and has no direct knowledge of Solr.

## Vocabulary introduced here

None. The frontend uses the SSSOM and OxO2 cross-cutting vocabulary defined in `/CONTEXT.md` § Glossary. Frontend-side 
TypeScript models (`src/model/Mapping.ts`, `MappingSet.ts`, `Search.ts`, `AdvancedFields.ts`) mirror the backend response shapes.

## Depends on

External:
- **React 19**, **TypeScript**, **Vite** — framework, type system, dev server / build.
- **Tailwind CSS** — styling.
- **TanStack React Query** (`@tanstack/react-query`) — server-state caching and request coalescing. Wired at the root in `App.tsx`.
- **react-router / react-router-dom** — client-side routing.

OxO2 modules:
- `oxo2-backend` via HTTP only — the frontend talks to `/api/v2/...`. No build-time dependency.

## Exposes

The user interface, served on port 8080 (Vite dev server, Docker, and Kubernetes deployments).

Routes (`App.tsx`):

- **`/`** and **`/home`** → `Home` — landing page with search.
- **`/search/:curies`** → `MappingResults` — paged mapping results for the given CURIE(s).
- **`/map`** → cross-ontology results for `?from=…&to=…` (source→target prefixes), bookmarkable; the
pasted-term-list (batch) variant is POST-only and not bookmarkable ([ADR-0024](../docs/adr/0024-cross-ontology-mapping.md)).
- **`/mapping/:id`** → `MappingDetails` (via `MappingDetailsWrapper` to pass state through router) — detail view for a 
single mapping including inferred-mapping graph.
- **`/inferences`** and **`/inferences/*`** → `InferencesPage` — resolution surface for inferred mapping sets: the
cross-set `https://www.ebi.ac.uk/oxo2/inferences` set, and per-source OWL sets via the `*` splat. Fetches the set
via `GET /api/v2/mapping-sets/by-id` and lists its mappings ([ADR-0012](../docs/adr/0012-resolvable-inference-set-iris.md)).
- **`/docs`** → `Documentation`.
- **`/about`** → `About`.

## Module notes

### Layout

- `src/App.tsx`, `src/index.tsx` — root and routing.
- `src/app/api.ts` — HTTP client for `oxo2-backend`.
- `src/model/` — TypeScript shapes mirroring backend DTOs (`Mapping`, `MappingSet`, `Search`, `AdvancedFields`).
- `src/pages/` — top-level views (`home/`, `results/`, `documentation/`, `about/`).
- `src/components/` — reusable widgets: `search/Search.tsx`, `search/AdvancedSearch.tsx`, `paging/Paging.tsx`, 
`mapping/MappingItem.tsx`, `mapping/InferredMappingGraph.tsx`, `common/Header.tsx`, `common/Footer.tsx`, `infoCard/`, `error/`.

### Inference type (ADR-0011)

`src/model/InferenceType.ts` is the single source of truth for the inference type (ASSERTED /
SSSOM_INFERENCE): code→label map, display order, the default filter selection
(`{Asserted, SSSOM inference}`), and badge colours. Shared components
`mapping/InferenceTypeBadge.tsx` (badge) and `mapping/InferenceTypeFilter.tsx` (multi-select toggle) are
reused by both result tables and the mapping-set selector. `mapping/InferredMappingGraph.tsx` labels each
asserted leaf with its source mapping set, surfacing cross-set provenance.

### Cross-ontology mapping (ADR-0024)

The normal **Search** tab is extended (not a new tab) with two optional exact-prefix selectors,
**"from \[source ontologies]"** and **"to \[target ontologies]"**, plus a ⇄ swap, beside the existing
terms box. Options and counts come from `GET /api/v2/ontologies` (re-fetched with `?forSubject=` once a
source is chosen, so targets show counts and zero-count targets grey out). They compose: source prefix
+ empty box → entire ontology; terms + target prefix → bounded batch lookup; terms only → today's
search. A label input (not a CURIE/IRI) is treated as a **source-side** label, bounded by the target
prefixes. Results reuse `NormalResultsTable` plus a summary header and an SSSOM-TSV "Export" button; the
batch (pasted-list) variant also shows an unmapped-inputs panel and mirrors its input to
`sessionStorage` so a refresh re-runs.

### State management

Server state is handled by TanStack Query (caching, invalidation, retry). Local view state uses React's `useState` / `useReducer`. 
Files named `*Slice.ts` (e.g. `MappingResultsSlice.ts`, `MappingSetsSlice.ts`, `InfoCardSlice.ts`, `ErrorSlice.ts`) 
are **not** Redux slices — the naming is residual; there is no Redux store in this codebase.

### Build and run

- `npm run dev` — Vite dev server on port 5173.
- `npm run build` — production bundle.
- `npm run lint` — ESLint.
- In Docker/Kubernetes deployments the built bundle is served on port 8080.
