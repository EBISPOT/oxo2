import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, post } from "../../app/api";
import Mapping from "../../model/Mapping";

export interface SearchState {
  mappings: Mapping[];
  pagination: Pagination;
  loadingSearch: boolean;
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
  loadingSearch: false,
};

export const getMappingsAll = createAsyncThunk(
  "search_all",
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
  "search_entities",
  async ({ entityIds, limit, page }: any, { rejectWithValue }) => {
    try {
      const searchResponse = await post<{ curies: string[] }, Page<Mapping>>(
        `/entities/?${new URLSearchParams({
          limit,
          page,
        })}`,
        { curies: entityIds }
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
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsAll.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsAll.rejected, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = false;
    });
    builder.addCase(
      getMappingsByEntityIds.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Mapping>>) => {
        state.mappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsByEntityIds.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsByEntityIds.rejected, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = false;
    });
  },
});

export interface Page<T> {
  data: T[];
  pagination: Pagination;
}

export interface Pagination {
  next: string;
  previous: string;
  page_number: number;
  total_items: number;
  total_pages: number;
}

export default searchSlice.reducer;
