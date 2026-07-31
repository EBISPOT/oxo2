import { useLocation, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import MappingDetails from "./MappingDetails";
import { fetchMappingById } from "./MappingResultsSlice";
import { Mapping } from "../../model/Mapping";

/**
 * Route component for /mapping/:id. The mapping is always fetched by the mapping_id in the URL, so
 * the page works on direct navigation (shared links, bookmarks) as well as click-through. A mapping
 * passed through router state by a results table is only placeholder data: it paints instantly but
 * carries just the search fieldList, and is replaced by the full document (provenance, mapping-set
 * metadata, subject/object details) when the fetch lands.
 */
function MappingDetailsPage() {
    const { id } = useParams<{ id: string }>();
    const location = useLocation();
    const stateMapping: Mapping | undefined = location.state?.mapping;

    const { data, isPending, isError } = useQuery({
        queryKey: ["mappingById", id],
        queryFn: () => fetchMappingById(id!),
        enabled: !!id,
        placeholderData: stateMapping,
        staleTime: 5 * 60 * 1000,
    });

    // While pending, data is the placeholder (the router-state mapping) when there is one.
    if (data) {
        return <MappingDetails mapping={data} />;
    }
    if (isPending) {
        return (
            <div className="flex justify-center p-8">
                <div className="spinner-default w-10 h-10 animate-spin" role="status">
                    <span className="sr-only">Loading...</span>
                </div>
            </div>
        );
    }
    if (isError) {
        // The fetch failed outright (not a 404); fall back to whatever the results table passed.
        if (stateMapping) {
            return <MappingDetails mapping={stateMapping} />;
        }
        return (
            <div className="container mx-auto px-4 py-8">
                <div className="alert-error">Could not load mapping {id}.</div>
            </div>
        );
    }
    return (
        <div className="container mx-auto px-4 py-8">
            <div className="alert-info">No mapping found with id {id}.</div>
        </div>
    );
}

export default MappingDetailsPage;
