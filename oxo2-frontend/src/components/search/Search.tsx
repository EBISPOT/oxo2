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
        const sanitizedSearchInput = userSearchInput.split('\n');
        setSearchState({ userSearchInput, sanitizedSearchInput });
    };

    const handleSearch = () => {
        navigate("/search", { state: { searchState } });
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
                        style={{ resize: "vertical", minHeight: "5rem" }}
                        placeholder={"Search OxO..."}
                        className="input-default text-lg"
                        value={ searchState.userSearchInput }
                        onChange={ handleInputChange }
                    />
                </div>
                <button
                    className="button-primary text-lg font-bold self-end md:self-center"
                    onClick={ handleSearch }
                >
                    Search
                </button>
            </div>
        </div>
    )
}
