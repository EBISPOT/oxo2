import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, post } from "../../app/api";
import Mapping from "../../model/Mapping";

export interface SearchState {
  mappings: Mapping[];
  pagination: Pagination;
  loadingSearch: boolean;
  facets: any;
}
const initialState: SearchState = {
  mappings: [],
  pagination: {
    previous: "",
    next: "",
    page_number: 0,
    total_items: 0,
    total_pages: 0,
  },
  facets: {},
  loadingSearch: false,
};

export const getMappingsAll = createAsyncThunk(
  "search_mappings_all",
  async ({ limit, page }: any, { rejectWithValue }) => {
    try {
      const searchResponse = await get<Page<Mapping>>(
        `/mappings/?${new URLSearchParams({
          limit,
          page,
        })}`
      );
      return searchResponse;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getMappingsByEntityIds = createAsyncThunk(
  "search_mappings_by_entities",
  async ({ entityIds, facetIds, limit, page }: any, { rejectWithValue }) => {
    var searchBody = facetIds;
    searchBody["curies"] = entityIds;
    try {
      const searchResponse = await post<{ curies: string[] }, Page<Mapping>>(
        `/ui/entities/?${new URLSearchParams({
          limit,
          page,
        })}`,
        searchBody
      );
      return searchResponse;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const searchSlice = createSlice({
  name: "search",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getMappingsAll.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Mapping>>) => {
        state.mappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.facets = action.payload.facets;
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsAll.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsAll.rejected, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingSearch = false;
    });
    builder.addCase(
      getMappingsByEntityIds.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Mapping>>) => {
        state.mappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.facets = action.payload.facets;
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsByEntityIds.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsByEntityIds.rejected, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingSearch = false;
    });
  },
});

export interface Page<T> {
  data: T[];
  pagination: Pagination;
  facets: any;
}

export interface Pagination {
  next: string;
  previous: string;
  page_number: number;
  total_items: number;
  total_pages: number;
}

export default searchSlice.reducer;
