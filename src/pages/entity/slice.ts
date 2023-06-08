import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { post } from "../../app/api";
import Mapping from "../../model/Mapping";
import { Page } from "../search/slice";

export interface EntityState {
  entityMappings: Mapping[];
  loadingEntity: boolean;
}
const initialState: EntityState = {
  entityMappings: [],
  loadingEntity: false,
};

export const getEntities = createAsyncThunk(
  "entity_search",
  async (entityId: string, { rejectWithValue }) => {
    let searchBody = {
      curies: [entityId],
    };
    try {
      const searchResponse = await post<{ curies: string[] }, Page<Mapping>>(
        `/ui/entities/?${new URLSearchParams({
          limit: "100",
          page: "1",
        })}`,
        searchBody
      );
      return searchResponse;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const entitySlice = createSlice({
  name: "entity",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getEntities.fulfilled,
      (state: EntityState, action: PayloadAction<Page<Mapping>>) => {
        state.entityMappings = action.payload.data.map(
          (element) => new Mapping(element)
        );
        state.loadingEntity = false;
      }
    );
    builder.addCase(getEntities.pending, (state: EntityState) => {
      state.entityMappings = initialState.entityMappings;
      state.loadingEntity = true;
    });
    builder.addCase(getEntities.rejected, (state: EntityState) => {
      state.entityMappings = initialState.entityMappings;
      state.loadingEntity = false;
    });
  },
});

export default entitySlice.reducer;
