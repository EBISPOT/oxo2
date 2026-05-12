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

## Data Loading Pipeline

See [`oxo2-dataload/CONTEXT.md`](oxo2-dataload/CONTEXT.md) for the pipeline stages, scripts, and chunked-tracing details.

## Build & Test Commands

```bash
# Build everything (from repo root)
mvn clean install

# Frontend
cd oxo2-frontend
npm install
npm run dev          # Dev server on port 5173
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
export OXO2_SOLR_HOST=http://localhost:8983/solr
```

```bash
# Start Solr, load data, start backend
cp ./oxo2-dataload/solr-config/* $SOLR_HOME
$SOLR_SCRIPT/solr start --user-managed
cd oxo2-dataload && ./loadData.nextflow && cd ..
./startBackend.sh
```

Or use Docker Compose:
```bash
export OXO2_CONFIG=./oxo-config.json
docker compose up
# Frontend: :8080  Backend: :8081  Solr: :8983
```

## Tech Stack

- Java 17, Maven, Spring Boot 3.4.1
- Apache Solr 9.9.0 (sole data store — see [ADR-0002](docs/adr/0002-solr-as-sole-data-store.md))
- Nemo v0.9.1 rules engine (inference/explanation)
- React 19, TypeScript, Vite, Tailwind CSS, TanStack React Query
- Nextflow (required for local and HPC — see [ADR-0003](docs/adr/0003-nextflow-as-sole-dataload-path.md))

## CI/CD

GitHub Actions (`.github/workflows/docker.yml`): builds and pushes Docker images to `ghcr.io/ebispot/` on push/PR to `dev` branch.

## Deployment

- **Local**: Docker Compose or manual setup
- **Kubernetes**: Helm charts in `k8chart-local/` and `k8chart-dev/`
- **HPC**: SLURM integration via `loadData.slurm` / `loadData.hpc`
