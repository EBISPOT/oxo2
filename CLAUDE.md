# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OxO2 is a SSSOM-compliant (Simple Standard for Sharing Ontology Mappings) ontology mapping service. It downloads SSSOM mappings, converts them to JSON, generates inferences using the Nemo rules engine, and indexes them into Apache Solr. A Spring Boot backend serves an API and a React frontend provides the UI.

## Build & Test Commands

```bash
# Build everything (from repo root)
mvn clean install

# Run all tests
mvn clean test

# Run a single test class
mvn test -pl oxo2-dataload/oxo2-sssom2json -Dtest=SomeTestClass

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
cd oxo2-dataload && ./loadData.sh && cd ..
./startBackend.sh
```

Or use Docker Compose:
```bash
export OXO2_CONFIG=./oxo-config.json
docker compose up
# Frontend: :8080  Backend: :8081  Solr: :8983
```

## Module Structure

- **oxo2-shared** — SSSOM data models and Jackson serialization (Mapping, MappingSet, EntityReference, etc.)
- **oxo2-dataload** — Data loading pipeline with sub-modules:
  - `oxo2-downloader` — Download SSSOM files from URLs/GitHub
  - `oxo2-sssom2json` — Convert SSSOM TSV → JSON
  - `oxo2-json2inferences` — Generate inferences via Nemo rules engine
  - `oxo2-solr-dataload-client` — Index JSON into Solr
  - `oxo2-dataload-testing` — Test utilities
  - `solr-config/` — Solr collection configs (oxo2-mappings, oxo2-mappingsets)
- **oxo2-backend** — Spring Boot 3.4.1 REST API (port 8081), queries Solr via SolrJ
- **oxo2-frontend** — React 19 + TypeScript + Vite + Tailwind CSS

## Data Loading Pipeline

Two execution paths exist:
- **Sequential** (`*.sh` scripts): `loadData.sh` → `downloadMappings.sh` → `sssom2json.sh` → `determineInferencesAndExplanations.sh` → `json2solr.sh`
- **Parallel** (`*.nextflow` / `*.nf` scripts): Uses Nextflow for parallelization. Docker always uses this path.

Shell scripts use `SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"` for relative paths. Nextflow DSL workflows are in `.nf` files; `*Nextflow.sh` are helper scripts called by `.nf` workflows.

## Tech Stack

- Java 17, Maven, Spring Boot 3.4.1
- Apache Solr 9.9.0 (no database — Solr is the sole data store)
- Nemo v0.9.1 rules engine (inference/explanation)
- React 19, TypeScript, Vite, Tailwind CSS, TanStack React Query
- Nextflow (optional for local, required in Docker)

## CI/CD

GitHub Actions (`.github/workflows/docker.yml`): builds and pushes Docker images to `ghcr.io/ebispot/` on push/PR to `dev` branch.

## Deployment

- **Local**: Docker Compose or manual setup
- **Kubernetes**: Helm charts in `k8chart-local/` and `k8chart-dev/`
- **HPC**: SLURM integration via `loadData.slurm` / `loadData.hpc`
