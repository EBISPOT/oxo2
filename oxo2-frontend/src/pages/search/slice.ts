import {createAsyncThunk, createSlice} from '@reduxjs/toolkit';
import {Mapping, MappingFields} from '../../model/Mapping';
import {post} from "../../app/api";


export interface FacetedMappingResponse {
    mappings: {
        content: Mapping[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
    facets: Record<string, Record<string, number>>;
}

interface SearchRequest {
    queries: string[];
    page: number;
    size: number;
    queryFields: string[];
    fieldList: string[];
    facets: string[];
}

export enum SearchStatus {
    Idle = 'idle',
    Loading = 'loading',
    Succeeded = 'succeeded',
    Failed = 'failed'
}

interface SearchState {
    searchInput: string;
    mappingResponse?: FacetedMappingResponse;
    status: SearchStatus;
    error: string | null;
}

const initialState: SearchState = {
    searchInput: '',
    mappingResponse: undefined,
    status: SearchStatus.Idle,
    error: '',
};


export const fetchMappings = createAsyncThunk(
    'search/fetchMappings',
    async (queries: string[], { rejectWithValue }) => {

        const requestBody: SearchRequest = {
            queries: queries,
            page: 0,
            size: 10,
            queryFields: ['subject_id', 'object_id'],
            fieldList: ['mapping_set_id', 'subject_id', 'subject_label', 'subject_id_prefix', 'predicate_id', 'predicate_label', 'predicate_modifier', 'object_id', 'object_label', 'object_id_prefix', 'mapping_justification'],
            facets: ['object_id_prefix', 'subject_id_prefix'],
        };

        try {
            const searchResponse = await post<SearchRequest, FacetedMappingResponse>(
                '/api/v2/mappings/search',
                requestBody);
            return searchResponse;
        } catch (error: any) {
            return rejectWithValue(error.message);
        }
    }
);

const searchSlice = createSlice({
    name: 'search',
    initialState,
    reducers: {
        setSearchInput(state, action) {
            state.searchInput = action.payload;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchMappings.pending, (state) => {
                state.status = SearchStatus.Loading;
            })
            .addCase(fetchMappings.fulfilled, (state, action) => {
                state.mappingResponse = action.payload;
                state.mappingResponse.mappings.content = action.payload.mappings.content.map((item: any) => ({
                    ...Object.keys(MappingFields).reduce((acc, key) => {
                        acc[key] = item[MappingFields[key]];
                        return acc;
                    }, {} as Mapping)
                }));
                state.status = SearchStatus.Succeeded;
            })
            .addCase(fetchMappings.rejected, (state, action) => {
                state.status = SearchStatus.Failed;
                state.error = action.error.message ?? null;
            });
    },
});

export const { setSearchInput } = searchSlice.actions;


export default searchSlice.reducer;