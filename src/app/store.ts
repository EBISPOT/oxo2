import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/slice";

export const store = configureStore({
  reducer: {
    home: homeReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
