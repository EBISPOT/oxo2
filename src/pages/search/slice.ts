import {
  createAsyncThunk,
  createSlice,
  PayloadAction
} from "@reduxjs/toolkit";
import { get } from "../../app/api";
import Mapping from "../../model/Mapping";

export interface SearchState {
  mappings: Mapping[];
  pagination: { previous: string; next: string };
  loadingSearch: boolean;
}
const initialState: SearchState = {
  mappings: [],
  pagination: {
    previous: "",
    next: "",
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
export const getMappingsByEntityId = createAsyncThunk(
  "search_entity",
  async ({ entityId, limit, page }: any, { rejectWithValue }) => {
    try {
      const searchResponse = await get<Page<Mapping>>(
        `/entities/${encodeURIComponent(entityId)}?${new URLSearchParams({
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
      getMappingsByEntityId.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Mapping>>) => {
        state.mappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.loadingSearch = false;
      }
    );
    builder.addCase(getMappingsByEntityId.pending, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = true;
    });
    builder.addCase(getMappingsByEntityId.rejected, (state: SearchState) => {
      state.mappings = initialState.mappings;
      state.pagination = initialState.pagination;
      state.loadingSearch = false;
    });
  },
});

export interface Page<T> {
  data: T[];
  pagination: {
    next: string;
    previous: string;
  };
}

export default searchSlice.reducer;
