import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import {FacetedMapping, fetchMappings, fromJson} from "./MappingResultsSlice.ts";
import { useQuery } from '@tanstack/react-query';

function MappingResults(searchInput: SearchInput) {

    const { data, isLoading, error } = useQuery({
        queryKey: ["fetchMappings"],
        queryFn: () => fetchMappings(searchInput.sanitizedSearchInput)
    });

    if (isLoading) return <div>Loading...</div>;
    if (error) return <div>Error: {error.message}</div>;
    const mappingResponse: FacetedMapping = fromJson(data);

    return (
        <div>
            <Search
                searchInput = {searchInput}
            />
            <h1>Search Results</h1>({
                <ul>

                    {   mappingResponse.mappings &&
                        mappingResponse.mappings.map((mapping, index) => (
                        <li key={index}>
                            <p>Mapping ID: {mapping.mappingId}</p>
                            <p>Mapping Set ID: {mapping.mappingSetId}</p>
                            <p>Subject ID: {mapping.subjectId}</p>
                            <p>Predicate ID: {mapping.predicateId}</p>
                            <p>Object ID: {mapping.objectId}</p>
                            <p>Mapping Justification: {mapping.mappingJustification}</p>
                        </li>
                    ))
                    }
                </ul>
        })
        </div>
    );
};

export default MappingResults;
