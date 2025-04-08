import { useNavigate } from "react-router-dom";
import { SearchInput, initialSearchState } from "../../model/Search";
import { useState } from "react";
import React from "react";

export function Search({searchInput = initialSearchState, showWelcome = false }: {
    searchInput: SearchInput,
    showWelcome?: boolean
}) {
    const navigate = useNavigate();
    const [searchState, setSearchState] = useState<SearchInput>(searchInput);

    const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
        const userSearchInput = event.target.value;
        const sanitizedSearchInput = userSearchInput.split(/[\n,]+/).filter(item => item.trim() !== '');
        setSearchState({ userSearchInput, sanitizedSearchInput });
    };

    const handleSearch = () => {
        if (searchState.userSearchInput && searchState.userSearchInput.trim() !== "") {
            navigate("/search", {state: {searchState}});
        }
    };

    const handleClear = () => {
        setSearchState({
            userSearchInput: "",
            sanitizedSearchInput: []
        });
    };

    return  (
        <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
            { showWelcome && (
                <div className="text-primary">
                    Welcome to the EMBL-EBI OxO Mapping Service
                </div>
            )}
            <div className="flex flex-col md:flex-row gap-4">
                <div className="w-full">
                    <div className="flex flex-col md:flex-row justify-between mb-2">
                        <div className="text-tertiary">
                            Enter identifiers (CURIE format) separated by comma or newline:
                        </div>
                        <div
                            className="link-default md:mx-0.5"
                            onClick={() => {
                                setSearchState({
                                    userSearchInput: "UBERON:0002107\nHP:0000518\nMP:0001289\nMP:0000564",
                                    sanitizedSearchInput: ["UBERON:0002107", "HP:0000518", "MP:0001289", "MP:0000564"]
                                });
                            }}
                        >
                            Examples...
                        </div>
                    </div>
                    <textarea
                        id="home-search"
                        rows={2}
                        style={{ resize: "vertical", minHeight: "6rem" }}
                        placeholder={"Search OxO..."}
                        className="input-default text-lg"
                        value={ searchState.userSearchInput }
                        onChange={ handleInputChange }
                    />
                </div>
                <div className="flex flex-col gap-2 md:mt-10">
                    <button
                        className="button-primary text-base font-bold px-4 py-1"
                        onClick={ handleSearch }
                    >
                        Search
                    </button>
                    <button
                        className="button-primary text-base font-bold px-4 py-1"
                        onClick={ handleClear }
                    >
                        Clear
                    </button>
                </div>
            </div>
        </div>
    )
}
