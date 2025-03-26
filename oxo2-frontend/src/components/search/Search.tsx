import { useNavigate } from "react-router-dom";
import { SearchInput, searchSlice } from "./SearchSlice";
import {useEffect, useState} from "react";
import {useAppDispatch, useAppSelector} from "../../app/hooks";
import {RootState} from "../../app/store";
import React from "react";

export function Search(searchInput: SearchInput) {
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { userSearchInput } = useAppSelector((state: RootState) => state.search);
    const [currentSearchInput, setSearchInputState] = useState<string>(userSearchInput);

    // Currently this is unnecessary, but when it may be useful when this componentt froms part of the
    // results page.
    // useEffect(() => {
    //     dispatch(searchSlice.actions.setSearchInput(currentSearchInput));
    // }, [currentSearchInput]);

    const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
        setSearchInputState(event.target.value);
        searchSlice.actions.setSearchInput(event.target.value);
    };

    const handleSearch = () => {
        navigate({ pathname: "/search" });
    };


    return  (
        <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
            <div className="text-primary">
                Welcome to the EMBL-EBI OxO Mapping Service
            </div>
            <div className="flex flex-col md:flex-row gap-4">
                <div className="w-full">
                    <div className="flex flex-col md:flex-row justify-between mb-2">
                        <div className="text-tertiary">
                            Enter identifiers (CURIE format) separated by comma or newline:
                        </div>
                        <div
                            className="link-default md:mx-0.5"
                            onClick={() => {
                                setSearchInputState("UBERON:0002107\nHP:0000518\nMP:0001289\nMP:0000564");
                            }}
                        >
                            Examples...
                        </div>
                    </div>
                    <textarea
                        id="home-search"
                        rows={2}
                        style={{ resize: "vertical", minHeight: "5rem" }}
                        placeholder={"Search OxO..."}
                        className="input-default text-lg"
                        value={currentSearchInput}
                        onChange={handleInputChange}
                    />
                </div>
                <button
                    className="button-primary text-lg font-bold self-end md:self-center"
                    onClick={handleSearch}
                >
                    Search
                </button>
            </div>
        </div>
    )
}
