import { LabelMatchMode, DEFAULT_LABEL_MATCH } from "./LabelMatchMode";

export interface AdvancedFieldQuery {
    field: string;
    value: string;
}

export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[];
    mappingSetIds?: string[];
    advancedFieldQueries?: AdvancedFieldQuery[];
    activeTab?: 'search' | 'advanced';
    // Cross-ontology mapping (ADR-0024): source/target ontology (CURIE) prefixes.
    subjectPrefixes?: string[];
    objectPrefixes?: string[];
    // Label match mode (ADR-0026): how free-text label queries match.
    labelMatch?: LabelMatchMode;
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
    mappingSetIds: undefined,
    advancedFieldQueries: undefined,
    activeTab: 'search',
    subjectPrefixes: undefined,
    objectPrefixes: undefined,
    labelMatch: DEFAULT_LABEL_MATCH,
}
