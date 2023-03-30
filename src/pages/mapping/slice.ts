import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get } from "../../app/api";
import Mapping from "../../model/Mapping";

export interface MappingState {
  mapping: Mapping | undefined;
  loadingMapping: boolean;
}
const initialState: MappingState = {
  mapping: undefined,
  loadingMapping: false,
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
  },
});

export default mappingSlice.reducer;
