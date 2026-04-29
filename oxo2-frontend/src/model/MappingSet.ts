export interface MappingSetResponse {
    mapping_set_id: string;
    mapping_set_title?: string;
    mapping_set_description?: string;
    creator_label?: string[];
    mapping_provider?: string;
}

export interface MappingSet {
    mappingSetId: string;
    mappingSetTitle: string;
    mappingSetDescription: string;
    creatorLabel: string[];
    mappingProvider: string;
}

export function fromMappingSetResponse(r: MappingSetResponse): MappingSet {
    return {
        mappingSetId: r.mapping_set_id,
        mappingSetTitle: r.mapping_set_title ?? '',
        mappingSetDescription: r.mapping_set_description ?? '',
        creatorLabel: r.creator_label ?? [],
        mappingProvider: r.mapping_provider ?? '',
    };
}
