import {
    createAsyncThunk,
    createSlice,
    AsyncThunk,
    PayloadAction,
    Draft,
    Slice,
    ActionReducerMapBuilder
} from '@reduxjs/toolkit';
import {MappingResponse, Mapping, MappingFields} from '../../model/Mapping';
import {post} from "../../app/api";

interface IFacetedMappingResponse {
    mappings: {
        content: MappingResponse[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
    facets: Record<string, Record<string, number>>;
}

export interface IFacetedMapping {
    mappings: Mapping[];
    facets: Record<string, Record<string, number>>;
}

interface ISearchRequest {
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


interface ISearchState {
    searchInput: string;
    sanitizedSearchInput: string[];
    mappingResponse: IFacetedMapping;
    status: SearchStatus;
    error: string;
}


const emptyMappingResponse: IFacetedMapping = {
    mappings: [],
    facets: {},
}

const initialState: ISearchState = {
    searchInput: '',
    sanitizedSearchInput: [],
    mappingResponse: emptyMappingResponse,
    status: SearchStatus.Idle,
    error: '',
};


function fromJson(json: IFacetedMappingResponse): IFacetedMapping {
    if (!json || !json.mappings || !json.mappings.content || !json.facets) {
        return emptyMappingResponse;
    }
    return {
        mappings: json.mappings.content.map(item => {
            return {
                mappingId: item.mapping_id,
                mappingJustification: item.mapping_justification || '',
                mappingSetId: item.mapping_set_id,
                objectId: item.object_id || '',
                predicateId: item.predicate_id || '',
                subjectId: item.subject_id || ''
            };
        }),
        facets: json.facets
    }
}

function parseString(input: string): string[] {
    return input.trim().split('\n').map(item => item.trim());
}

export const fetchMappings: AsyncThunk<
                                        IFacetedMappingResponse,
                                        string[],
                                        { rejectValue: string }> =
    createAsyncThunk<
                    IFacetedMappingResponse,
                    string[],
                    { rejectValue: string }>(
        'search/fetchMappings',
        async (queries: string[], { rejectWithValue }) => {

            const requestBody: ISearchRequest = {
                queries: queries,
                page: 0,
                size: 10,
                queryFields: [MappingFields.subjectId, MappingFields.objectId],
                fieldList: [],
                facets: [MappingFields.subjectIdPrefix, MappingFields.objectIdPrefix],
            };

            try {
                const searchResponse: IFacetedMappingResponse =
                    await post<ISearchRequest, IFacetedMappingResponse>(
                        '/api/v2/mappings/search',
                        requestBody);
                return searchResponse;
            } catch (error) {
                const details = 'Error fetching mappings for queries = ${queries}: ';
                return rejectWithValue(error instanceof Error ? details + error.message : details + 'Unknown error: ${error}');
            }
        }
);

/**
 * A function that accepts an initial state, an object full of reducer
 * functions, and a "slice name", and automatically generates
 * action creators and action types that correspond to the
 * reducers and state.
 *
 * declare const createSlice: <
 *      State,
 *      CaseReducers extends SliceCaseReducers<State>,
 *      Name extends string,
 *      Selectors extends SliceSelectors<State>,
 *      ReducerPath extends string = Name>
 *  (options: CreateSliceOptions<State, CaseReducers, Name, ReducerPath, Selectors>) =>
 *      Slice<State, CaseReducers, Name, ReducerPath, Selectors>;
 */

const searchSlice: Slice<
                        ISearchState,
                        { setSearchInput: (state: Draft<ISearchState>, action: PayloadAction<string>) => void },
                        "search",
                        string> =
    createSlice({
        name: 'search',
        initialState: initialState,
        reducers: {
            setSearchInput(state: Draft<ISearchState>, action: PayloadAction<string>) {
                state.searchInput = action.payload;
                state.sanitizedSearchInput = parseString(action.payload);
            },
        },
        extraReducers: (builder: ActionReducerMapBuilder<ISearchState>) => {
            builder
                .addCase(fetchMappings.pending, (state: Draft<ISearchState>) => {
                    state.status = SearchStatus.Loading;
                })
                .addCase(fetchMappings.fulfilled, (state: Draft<ISearchState>, action: PayloadAction<IFacetedMappingResponse>) => {
                    state.mappingResponse = fromJson(action.payload);
                    state.status = SearchStatus.Succeeded;
                })
                .addCase(fetchMappings.rejected, (state: Draft<ISearchState>, action: PayloadAction<string|undefined>) => {
                    state.status = SearchStatus.Failed;
                    state.error = action.payload || 'Unknown error';
                    state.mappingResponse = emptyMappingResponse;
                });
        },
});

// export const { setSearchInput } = searchSlice.actions;


// export default searchSlice.reducer;