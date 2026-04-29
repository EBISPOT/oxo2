export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[];
    mappingSetIds?: string[];
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
    mappingSetIds: undefined
}
