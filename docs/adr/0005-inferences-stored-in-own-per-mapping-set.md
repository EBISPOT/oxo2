# ADR-0005: Inferences are stored in their own per mapping set, mapping set

- **Status**: Accepted
- **Date**: 2026-05-01

## Context

Initially OxO2 stored all inferences in a single mapping set https://www.ebi.ac.uk/spot/oxo/inferences/ and the specific inferences
that followed from a given mapping set was not clear. 

## Decision

For each mapping set (`source_mapping_set_id`), an additional inferences mapping set is created with 
`mapping_set_id = "https://www.ebi.ac.uk/spot/oxo/inferences/" + UrlEncoded(source_mapping_set_id)`. We also set
`mapping_set_source = source_mapping_set_id`. 

## Consequences

1. We can find the inferences that follow from a given mapping set.
2. For any inferred mapping, we know exactly from which mapping set it was inferred.