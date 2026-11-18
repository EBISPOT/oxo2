# ADR-0055: Warm the ontology catalog once per backend lifetime

- **Status**: Accepted
- **Date**: 2026-11-18

## Context

The global `/api/v2/ontologies` response computes prefix counts over the mappings index, namespace
metadata over the entity index, and ontology IRIs over the mapping-set index. Repeating these
queries under concurrent traffic consumes substantial Solr CPU and heap. The serving index is an
immutable release built on the HPC and imported into Kubernetes. The backend is redeployed with
each data release.

## Decision

Keep the complete, immutable ontology catalog in backend memory for the lifetime of that backend.
Warm it asynchronously on `ApplicationReadyEvent`, retrying every 30 seconds until successful.
Startup warming and HTTP requests use the same synchronised loader, so only one load can run at
a time. Publish the snapshot only after all queries succeed. No expiry or shared cache is needed.

## Consequences

- Every replacement of the serving index **requires restarting every backend replica**, including
  a rollback or a local dataload. Restarting Solr alone does not invalidate the backend catalog.
  Coordinate the release so the backend loads from the new, ready Solr index before restoring
  traffic. A software-only backend restart safely reloads the same catalog.
- Each backend replica computes the catalog once. Later global listing requests issue no Solr
  queries. `?forSubject=...` target facets and export queries remain separate query paths.
- The backend can start before Solr. The daemon warmer retries failures and empty results and is
  interrupted at shutdown. Requests arriving before warmup completes wait for an active load or
  attempt the load themselves. Backend readiness does not wait for the catalog to warm.
- An enrichment failure still permits the existing counts-only response, but that response is
  not cached. Partial Solr results and truncated ontology metadata are not cached either. Missing
  optional metadata in a successfully queried legacy index remains valid.
- `oxo.ontology-cache.retry-delay-ms` controls the retry interval (default `30000`, positive).
  A successful warmup logs its duration. The first successful nonempty, complete catalog is kept
  until process exit.
