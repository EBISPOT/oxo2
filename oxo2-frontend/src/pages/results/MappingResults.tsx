import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import { FacetedMapping, fetchMappings, fromJson, emptyFacetedMapping } from "./MappingResultsSlice";
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from "react-router-dom";
import { MappingItem } from "../../components/mapping/MappingItem";
import { ErrorInfo } from "../../components/error/ErrorInfo";

function MappingResults(searchInput: SearchInput) {
    const navigate = useNavigate();

    const { data, isLoading, error } = useQuery({
        queryKey: ["fetchMappings", searchInput.sanitizedSearchInput],
        queryFn: () => fetchMappings(searchInput.sanitizedSearchInput),
        staleTime: Infinity
    });

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    return (
        <div>
            <Search searchInput={searchInput} />

            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-border text-primary" role="status">
                        Loading...
                    </div>
                </div>
            )}

            {error &&
                <ErrorInfo task={"fetching mappings"} message={error.message}/>
            }

            {!isLoading && !error && mappingResults.mappings.length === 0 && (
                <div className="bg-blue-100 text-blue-700 p-4 rounded-lg my-4">
                    No mapping results found for your search.
                </div>
            )}

            {!isLoading && !error && mappingResults.mappings.length > 0 && (
                <ul>
                    {mappingResults.mappings.map((mapping) => (
                        <MappingItem
                            key={mapping.mappingId}
                            mapping={mapping}
                            navigateFn={navigate}
                        />
                    ))}
                </ul>
            )}
        </div>
    );
}


export default MappingResults;