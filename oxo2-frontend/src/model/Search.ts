export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[]
}

export const initialSearchState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: []
}
