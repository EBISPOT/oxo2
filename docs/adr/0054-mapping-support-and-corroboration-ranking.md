# ADR-0054: Mapping support and corroboration ranking

- **Status**: Accepted
- **Date**: 2026-11-13

## Context

[Issue #181](https://github.com/EBISPOT/oxo2/issues/181) concerns the priority of mappings supported
by multiple records or justifications. Search collapses the same statement into one representative
row, with a bounded inline expansion. A representative's justification does not describe every
record in that group, and multiple records or methods do not necessarily mean multiple sources.

[ADR-0013](0013-group-same-spo-mappings-in-result-views.md) defines the grouped view and
[ADR-0023](0023-collapse-for-same-spo.md) moves grouping into Solr. Existing provenance ranking
([ADR-0027](0027-config-driven-mapping-set-category.md)) rewards source category, predicate strength, manual
curation and confidence, but does not reward corroboration by additional asserted source sets.

Dataload builds a fresh index from the complete input collection. A post-index backfill would add a
separate stage, require reading Solr again, and temporarily leave documents without their support
statistic. The counts can instead be computed from the asserted input before its first upload.

## Decision

Expose paginated dataset-wide supporting records for each mapping group, and sort default search
results first by the number of distinct source mapping sets directly asserting that statement.
Populate the ranking
statistic during initial asserted indexing, without a backfill stage.

A group uses the existing indexed `spo_key`: subject, predicate, predicate modifier and object.
Positive and negated statements remain separate. Preserve these distinct quantities:

- **Record count** includes all records in the group, including the selected record.
- **Justification-method count** counts distinct nonblank `mapping_justification` identifiers.
- **Asserted-source count** counts distinct nonblank `mapping_set_id` values among `ASSERTED`
  records. Inference premises making other statements do not contribute to this count.

`GET /api/v2/mappings/{mappingId}/support` resolves the indexed group key and returns summary
records
and exact group-wide counts. It ignores search visibility filters and does not collapse results.
Paging is zero-based, defaults to 20 records and permits 1–100 per page. Ranking followed by index
ID
provides stable ordering. Individual record links expose full details and inference explanations.

Search distinguishes its matching-record count from dataset-wide support, and links to the complete
support list beyond the inline expansion limit of 21 records. Mapping details show Supporting
mappings for asserted records; for inferred records, show it only if another record exists besides
the current one. Inference premises remain in their separate explanation section.

Default search order is:

```text
asserted source count descending, provenance-weighted relevance score descending
```

Three direct sources rank above two, two above one, and one above zero, even when the result with
fewer sources has stronger provenance or a higher query score. Missing counts sort as zero. Count
sources across the dataset, not the filtered result or current page. Repeated records or methods
from the same set do not increase the count. Explicit column sorting replaces this default order.
The v2 and SSSOM search surfaces share this order. Grouped searches append the existing group key
for stable paging; within a group the count is shared, so score still selects the representative.

This amends ADR-0027's default ranking priority: its provenance-weighted relevance score remains
intact as the second sort key. A capped multiplicative support bonus was rejected because it lets
single-source ontology mappings outrank mappings directly asserted by several curated sources.
A larger multiplier still cannot guarantee source-count priority over arbitrary query scores.

The existing `index-asserted` stage passes `--asserted-support` to `json2solr.sh`. The Java loader
streams all asserted input files, externally sorts and deduplicates group/source pairs, and builds
a memory-mapped count lookup. It then enriches complete documents with `asserted_source_count`
before uploading them. Original input files remain unchanged and temporary files are cleaned up.
The local and HPC pipelines use the same path. Inferred loading retains the schema default of zero:
the inference rules exclude conclusions already asserted, so inferred groups have no directly
asserted sources. Revisit this assumption if the inference rules change.

## Consequences

- Users can inspect every justification and source, including records their search hid. Support
  counts can exceed the matching-record count. The API counts the whole group, not each page.
- Distinct source sets are a corroboration signal, not independent evidence or a confidence
  estimate. Providers can reuse evidence across sets. Counting records or methods instead would
  reward repetition within a source, so neither drives the primary sort.
- Source count takes precedence over text relevance and source category among matching records.
  A less relevant match with more sources can appear ahead of a closer match with fewer sources.
  Search filters still determine which mappings are eligible; support does not bypass them.
- Within equal source counts, the provenance-weighted relevance score keeps the existing behavior.
  Its maximum non-provenance boost is `2 × 1.3 × 1.3 = 3.38`, below the smallest provenance gap of
  5.
  Provenance therefore dominates these score factors at equal query relevance. This is a property
  of the secondary score, not an override of the primary source-count sort.
- Ranking is applied in Solr before grouping and paging. Reordering an already returned page would
  miss better-supported mappings on later pages and is not an alternative.
- The Solr schema must include the single-valued numeric docValues field `asserted_source_count`
  (default zero). Deploy the schema and rebuild data to populate counts for existing assertions;
  an older index without populated counts does not gain corroboration ranking automatically.
- Initial loading needs GNU sort and temporary disk space for pairs, the count index and one
  enriched
  input file. This bounds heap use instead of keeping all group/source pairs in memory. Production
  scale disk usage and throughput require measurement with the target dataset.
- There is no extra dataload stage and no Solr read or partial update for this statistic. Future
  incremental loading would need a separate strategy for keeping group counts consistent.
