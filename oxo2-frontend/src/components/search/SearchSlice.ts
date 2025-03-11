import {createSlice } from '@reduxjs/toolkit'
import type { PayloadAction } from '@reduxjs/toolkit'


export interface SearchInput {
    userSearchInput: string;
    sanitizedSearchInput: string[]
}



export const initialState: SearchInput = {
    userSearchInput: '',
    sanitizedSearchInput: [],
}

export const searchSlice = createSlice({
    name: 'search',
    initialState,
    reducers: {
        setSearchInput: (state, action: PayloadAction<string>) => {
            state.userSearchInput = action.payload;
            state.sanitizedSearchInput = action.payload.split('\n');
        }
    }
})


// export const { setSearchInput } = searchSlice.actions;


export default searchSlice.reducer;