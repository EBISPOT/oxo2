import { InferenceType, asInferenceType } from "./InferenceType";
import { MappingSetCategory, asMappingSetCategory } from "./MappingSetCategory";

export interface MappingSetResponse {
    mapping_set_id: string;
    mapping_set_title?: string;
    mapping_set_description?: string;
    creator_label?: string[];
    mapping_provider?: string;
    inference_type?: string;
    mapping_set_source?: string[];
    mapping_set_category?: string;
    prefix?: string;
    ontology?: string;
    obsolete?: boolean;
}

export interface MappingSet {
    mappingSetId: string;
    mappingSetTitle: string;
    mappingSetDescription: string;
    creatorLabel: string[];
    mappingProvider: string;
    inferenceType: InferenceType;
    mappingSetSource: string[];
    // OxO curation category (ADR-0027); undefined on the synthetic inferences set.
    mappingSetCategory?: MappingSetCategory;
    // Ontology CURIE prefix and display name (ADR-0038), populated only on ONTOLOGY sets; '' otherwise.
    prefix: string;
    ontology: string;
    // True if every subject of this set is an obsolete term (ADR-0041). Hidden from the picker unless
    // the "obsolete terms" switch is on; labelled by the Obsolete column when shown.
    obsolete: boolean;
}

export function fromMappingSetResponse(r: MappingSetResponse): MappingSet {
    return {
        mappingSetId: r.mapping_set_id,
        mappingSetTitle: r.mapping_set_title ?? '',
        mappingSetDescription: r.mapping_set_description ?? '',
        creatorLabel: r.creator_label ?? [],
        mappingProvider: r.mapping_provider ?? '',
        inferenceType: asInferenceType(r.inference_type),
        mappingSetSource: r.mapping_set_source ?? [],
        mappingSetCategory: asMappingSetCategory(r.mapping_set_category),
        prefix: r.prefix ?? '',
        ontology: r.ontology ?? '',
        obsolete: r.obsolete ?? false,
    };
}
