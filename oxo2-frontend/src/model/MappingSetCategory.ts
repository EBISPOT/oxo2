// Which asserted corpora a search covers (ADR-0027). The codes match the backend MappingSetCategory
// enum and are sent verbatim as the request `mappingSetCategory` list.
//
// Inferred mappings carry no category and always pass the backend's corpus filter — an inference
// chains premises from several sets, so it belongs to no single corpus. Whether inferences appear at
// all is the separate inference-type filter's job.

export type MappingSetCategory = 'ONTOLOGY' | 'CURATED';

// What the user picks. "Both" is the default and sends no filter at all, which is also what keeps
// results flowing before the reindex that populates mapping_set_category.
export type CorpusMode = 'BOTH' | 'ONTOLOGY' | 'CURATED';

export const DEFAULT_CORPUS: CorpusMode = 'BOTH';

export const CORPUS_LABELS: Record<CorpusMode, string> = {
    BOTH: 'Both',
    ONTOLOGY: 'Ontologies',
    CURATED: 'Curated sets',
};

/** One-line explanation shown under the control, so the choice isn't jargon. */
export const CORPUS_HINTS: Record<CorpusMode, string> = {
    BOTH: 'Everything OxO knows about.',
    ONTOLOGY: "Cross-references an ontology asserts about its own terms.",
    CURATED: 'Mappings published as curated SSSOM files, such as EVORA or Mapping Commons.',
};

export const CORPUS_ORDER: CorpusMode[] = ['ONTOLOGY', 'CURATED', 'BOTH'];

/** URL `corpus` param value, or undefined for the default (which stays out of the URL). */
export function corpusToUrlParam(mode: CorpusMode): string | undefined {
    return mode === DEFAULT_CORPUS ? undefined : mode.toLowerCase();
}

/** Coerce the URL `corpus` param to a mode, defaulting when absent or unrecognised. */
export function asCorpusMode(value?: string | null): CorpusMode {
    const upper = value?.toUpperCase();
    return upper === 'ONTOLOGY' || upper === 'CURATED' ? upper : DEFAULT_CORPUS;
}

/** The backend request field: a category list, or undefined for "every corpus" (no filter clause). */
export function corpusToRequest(mode: CorpusMode): MappingSetCategory[] | undefined {
    return mode === 'BOTH' ? undefined : [mode];
}
