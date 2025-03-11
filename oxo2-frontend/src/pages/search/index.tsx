import React, { useEffect, useState } from "react";
import { useAppSelector, useAppDispatch } from "../../app/hooks";
import { RootState, AppDispatch } from "../../app/store";
import { fetchMappings, SearchStatus } from "./slice";

export default function SearchResults() {
    const dispatch = useAppDispatch();
    // const { mappingResponse, status, error } = useAppSelector((state: RootState) => state.search);

    // useEffect(() => {
    //     const queries = appRef.current.searchQuery
    //         .split(/[\n,]+/)
    //         .map(id => id.trim())
    //         .filter(Boolean);
    //     if (queries.length) {
    //         dispatch(fetchMappings(queries));
    //     }
    // }, [appRef]);


    return (
        <div>
            <h1>Search Results</h1>
            {/*{status === SearchStatus.Loading && <p>Loading...</p>}*/}
            {/*{status === SearchStatus.Failed && <p>Error: {error}</p>}*/}
            {/*{status === SearchStatus.Succeeded && (*/}
            {/*    <ul>*/}
            {/*        {   mappingResponse && mappingResponse.mappings &&*/}
            {/*            mappingResponse.mappings.map((mapping, index) => (*/}
            {/*            <li key={index}>*/}
            {/*                <p>Mapping ID: {mapping.mappingId}</p>*/}
            {/*                <p>Mapping Set ID: {mapping.mappingSetId}</p>*/}
            {/*                <p>Subject ID: {mapping.subjectId}</p>*/}
            {/*                <p>Predicate ID: {mapping.predicateId}</p>*/}
            {/*                <p>Object ID: {mapping.objectId}</p>*/}
            {/*                <p>Mapping Justification: {mapping.mappingJustification}</p>*/}

            {/*            </li>*/}
            {/*        ))}*/}
            {/*    </ul>*/}
            {/*)}*/}
        </div>
    );
};


