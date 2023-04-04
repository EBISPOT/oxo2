import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/slice";
import searchReducer from "../pages/search/slice";
import mappingReducer from "../pages/mapping/slice";

export const store = configureStore({
  reducer: {
    home: homeReducer,
    search: searchReducer,
    mapping: mappingReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
