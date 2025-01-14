import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { post } from "../../app/api";
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

export const getMappingsByEntities = createAsyncThunk(
  "search_mappings_by_entities",
  async ({ entityIds, facetIds, confidence, limit, page }: any, { rejectWithValue }) => {
    let searchBody = JSON.parse(JSON.stringify(facetIds));
    searchBody["curies"] = entityIds;
    searchBody["confidence"] = JSON.parse(JSON.stringify(confidence));
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
      getMappingsByEntities.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Mapping>>) => {
        state.mappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.facets = action.payload.facets;
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsByEntities.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsByEntities.rejected, (state: SearchState) => {
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
