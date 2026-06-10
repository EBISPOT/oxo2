import { useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { fetchMappingSetById } from "./MappingSetsSlice";
import { NormalResultsTable } from "./NormalResultsTable";
import { INFERENCE_TYPE_ORDER } from "../../model/InferenceType";
import { InferenceTypeBadge } from "../../components/mapping/InferenceTypeBadge";

const INFERENCES_BASE = "https://www.ebi.ac.uk/oxo2/inferences";

/**
 * Resolution surface for inferred mapping sets (ADR-0012). The route /inferences resolves the
 * single cross-set SSSOM set; /inferences/<encoded source id> resolves a per-source inferred set. The
 * full mapping_set_id is reconstructed from the raw path tail so a percent-encoded source id
 * survives as one path segment.
 */
function InferencesPage() {
    const location = useLocation();
    const marker = "/inferences";
    const markerIndex = location.pathname.indexOf(marker);
    const tail = markerIndex >= 0 ? location.pathname.slice(markerIndex + marker.length) : "";
    const mappingSetId = INFERENCES_BASE + tail;

    const { data: mappingSet, isLoading, isError } = useQuery({
        queryKey: ["mappingSetById", mappingSetId],
        queryFn: () => fetchMappingSetById(mappingSetId),
        staleTime: Infinity,
    });

    return (
        <div className="px-4 py-6 max-w-screen-2xl mx-auto">
            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-default w-10 h-10 animate-spin" role="status">
                        <span className="sr-only">Loading...</span>
                    </div>
                </div>
            )}
            {isError && (
                <div className="text-red-700">Could not load inferred mapping set: {mappingSetId}</div>
            )}
            {mappingSet && (
                <div className="mb-4">
                    <div className="flex items-center gap-2">
                        <h1 className="text-2xl font-semibold break-words">
                            {mappingSet.mappingSetTitle || "Inferred mappings"}
                        </h1>
                        <InferenceTypeBadge value={mappingSet.inferenceType} />
                    </div>
                    {mappingSet.mappingSetDescription && (
                        <p className="text-gray-600 mt-1">{mappingSet.mappingSetDescription}</p>
                    )}
                    <p className="text-xs text-gray-500 mt-1 break-all">{mappingSet.mappingSetId}</p>
                    {mappingSet.mappingSetSource.length > 0 && (
                        <div className="mt-2 text-sm">
                            <span className="font-medium">
                                Derived from {mappingSet.mappingSetSource.length} source set(s):
                            </span>
                            <ul className="list-disc ml-6 mt-1">
                                {mappingSet.mappingSetSource.map((source) => (
                                    <li key={source} className="break-all text-gray-700">{source}</li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}
            {/* Show every inference type within the set, regardless of the global default filter. */}
            <NormalResultsTable
                queries={[]}
                mappingSetIds={[mappingSetId]}
                initialInferenceTypes={INFERENCE_TYPE_ORDER}
            />
        </div>
    );
}

export default InferencesPage;
