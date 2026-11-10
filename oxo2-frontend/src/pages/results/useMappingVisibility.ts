import {useQueries, useQuery} from "@tanstack/react-query";
import {fetchMappingSearch, SearchRequest} from "./MappingResultsSlice";
import {WEAK_PREDICATE_ORDER} from "../../model/WeakPredicate";

export type VisibilityOption = 'subClassOf' | 'hasDbXref' | 'obsolete';

export interface MappingVisibility {
    additional: Partial<Record<VisibilityOption, number>>;
    allTotal?: number;
    total?: number;
    loading: boolean;
    unavailable: boolean;
}

/** Shared with the table: observing its result here must not issue a second search. */
export function useMappingSearch(request: SearchRequest) {
    return useQuery({
        queryKey: ['fetchMappings', request],
        queryFn: ({signal}) => fetchMappingSearch(request, signal),
        staleTime: Infinity,
    });
}

/** Probe only unchecked options, preserving every other search restriction and SPO grouping.
 * One representative is the smallest page the API accepts. Totals cover the whole result set.
 * Normalising away paging/sorting lets counts survive pagination; deduplication avoids querying
 * the same combination twice when "all" is also the last unchecked option.
 */
export function useMappingVisibility(request: SearchRequest): MappingVisibility {
    const current = useMappingSearch(request);
    const weak = request.includeWeakPredicates ?? [];
    const selected: Record<VisibilityOption, boolean> = {
        subClassOf: weak.includes('subClassOf'),
        hasDbXref: weak.includes('hasDbXref'),
        obsolete: request.includeObsolete === true,
    };
    const variants = new Map<string, SearchRequest>();
    const keys: Partial<Record<VisibilityOption | 'all', string>> = {};
    for (const option of ['subClassOf', 'hasDbXref', 'obsolete', 'all'] as const) {
        if (option === 'all' ? Object.values(selected).every(Boolean) : selected[option]) continue;
        const probe: SearchRequest = {
            ...request,
            page: 0,
            size: 1,
            fieldList: ['mapping_id'],
            sortedFields: [],
            includeWeakPredicates: WEAK_PREDICATE_ORDER.filter(code =>
                selected[code] || option === code || option === 'all'),
            includeObsolete: selected.obsolete || option === 'obsolete' || option === 'all',
        };
        const key = JSON.stringify(probe);
        keys[option] = key;
        variants.set(key, probe);
    }
    const entries = [...variants];
    const probes = useQueries({queries: entries.map(([, probe]) => ({
        queryKey: ['mappingVisibility', probe],
        queryFn: ({signal}: {signal: AbortSignal}) => fetchMappingSearch(probe, signal),
        enabled: current.isSuccess,
        staleTime: Infinity,
        retry: false,
    }))});
    const totals = new Map(entries.map(([key], i) => [key, probes[i].data?.mappings.totalElements]));
    const total = current.isSuccess ? current.data.mappings.totalElements : undefined;
    const additional: MappingVisibility['additional'] = {};
    for (const option of ['subClassOf', 'hasDbXref', 'obsolete'] as const) {
        const key = keys[option];
        const count = key === undefined ? undefined : totals.get(key);
        if (total !== undefined && count !== undefined) additional[option] = Math.max(0, count - total);
    }
    return {
        additional,
        total,
        allTotal: total === undefined ? undefined : keys.all ? totals.get(keys.all) : total,
        loading: current.isLoading || (current.isSuccess && probes.some(probe => probe.isPending)),
        unavailable: current.isError || probes.some(probe => probe.isError),
    };
}
