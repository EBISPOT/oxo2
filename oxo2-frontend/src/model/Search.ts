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
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
    mappingSetIds: undefined,
    advancedFieldQueries: undefined,
    activeTab: 'search',
}
