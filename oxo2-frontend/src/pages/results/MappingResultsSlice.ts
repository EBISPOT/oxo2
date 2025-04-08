import { MappingResponse, Mapping/*, MappingFields*/ } from '../../model/Mapping';
import { post } from '../../app/api';

export interface FacetedMappingResponse {
    mappings: {
        content: MappingResponse[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
    facets: Record<string, Record<string, number>>;
}

export const emptyFacetedMappingResponse: FacetedMappingResponse = {
    mappings: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 0
    },
    facets: {}
}

export interface FacetedMapping {
    mappings: Mapping[];
    facets: Record<string, Record<string, number>>;
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export const emptyFacetedMapping: FacetedMapping = {
    mappings: [],
    facets: {},
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 0
}

interface SearchRequest {
    queries: string[];
    page: number;
    size: number;
    queryFields: string[];
    fieldList: string[];
    facets: string[];
}

export enum SearchStatus {
    Idle = 'idle',
    Loading = 'loading',
    Succeeded = 'succeeded',
    Failed = 'failed'
}



export function fromJson(json: FacetedMappingResponse|undefined): FacetedMapping {
    if (!json || !json.mappings || !json.mappings.content || !json.facets) {
        return emptyFacetedMapping;
    }
    return {
        mappings: json.mappings.content.map(item => {
            return {
                authorId: item.author_id,
                authorLabel: item.author_label,
                comment: item.comment,
                confidence: item.confidence,
                creatorId: item.creator_id,
                creatorLabel: item.creator_label,
                curationRule: item.curation_rule,
                issueTrackerItem: item.issue_tracker_item,
                license: item.license,
                mappingCardinality: item.mapping_cardinality,
                mappingDate: item.mapping_date,
                mappingId: item.mapping_id,
                mappingJustification: item.mapping_justification || '',
                mappingProvider: item.mapping_provider,
                mappingSetDescription: item.mapping_set_description,
                mappingSetId: item.mapping_set_id,
                mappingSetSource: item.mapping_set_source,
                mappingSetTitle: item.mapping_set_title,
                mappingSetVersion: item.mapping_set_version,
                mappingSource: item.mapping_source,
                mappingTool: item.mapping_tool,
                mappingToolVersion: item.mapping_tool_version,
                matchString: item.match_string,
                objectCategory: item.object_category,
                objectId: item.object_id || '',
                objectLabel: item.object_label || '',
                objectMatchField: item.object_match_field,
                objectPreprocessing: item.object_preprocessing,
                objectSource: item.object_source,
                objectSourceVersion: item.object_source_version,
                objectType: item.object_type,
                objectIdPrefix: item.object_id_prefix,
                other: item.other,
                predicateId: item.predicate_id || '',
                predicateLabel: item.predicate_label || '',
                predicateModifier: item.predicate_modifier,
                publicationDate: item.publication_date,
                reviewerId: item.reviewer_id,
                reviewerLabel: item.reviewer_label,
                seeAlso: item.see_also,
                similarityMeasure: item.similarity_measure,
                similarityScore: item.similarity_score,
                subjectCategory: item.subject_category,
                subjectId: item.subject_id || '',
                subjectLabel: item.subject_label || '',
                subjectMatchField: item.subject_match_field,
                subjectPreprocessing: item.subject_preprocessing,
                subjectSource: item.subject_source,
                subjectSourceVersion: item.subject_source_version,
                subjectType: item.subject_type,
                subjectIdPrefix: item.subject_id_prefix
            };
        }),
        totalElements: json.mappings.totalElements,
        totalPages: json.mappings.totalPages,
        number: json.mappings.number,
        size: json.mappings.size,
        facets: json.facets
    }
}

export function fetchMappings(queries: string[]): Promise<FacetedMappingResponse> {
    const requestBody: SearchRequest = {
        queries: queries,
        page: 0,
        size: 10,
        queryFields: ['subject_id', 'object_id'],
        fieldList: ['mapping_set_id', 'subject_id', 'subject_label', 'subject_id_prefix', 'predicate_id', 'predicate_label', 'predicate_modifier', 'object_id', 'object_label', 'object_id_prefix', 'mapping_justification'],
        facets: ['object_id_prefix', 'subject_id_prefix'],
    };

    const searchResponse = post<SearchRequest, FacetedMappingResponse>(
        '/api/v2/mappings/search',
        requestBody);

    return searchResponse;
}
