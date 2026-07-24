// Mapping justification display (SSSOM `mapping_justification`).
//
// SSSOM requires the justification to be a term from the SEMAPV "matching process" subtree
// (https://w3id.org/semapv/vocab/Matching). The index stores it as a CURIE — normalised to the
// upper-case prefix `SEMAPV:` (see the backend's normalisedCurie) — but data in the wild also
// arrives lower-cased, as a full IRI, or, for non-conformant files, as arbitrary free text.
//
// For display we show a known SEMAPV term by its CamelCase local name (`MappingChaining`) — short
// enough to scan down a table column and to type into a filter — and keep the full rdfs:label
// ("mapping chaining-based matching process") for the hover tooltip and detail views, where there is
// room to spell out the intent. Anything we can't recognise as a SEMAPV term is shown verbatim, so
// arbitrary text survives untouched.
//
// This is presentational only: search filters, the vocab typeahead, and TSV export keep the raw
// CURIE, which is what Solr and the SSSOM export are matched against.

// rdfs:label per SEMAPV term, keyed by the IRI local name (the part after the `semapv:` prefix /
// the last `/` of the IRI). Sourced from the SEMAPV vocabulary
// (mapping-commons/semantic-mapping-vocabulary, semapv-terms.tsv): the `semapv:Matching` root and
// every descendant — the terms a conformant `mapping_justification` can hold — plus
// `semapv:MappingReview`, a mapping-activity term that appears as a justification in loaded corpora.
export const SEMAPV_MATCHING_LABELS: Record<string, string> = {
    Matching: 'matching process',
    MappingReview: 'mapping review',
    LexicalMatching: 'lexical matching process',
    LogicalReasoning: 'logical reasoning matching process',
    CompositeMatching: 'composite matching process',
    UnspecifiedMatching: 'unspecified matching process',
    SemanticSimilarityThresholdMatching: 'semantic similarity threshold-based matching process',
    LexicalSimilarityThresholdMatching: 'lexical similarity threshold-based matching process',
    StructuralMatching: 'structural matching process',
    BoundedPathMatching: 'bounded path matching process',
    InstanceBasedMatching: 'instance-based matching process',
    BackgroundKnowledgeBasedMatching: 'background knowledge-based matching process',
    MappingChaining: 'mapping chaining-based matching process',
    MappingInversion: 'mapping inversion-based matching process',
    ManualMappingCuration: 'manual mapping curation',
    MachineLearningBasedMatching: 'machine learning-based matching process',
    EmbeddingBasedMatching: 'embedding-based matching process',
    TransformerBasedMatching: 'transformer-based matching process',
    GraphRepresentationLearningBasedMatching: 'graph representation learning-based matching process',
    LLMBasedMatching: 'LLM-based matching process',
    AgentBasedMatching: 'agent-based matching process',
};

const SEMAPV_IRI_PREFIX = 'https://w3id.org/semapv/vocab/';

// Extract the SEMAPV term local name from a justification value, or null if it is not a SEMAPV
// term. Accepts the `semapv:`/`SEMAPV:` CURIE prefix (case-insensitive) and the full vocab IRI
// (either scheme). A value that matches neither shape is treated as free text by the caller.
function semapvLocalName(raw: string): string | null {
    const trimmed = raw.trim();

    const iri = trimmed.replace(/^http:/, 'https:');
    if (iri.startsWith(SEMAPV_IRI_PREFIX)) {
        const localName = iri.slice(SEMAPV_IRI_PREFIX.length);
        return localName.length > 0 ? localName : null;
    }

    const curieMatch = /^semapv:(.+)$/i.exec(trimmed);
    return curieMatch ? curieMatch[1] : null;
}

// Compact display of a `mapping_justification` value: a recognised SEMAPV matching-process term
// renders as its CamelCase local name (`MappingChaining`); any other value (SEMAPV terms outside our
// table, non-SSSOM free text) is returned verbatim. This is what the result tables and the filter
// dropdowns show — pair it with `mappingJustificationLabel` for the tooltip.
export function mappingJustificationShortName(raw?: string | null): string {
    if (!raw) return '';

    const localName = semapvLocalName(raw);
    if (localName && localName in SEMAPV_MATCHING_LABELS) {
        return localName;
    }
    return raw;
}

// Full rdfs:label of a `mapping_justification` value ("mapping chaining-based matching process"), for
// the hover tooltip beside the short name and for detail views. Same recognition and verbatim
// fallback as `mappingJustificationShortName`.
export function mappingJustificationLabel(raw?: string | null): string {
    if (!raw) return '';

    const localName = semapvLocalName(raw);
    if (localName && localName in SEMAPV_MATCHING_LABELS) {
        return SEMAPV_MATCHING_LABELS[localName];
    }
    return raw;
}
