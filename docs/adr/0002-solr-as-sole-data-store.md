# ADR-0002: Solr is the sole data store

- **Status**: Accepted
- **Date**: 2026-05-12

## Context

OxO2 needs to store mappings, mapping sets, and entity references, and serve them via a query API supporting full-text search, faceting, filtering, and pagination. The natural candidates were a relational database (e.g. PostgreSQL) plus a search index (e.g. Solr/Elasticsearch), or a single search-index-as-store deployment.

The OxO2 query surface is dominated by faceted search and filtering — workloads that Solr handles natively and that relational databases handle awkwardly. Strong relational consistency is not required: mappings are loaded in bulk from external SSSOM files; there is no transactional update flow from end users.

## Decision

OxO2 uses Apache Solr as the *sole* data store. There is no relational database. The two Solr collections are `oxo2-mappings` and `oxo2-mappingsets` (see `oxo2-dataload/solr-config/`).

## Consequences

- All persistence concerns live in Solr: schema in `oxo2-dataload/solr-config/`, backups are Solr-shaped, scaling is Solr's clustering story.
- Query patterns are constrained by Solr's capabilities. Joins are limited; the data model is denormalised at load time, not at query time.
- The dataload pipeline is responsible for producing query-ready denormalised documents (`oxo2-solr-dataload-client` caches `EntityDetails` and `<s, p, o>` triples to enable this).
- There is no transactional write path from the backend API; mappings change only via re-running the dataload pipeline.
- Schema evolution is via Solr config (`solr-config/oxo2-mappings`, `solr-config/oxo2-mappingsets`) and re-indexing, not migrations.
