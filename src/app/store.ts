import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/slice";
import searchReducer from "../pages/search/slice";

export const store = configureStore({
  reducer: {
    home: homeReducer,
    search: searchReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
