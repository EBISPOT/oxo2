import { LabelMatchMode, DEFAULT_LABEL_MATCH } from "./LabelMatchMode";
import { CorpusMode, DEFAULT_CORPUS } from "./MappingSetCategory";
import { SortMode, DEFAULT_SORT_MODE } from "./SortMode";

export interface AdvancedFieldQuery {
    field: string;
    value: string;
}

/** Single term vs. a batch of terms. Only changes the entry control, never the query semantics. */
export type SearchMode = 'single' | 'multiple';

export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[];
    mappingSetIds?: string[];
    advancedFieldQueries?: AdvancedFieldQuery[];
    activeTab?: 'search' | 'advanced';
    searchMode?: SearchMode;
    // Cross-ontology mapping (ADR-0024): source/target ontology (CURIE) prefixes.
    subjectPrefixes?: string[];
    objectPrefixes?: string[];
    // Label match mode (ADR-0026): how free-text label queries match.
    labelMatch?: LabelMatchMode;
    // Which asserted corpora to search (ADR-0027).
    corpus?: CorpusMode;
    // How results are ordered; "Best match" means the provenance-led ranking.
    sortBy?: SortMode;
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
    mappingSetIds: undefined,
    advancedFieldQueries: undefined,
    activeTab: 'search',
    searchMode: 'single',
    subjectPrefixes: undefined,
    objectPrefixes: undefined,
    labelMatch: DEFAULT_LABEL_MATCH,
    corpus: DEFAULT_CORPUS,
    sortBy: DEFAULT_SORT_MODE,
}
