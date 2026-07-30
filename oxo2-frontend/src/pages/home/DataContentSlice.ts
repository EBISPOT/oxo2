import { get } from '../../app/api';

// The landing page's Data Content summary (ADR-0043): what the currently loaded corpus consists of.

/** Mapping counts by origin. `asserted` and `inferred` partition `total` exactly. */
export interface MappingCounts {
    total: number;
    asserted: number;
    inferred: number;
}

/**
 * Mapping-set counts by curation category (ADR-0027). `total` covers asserted sets only — the synthetic
 * cross-set inferences set is excluded — so `curated + ontologies` accounts for it, except on data
 * indexed before `mapping_set_category` existed, where uncategorised sets fall into neither bucket.
 *
 * `ontologies` is the number of ontology-derived sets, one per ontology OxO2 has loaded. It is NOT the
 * `/api/v2/ontologies` count, which is every CURIE prefix appearing anywhere in the mappings index.
 */
export interface MappingSetCounts {
    total: number;
    curated: number;
    ontologies: number;
}

export interface DataContent {
    /** ISO-8601 UTC instant of the dataload run that produced the corpus, or null if none is recorded. */
    releaseDate: string | null;
    mappings: MappingCounts;
    mappingSets: MappingSetCounts;
}

/** The corpus summary shown in the landing page's Data Content block. */
export function fetchDataContent(): Promise<DataContent> {
    return get<DataContent>('/api/v2/data-content');
}
