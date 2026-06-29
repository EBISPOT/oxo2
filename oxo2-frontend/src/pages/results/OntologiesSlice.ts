import { get } from '../../app/api';

// An ontology (CURIE prefix) present in the mappings index, with subject/object counts (ADR-0024).
export interface Ontology {
    prefix: string;
    asSubject: number;
    asObject: number;
}

// A target ontology reachable from a chosen source, with the mapping count into it.
export interface OntologyTarget {
    prefix: string;
    count: number;
}

/** All ontologies (CURIE prefixes) in the index, with subject/object counts. Drives the from selector. */
export function fetchOntologies(): Promise<Ontology[]> {
    return get<Ontology[]>('/api/v2/ontologies');
}

/** Target ontologies reachable from a source ontology, with per-target counts. Drives the to selector. */
export function fetchTargetsForSubject(sourcePrefix: string): Promise<OntologyTarget[]> {
    return get<OntologyTarget[]>(`/api/v2/ontologies?forSubject=${encodeURIComponent(sourcePrefix)}`);
}
