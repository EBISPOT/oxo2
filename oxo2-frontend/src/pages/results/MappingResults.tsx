import { useState, useEffect } from "react";
import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import { FacetedMapping, fetchMappings, fromJson, emptyFacetedMapping } from "./MappingResultsSlice";
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from "react-router-dom";
import { MappingItem } from "../../components/mapping/MappingItem";
import { ErrorInfo } from "../../components/error/ErrorInfo";
import { Paging } from "../../components/paging/Paging";

function MappingResults(searchInput: SearchInput) {
    const navigate = useNavigate();
    const [currentPage, setCurrentPage] = useState(0);
    const [pageSize, setPageSize] = useState(10); // Add page size state

    // Reset page when search input changes
    useEffect(() => {
        setCurrentPage(0);
    }, [searchInput.sanitizedSearchInput]);

    const { data, isLoading, error } = useQuery({
        queryKey: ["fetchMappings", searchInput.sanitizedSearchInput, currentPage, pageSize],
        queryFn: () => fetchMappings(searchInput.sanitizedSearchInput, currentPage, pageSize), // Update API call
        staleTime: Infinity
    });

    const handlePageSizeChange = (newPageSize: number) => {
        setPageSize(newPageSize);
        setCurrentPage(0); // Reset to first page when changing page size
    };

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    const handlePageChange = (newPage: number) => {
        if (newPage >= 0 && newPage < mappingResults.totalPages) {
            setCurrentPage(newPage);
        }
    };

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
                <div className="bg-orange-100 text-orange-700 p-4 rounded-lg my-4">
                    No mapping results found for your search.
                </div>
            )}

            {!isLoading && !error && mappingResults.mappings.length > 0 && (
                <div>
                    <Paging
                        currentPage={mappingResults.number}
                        totalPages={mappingResults.totalPages}
                        totalElements={mappingResults.totalElements}
                        pageSize={pageSize}
                        onPageChange={handlePageChange}
                        onPageSizeChange={handlePageSizeChange}
                    />
                    <ul>
                        {mappingResults.mappings.map((mapping) => (
                            <MappingItem
                                key={mapping.mappingId}
                                mapping={mapping}
                                navigateFn={navigate}
                            />
                        ))}
                    </ul>

                    <Paging
                        currentPage={mappingResults.number}
                        totalPages={mappingResults.totalPages}
                        totalElements={mappingResults.totalElements}
                        pageSize={pageSize}
                        onPageChange={handlePageChange}
                        onPageSizeChange={handlePageSizeChange}
                    />
                </div>
            )}
        </div>
    );
}

export default MappingResults;