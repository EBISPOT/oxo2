import { LabelMatchMode, DEFAULT_LABEL_MATCH } from "./LabelMatchMode";
import { CorpusMode, DEFAULT_CORPUS } from "./MappingSetCategory";
import { WeakPredicate, DEFAULT_WEAK_PREDICATES } from "./WeakPredicate";

/** Single term vs. a batch of terms. Only changes the entry control, never the query semantics. */
export type SearchMode = 'single' | 'multiple';

export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[];
    mappingSetIds?: string[];
    searchMode?: SearchMode;
    // Cross-ontology mapping (ADR-0024): source/target ontology (CURIE) prefixes.
    subjectPrefixes?: string[];
    objectPrefixes?: string[];
    // Label match mode (ADR-0026): how free-text label queries match.
    labelMatch?: LabelMatchMode;
    // Which asserted corpora to search (ADR-0027).
    corpus?: CorpusMode;
    // Normally-hidden predicates the user has asked to see (ADR-0035). Omitted = both hidden. Also
    // filters the typeahead, so that it can only offer entities the resulting search can show.
    includeWeakPredicates?: WeakPredicate[];
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
    mappingSetIds: undefined,
    searchMode: 'single',
    subjectPrefixes: undefined,
    objectPrefixes: undefined,
    labelMatch: DEFAULT_LABEL_MATCH,
    corpus: DEFAULT_CORPUS,
    includeWeakPredicates: DEFAULT_WEAK_PREDICATES,
}
