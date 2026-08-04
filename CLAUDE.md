# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OxO2 is a SSSOM-compliant ontology mapping service. For purpose, domain language, module roles, cross-cutting constraints, and the end-to-end data flow see [`/CONTEXT.md`](CONTEXT.md). For architectural decisions and their rationale see [`docs/adr/`](docs/adr/).

## Documentation conventions

- **Domain language and architecture** live in [`/CONTEXT.md`](CONTEXT.md) (project-wide) and `<module>/CONTEXT.md` (per top-level module). Update these when introducing or renaming a domain term, or when changing what a module exposes.
- **Architectural decisions** live in [`docs/adr/`](docs/adr/). When changing a cross-cutting constraint (e.g. inference scope, Solr collection layout, mapping-justification handling), add or supersede an ADR in the same PR. See [`docs/adr/README.md`](docs/adr/README.md) for conventions and template.
- **Operational instructions** (build/run/deploy) live here in CLAUDE.md and in the root `README.md`.

## Module map

See [`/CONTEXT.md`](CONTEXT.md) § Module map for the canonical list with one-line descriptions. Per-module detail in each `oxo2-*/CONTEXT.md`.

Note that `oxo2-integration-tests` (added 2026-05) is a top-level sibling of the other modules; it drives the full `loadData.nextflow` pipeline against the rule fixtures in `testcases/minimal/rules/`. See [`oxo2-integration-tests/CONTEXT.md`](oxo2-integration-tests/CONTEXT.md).

## Data Loading Pipeline

See [`oxo2-dataload/CONTEXT.md`](oxo2-dataload/CONTEXT.md) for the pipeline stages, scripts, and chunked-tracing details.

## Build & Test Commands

```bash
# Build everything (from repo root)
mvn clean install

# Backend tests (oxo2-backend has unit tests for the REST controllers and SolrQueryBuilder)
mvn -pl oxo2-backend test
# Conventions and known limitations: oxo2-backend/CONTEXT.md § Testing

# Frontend
cd oxo2-frontend
npm install
npm run dev          # Dev server on port 8080 (override with OXO_FRONTEND_PORT)
npm run build        # Production build
npm run lint         # ESLint
```

## Running Locally

Required environment variables:
```bash
export OXO2_DATA=/path/to/data
export OXO2_CONFIG=/absolute/path/to/oxo-config.json
export SOLR_SCRIPT=/path/to/solr/bin
export SOLR_HOME=/path/to/solr/data
export SOLR_HEAP=4g
export OXO2_SOLR_HOST=http://localhost:8983/solr
```

```bash
# Start Solr, load data, start backend
cp ./oxo2-dataload/solr-config/* $SOLR_HOME
$SOLR_SCRIPT/solr start --user-managed
cd oxo2-dataload && ./loadData.nextflow && cd ..
./startBackend.sh
```

All ports are overridable per checkout, so several stacks (e.g. git worktrees) can run side by
side: the Vite dev server reads `OXO_FRONTEND_PORT` (default 8080), `startBackend.sh` forwards
`OXO2_BACKEND_PORT` to `server.port` (default 8081), and `loadData.nextflow` starts/stops its
managed Solr on the port in `SOLR_URL` (default 8983). Point `OXO2_SOLR_HOST`/`SOLR_URL` and
`OXO_BACKEND_URL` at the same ports.

Optional: set the top-level `min_inference_confidence` key in `oxo-config.json` (default absent = off) to
keep low-confidence mappings out of the cross-set inference closure. When set above 0, the dataload drops
any mapping whose SSSOM `confidence` is present and below the threshold from the inference corpus (it
stays indexed as an asserted mapping) and records each drop in a `<set>.dropped-low-confidence.tsv`
sidecar. Mappings with no confidence value are unaffected. See
[ADR-0037](docs/adr/0037-confidence-gate-on-inference-corpus.md).

Or use Docker Compose:
```bash
export OXO2_CONFIG=./oxo-config.json
docker compose up
# Frontend: :8080  Backend: :8081  Solr: :8983
```

## Tech Stack

- Java 25, Maven, Spring Boot 4.1.0, Jackson 3 (`tools.jackson` — see
  [ADR-0046](docs/adr/0046-spring-boot-4-and-jackson-3.md))
- Apache Solr 10.0.0 (sole data store — see [ADR-0002](docs/adr/0002-solr-as-sole-data-store.md))
- Nemo v0.10.1 rules engine (inference/explanation)
- React 19, TypeScript, Vite, Tailwind CSS, TanStack React Query
- Nextflow (required for local and HPC — see [ADR-0003](docs/adr/0003-nextflow-as-sole-dataload-path.md))

## CI/CD

GitHub Actions (`.github/workflows/docker.yml`): builds and pushes Docker images to `ghcr.io/ebispot/` on push/PR to `dev` branch.

## Deployment

- **Local**: Docker Compose or manual setup
- **Kubernetes**: Helm charts in `k8chart-local/` and `k8chart-dev/`
- **HPC**: SLURM integration via `loadData.slurm` / `loadData.hpc`

## Markdown style
- Wrap markdown prose at 100 columns. Never write paragraphs as a single long line.
- Exceptions: tables, code blocks, and URLs may exceed the limit.

## Code style
- Use descriptive variable names. Avoid single-letter or short abbreviations
  for local variables even when the type is obvious from context.
  Prefer `processBuilder` over `pb`, `process` over `p`, `connection` over `conn`,
  `request` over `req`. Loop counters (`i`, `j`) and conventional math
  variables are fine.

## Agent skills

### Issue tracker

Issues live in GitHub Issues at `EBISPOT/oxo2`; skills use the `gh` CLI.
See [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md).

### Triage labels

Canonical names (`needs-triage`, `needs-info`, `ready-for-agent`,
`ready-for-human`, `wontfix`). See [`docs/agents/triage-labels.md`](docs/agents/triage-labels.md).

### Domain docs

Single-context: root `CONTEXT.md` is canonical with a Module map, per-module
`oxo2-*/CONTEXT.md` provide module detail, ADRs at `docs/adr/`.
See [`docs/agents/domain.md`](docs/agents/domain.md).
