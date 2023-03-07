import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get } from "../../app/api";

export interface HomeState {
  stats: Stats;
  loadingStats: boolean;
}
export interface Stats {
  nb_mapping: number;
  nb_mapping_set: number;
  nb_mapping_provider: number;
  nb_entity: number;
}
const initialState: HomeState = {
  stats: {
    nb_mapping: 0,
    nb_mapping_set: 0,
    nb_mapping_provider: 0,
    nb_entity: 0,
  },
  loadingStats: false,
};

export const getStats = createAsyncThunk(
  "home_stats",
  async (param, { rejectWithValue }) => {
    try {
      return await get<Stats>(`/stats`);
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const homeSlice = createSlice({
  name: "home",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getStats.fulfilled,
      (state: HomeState, action: PayloadAction<Stats>) => {
        state.stats = action.payload;
        state.loadingStats = false;
      }
    );
    builder.addCase(getStats.pending, (state: HomeState) => {
      state.stats = initialState.stats;
      state.loadingStats = true;
    });
    builder.addCase(getStats.rejected, (state: HomeState) => {
      state.stats = initialState.stats;
      state.loadingStats = false;
    });
  },
});

export default homeSlice.reducer;
