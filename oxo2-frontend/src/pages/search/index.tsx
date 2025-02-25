import React, { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { RootState, AppDispatch } from "../../app/store";
import { fetchMappings, SearchStatus } from "./slice";

export default function Search({ appRef }: { appRef: any }) {
    const dispatch = useDispatch<AppDispatch>();
    const {mappingResponse, status, error } = useSelector((state: RootState) => state.search);

    useEffect(() => {
        const queries = appRef.current.searchQuery
            .split(/[\n,]+/)
            .map(id => id.trim())
            .filter(Boolean);
        if (queries.length) {
            dispatch(fetchMappings(queries));
        }
    }, [appRef]);


    return (
        <div>
            <h1>Search Results</h1>
            {status === SearchStatus.Loading && <p>Loading...</p>}
            {status === SearchStatus.Failed && <p>Error: {error}</p>}
            {status === SearchStatus.Succeeded && (
                <ul>
                    {   mappingResponse && mappingResponse.mappings && mappingResponse.mappings.content &&
                        mappingResponse.mappings.content.map((mapping, index) => (
                        <li key={index}>
                            <p>Mapping Set ID: {mapping.mappingSetId}</p>
                            <p>Subject ID: {mapping.subjectId}</p>
                            <p>Subject Label: {mapping.subjectLabel}</p>
                            <p>Subject ID Prefix: {mapping.subjectIdPrefix}</p>
                            <p>Predicate ID: {mapping.predicateId}</p>
                            <p>Predicate Label: {mapping.predicateLabel}</p>
                            <p>Predicate Modifier: {mapping.predicateModifier}</p>
                            <p>Object ID: {mapping.objectId}</p>
                            <p>Object Label: {mapping.objectLabel}</p>
                            <p>Object ID Prefix: {mapping.objectIdPrefix}</p>
                            <p>Mapping Justification: {mapping.mappingJustification}</p>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
};


