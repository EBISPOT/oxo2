import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get } from "../../app/api";
import Mapping from "../../model/Mapping";
import { Page, Pagination } from "../search/slice";

export interface MappingState {
  mapping: Mapping | undefined;
  otherMappings: Mapping[];
  loadingMapping: boolean;
  loadingMappings: boolean;
  pagination: Pagination;
  facets: any;
}
const initialState: MappingState = {
  mapping: undefined,
  otherMappings: [],
  loadingMapping: false,
  loadingMappings: false,
  pagination: {
    previous: "",
    next: "",
    page_number: 0,
    total_items: 0,
    total_pages: 0,
  },
  facets: {},
};

export const getMapping = createAsyncThunk(
  "mapping_get",
  async (id: string, { rejectWithValue }) => {
    try {
      return await get<Mapping>("/ui/mappings/" + id);
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

export const getMappings = createAsyncThunk(
  "mapping_search",
  async (
    { subjectId, predicateId, objectId, limit, page }: any,
    { rejectWithValue }
  ) => {
    try {
      const searchParams = new URLSearchParams();
      if (subjectId) searchParams.append("subject_id", subjectId);
      if (predicateId) searchParams.append("predicate_id", predicateId);
      if (objectId) searchParams.append("object_id", objectId);
      searchParams.append("limit", limit);
      searchParams.append("page", page);
      const searchResponse = await get<Page<Mapping>>(
        `/ui/mappings/?${searchParams}`
      );
      return searchResponse;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
const mappingSlice = createSlice({
  name: "mapping",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getMapping.fulfilled,
      (state: MappingState, action: PayloadAction<Mapping>) => {
        state.mapping = new Mapping(action.payload);
        state.loadingMapping = false;
      }
    );
    builder.addCase(getMapping.pending, (state: MappingState) => {
      state.mapping = initialState.mapping;
      state.loadingMapping = true;
    });
    builder.addCase(getMapping.rejected, (state: MappingState) => {
      state.mapping = initialState.mapping;
      state.loadingMapping = false;
    });
    builder.addCase(
      getMappings.fulfilled,
      (state: MappingState, action: PayloadAction<Page<Mapping>>) => {
        state.otherMappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.pagination = action.payload.pagination;
        state.facets = action.payload.facets;
        state.loadingMappings = false;
      }
    );
    builder.addCase(getMappings.pending, (state: MappingState) => {
      state.otherMappings = initialState.otherMappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingMappings = true;
    });
    builder.addCase(getMappings.rejected, (state: MappingState) => {
      state.otherMappings = initialState.otherMappings;
      state.pagination = initialState.pagination;
      state.facets = initialState.facets;
      state.loadingMappings = false;
    });
  },
});

export default mappingSlice.reducer;
