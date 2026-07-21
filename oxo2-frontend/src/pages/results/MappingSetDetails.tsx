import { useQuery } from "@tanstack/react-query";
import { fetchMappingSetById } from "./MappingSetsSlice";
import { NormalResultsTable } from "./NormalResultsTable";
import { INFERENCE_TYPE_ORDER } from "../../model/InferenceType";
import { InferenceTypeBadge } from "../../components/mapping/InferenceTypeBadge";

/**
 * Detail surface for a single mapping set: a metadata header above the set's mappings. Backs both
 * the inferred-set resolution route (/inferences, ADR-0012) and the general clickable-set route
 * (/mapping-set), so there is one canonical set-detail layout.
 *
 * The mappings table runs UNGROUPED (groupBySpo={false}) on purpose. Same-SPO collapse (ADR-0023)
 * exists to merge a triple asserted across DIFFERENT sets; scoped to a single set it collapses
 * almost nothing, yet the CollapsingQParser would still allocate an ordinal map over every matching
 * doc — an unbounded cost that OOMs Solr on large sets (the 15.35M-row inferred set crashed a
 * 512 MB Solr this way). Ungrouped, the set-detail view is a plain paged scan.
 */
export function MappingSetDetails({ mappingSetId }: { mappingSetId: string }) {
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
                <div className="text-red-700">Could not load mapping set: {mappingSetId}</div>
            )}
            {mappingSet && (
                <div className="mb-4">
                    <div className="flex items-center gap-2">
                        <h1 className="text-2xl font-semibold break-words">
                            {mappingSet.mappingSetTitle || "Mapping set"}
                        </h1>
                        <InferenceTypeBadge value={mappingSet.inferenceType} />
                    </div>
                    {mappingSet.mappingSetDescription && (
                        <p className="text-gray-600 mt-1">{mappingSet.mappingSetDescription}</p>
                    )}
                    <p className="text-xs text-gray-500 mt-1 break-all">{mappingSet.mappingSetId}</p>
                    {mappingSet.mappingProvider && (
                        <p className="text-sm mt-1">
                            <span className="font-medium">Provider:</span> {mappingSet.mappingProvider}
                        </p>
                    )}
                    {mappingSet.creatorLabel.length > 0 && (
                        <p className="text-sm mt-1">
                            <span className="font-medium">Creators:</span> {mappingSet.creatorLabel.join(", ")}
                        </p>
                    )}
                    {/* The set's source sets ("Derived from N source set(s)") are intentionally not shown
                        here: at set level the list is long and unhelpful. This may return at the level of
                        an individual mapping, where provenance is meaningful. */}
                </div>
            )}
            {/* Show every inference type within the set, regardless of the global default filter.
                variant="set-detail" drops the Type/Mapping-set columns (constant within one set),
                shows Mapping confidence, and hides Mapping provider by default. */}
            <NormalResultsTable
                queries={[]}
                mappingSetIds={[mappingSetId]}
                initialInferenceTypes={INFERENCE_TYPE_ORDER}
                groupBySpo={false}
                variant="set-detail"
            />
        </div>
    );
}

export default MappingSetDetails;
