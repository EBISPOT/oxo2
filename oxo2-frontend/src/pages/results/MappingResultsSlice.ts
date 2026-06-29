import {
    MappingResponse,
    Mapping,
    InferredMappingResponse,
    InferredMapping,
    ChainRuleApplications,
    ChainRuleApplicationsResponse,
    ChainRuleResponse,
    ChainRule/*, MappingFields*/
} from '../../model/Mapping';
import { InferenceType, asInferenceType } from '../../model/InferenceType';
import { post, downloadPost } from '../../app/api';

export interface MappingSearchResponse {
    mappings: {
        content: MappingResponse[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
}

export interface MappingPage {
    mappings: Mapping[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export const emptyMappingPage: MappingPage = {
    mappings: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 0
}

interface AdvancedFieldQueryRequest {
    field: string;
    value: string;
}

interface SearchRequest {
    queries: string[];
    page: number;
    size: number;
    queryFields: string[];
    fieldList: string[];
    columnFilters: unknown[];
    sortedFields: unknown[];
    mappingSetIds?: string[];
    advancedFieldQueries?: AdvancedFieldQueryRequest[];
    // Multi-select inference-type filter (ADR-0011): omitted/empty = all types; otherwise restrict
    // to the listed codes (ASSERTED / SSSOM_INFERENCE).
    inferenceType?: string[];
    // Collapse same-SPO mappings into one row (ADR-0013). The normal/inferences result tables set this.
    groupBySpo?: boolean;
    // Cross-ontology mapping (ADR-0024): restrict subjects/objects to these ontology (CURIE) prefixes.
    subjectPrefixes?: string[];
    objectPrefixes?: string[];
}

// Batch cross-ontology mapping response (ADR-0024): the page of mappings plus the unmapped inputs.
export interface BatchMapResponse {
    mappings: MappingSearchResponse;
    unmappedInputs: string[];
    summary: { inputCount: number; mappedCount: number; unmappedCount: number };
}

export enum SearchStatus {
    Idle = 'idle',
    Loading = 'loading',
    Succeeded = 'succeeded',
    Failed = 'failed'
}


export function fromAssertedMappingString(assertedMappingsAsString?: string| undefined): InferredMapping[] {
    if (!assertedMappingsAsString ||
        (typeof assertedMappingsAsString === 'string' && assertedMappingsAsString.trim() === '')){
        return []
    }
    const assertedMappingsAsJson = JSON.parse(assertedMappingsAsString)

    if (!Array.isArray(assertedMappingsAsJson)) {
        return [];
    }
    return assertedMappingsAsJson.map(item => ({
        mappingJustification: item.mapping_justification,
        mappingTool: item.mapping_tool,
        mappingSetId: item.mapping_set_id,
        objectIri: item.object_iri,
        objectId: item.object_id || '',
        objectLabel: item.object_label,
        predicateIri: item.predicate_iri,
        predicateId: item.predicate_id || '',
        subjectIri: item.subject_iri,
        subjectId: item.subject_id || '',
        subjectLabel: item.subject_label
    }));
}

export function fromExplanationString(explanationAsString?: string| undefined): InferredMapping|undefined {
    if (!explanationAsString ||
        (typeof explanationAsString === 'string' && explanationAsString.trim() === '')) {
        return undefined;
    }
    const explanationAsJson: InferredMappingResponse = JSON.parse(explanationAsString);

    if (!explanationAsJson || typeof explanationAsJson !== 'object') {
        return undefined;
    }

    return {
        mappingJustification: explanationAsJson.mapping_justification,
        mappingTool: explanationAsJson.mapping_tool,
        mappingSetId: explanationAsJson.mapping_set_id,
        objectIri: explanationAsJson.object_iri,
        objectId: explanationAsJson.object_id || explanationAsJson.object_iri || '',
        objectLabel: explanationAsJson.object_label,
        predicateIri: explanationAsJson.predicate_iri,
        predicateId: explanationAsJson.predicate_id || explanationAsJson.predicate_iri || '',
        subjectIri: explanationAsJson.subject_iri,
        subjectId: explanationAsJson.subject_id || explanationAsJson.subject_iri || '',
        subjectLabel: explanationAsJson.subject_label,
        distance: explanationAsJson.distance,
        chainRuleApplications: fromChainRuleApplicationsResponse(explanationAsJson.chain_rule_applications)
    };
}

export function fromChainRuleApplicationsResponse(chainRuleApplications?: ChainRuleApplicationsResponse| undefined):
    ChainRuleApplications|undefined {

    if (!chainRuleApplications) {
        return undefined;
    }

    const premises: InferredMapping[] = Array.isArray(chainRuleApplications.premises)
        ? chainRuleApplications.premises.map((item: any) => ({
            mappingJustification: item.mapping_justification,
            mappingTool: item.mapping_tool,
            mappingSetId: item.mapping_set_id,
            objectIri: item.object_iri,
            objectId: item.object_id || item.object_iri || '',
            objectLabel: item.object_label,
            predicateIri: item.predicate_iri,
            predicateId: item.predicate_id || item.predicate_iri || '',
            subjectIri: item.subject_iri,
            subjectId: item.subject_id || item.subject_iri || '',
            subjectLabel: item.subject_label,
            distance: item.distance,
            chainRuleApplications: fromChainRuleApplicationsResponse(item.chain_rule_applications)
        }))
        : [];

    return {
        chainRule: fromChainRuleResponse(chainRuleApplications.chain_rule),
        premises
    };
}
export function fromChainRuleResponse(chainRule: ChainRuleResponse ): ChainRule | undefined {
    if (!chainRule) {
        return undefined;
    }
    try {
        if (
            typeof chainRule === 'object' &&
            typeof chainRule.chain_rule_name === 'string' &&
            typeof chainRule.chain_rule_long_form === 'string' &&
            typeof chainRule.chain_rule_abbreviated === 'string'
        ) {
            return {
                chainRuleName: chainRule.chain_rule_name,
                chainRuleLongForm: chainRule.chain_rule_long_form,
                chainRuleAbbreviated: chainRule.chain_rule_abbreviated
            };
        }
    } catch {
        // Invalid JSON
    }
    return undefined;
}

// Parse a representative's group_members payload ({"total":N,"members":[...]}) into the member
// Mapping[] and the group's true size (ADR-0013). Absent/blank → no grouping fields. Members carry no
// group_members of their own, so fromMappingResponse does not recurse.
export function fromGroupMembersString(groupMembersAsString?: string):
    { groupMembers?: Mapping[]; groupSize?: number } {
    if (!groupMembersAsString ||
        (typeof groupMembersAsString === 'string' && groupMembersAsString.trim() === '')) {
        return {};
    }
    const parsed = JSON.parse(groupMembersAsString);
    const members = Array.isArray(parsed?.members) ? parsed.members : [];
    return {
        groupMembers: members.map((member: MappingResponse) => fromMappingResponse(member)),
        groupSize: typeof parsed?.total === 'number' ? parsed.total : members.length,
    };
}

export function fromMappingResponse(item: MappingResponse): Mapping {
    return {
                authorId: item.author_id || '',
                authorLabel: item.author_label || '',
                comment: item.comment || '',
                confidence: item.confidence || 1,
                creatorId: item.creator_id || '',
                creatorLabel: item.creator_label || '',
                curationRule: item.curation_rule || '',
                issueTrackerItem: item.issue_tracker_item || '',
                license: item.license || '',
                mappingCardinality: item.mapping_cardinality || '',
                mappingDate: item.mapping_date || '',
                mappingId: item.mapping_id || '',
                mappingJustification: item.mapping_justification || '',
                mappingProvider: item.mapping_provider || '',
                mappingSetDescription: item.mapping_set_description || '',
                mappingSetId: item.mapping_set_id || '',
                mappingSetSource: item.mapping_set_source || '',
                mappingSetTitle: item.mapping_set_title || '',
                mappingSetVersion: item.mapping_set_version || '',
                mappingSource: item.mapping_source || '',
                mappingTool: item.mapping_tool || '',
                mappingToolVersion: item.mapping_tool_version || '',
                matchString: item.match_string || '',
                objectCategory: item.object_category || '',
                objectId: item.object_id || '',
                objectIri: item.object_iri || '',
                objectLabel: item.object_label || '',
                objectMatchField: item.object_match_field,
                objectPreprocessing: item.object_preprocessing,
                objectSource: item.object_source,
                objectSourceVersion: item.object_source_version,
                objectType: item.object_type,
                // other: item.other ?? [],
                predicateId: item.predicate_id || '',
                predicateIri: item.predicate_iri || '',
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
                subjectIri: item. subject_iri || '',
                subjectLabel: item.subject_label || '',
                subjectMatchField: item.subject_match_field,
                subjectPreprocessing: item.subject_preprocessing,
                subjectSource: item.subject_source,
                subjectSourceVersion: item.subject_source_version,
                subjectType: item.subject_type,
                assertedMappings: fromAssertedMappingString(item.asserted_mappings),
                explanation: fromExplanationString(item.explanation),
                inferenceType: asInferenceType(item.inference_type),
                ...fromGroupMembersString(item.group_members),
    };
}

export function fromJson(json: MappingSearchResponse|undefined): MappingPage {
    if (!json || !json.mappings || !json.mappings.content) {
        return emptyMappingPage;
    }
    return {
        mappings: json.mappings.content.map(fromMappingResponse),
        totalElements: json.mappings.totalElements,
        totalPages: json.mappings.totalPages,
        number: json.mappings.number,
        size: json.mappings.size
    }
}

function buildSearchRequest(queries: string[], page: number, pageSize: number, columnFilters: unknown[],
                           sorting: unknown[], mappingSetIds?: string[],
                           advancedFieldQueries?: AdvancedFieldQueryRequest[],
                           inferenceType?: InferenceType[], groupBySpo: boolean = false,
                           subjectPrefixes?: string[], objectPrefixes?: string[]): SearchRequest {
    return {
        queries: queries,
        page: page,
        size: pageSize,
        queryFields: [],
        fieldList: ['mapping_set_id', 'mapping_set_title', 'subject_id', 'subject_label', 'predicate_id', 'predicate_label',
            'predicate_modifier', 'object_id', 'object_label', 'mapping_justification', 'mapping_provider', 'inference_type', 'asserted_mappings', 'explanation'],
        columnFilters: columnFilters,
        sortedFields: sorting,
        ...(mappingSetIds && mappingSetIds.length > 0 ? { mappingSetIds } : {}),
        ...(advancedFieldQueries && advancedFieldQueries.length > 0 ? { advancedFieldQueries } : {}),
        ...(inferenceType && inferenceType.length > 0 ? { inferenceType } : {}),
        ...(groupBySpo ? { groupBySpo } : {}),
        ...(subjectPrefixes && subjectPrefixes.length > 0 ? { subjectPrefixes } : {}),
        ...(objectPrefixes && objectPrefixes.length > 0 ? { objectPrefixes } : {}),
    };
}

export function fetchMappings(queries: string[], page: number = 0, pageSize: number = 10, columnFilters: unknown[],
                              sorting: unknown[], mappingSetIds?: string[],
                              advancedFieldQueries?: AdvancedFieldQueryRequest[],
                              inferenceType?: InferenceType[],
                              groupBySpo: boolean = false,
                              subjectPrefixes?: string[],
                              objectPrefixes?: string[]): Promise<MappingSearchResponse> {
    const requestBody = buildSearchRequest(queries, page, pageSize, columnFilters, sorting, mappingSetIds,
        advancedFieldQueries, inferenceType, groupBySpo, subjectPrefixes, objectPrefixes);
    return post<SearchRequest, MappingSearchResponse>('/api/v2/mappings/search', requestBody);
}

/**
 * Download the current result set as an SSSOM-compliant TSV (ADR-0024): the same filters as the
 * search, streamed in full by the backend (paging ignored) via ?format=sssom-tsv.
 */
export function exportMappings(queries: string[], columnFilters: unknown[], mappingSetIds?: string[],
                               inferenceType?: InferenceType[],
                               subjectPrefixes?: string[], objectPrefixes?: string[]): Promise<void> {
    const requestBody = buildSearchRequest(queries, 0, 10, columnFilters, [], mappingSetIds,
        undefined, inferenceType, false, subjectPrefixes, objectPrefixes);
    return downloadPost('/api/v2/mappings/search?format=sssom-tsv', requestBody, 'oxo2-mappings.tsv');
}

/**
 * Batch cross-ontology mapping (ADR-0024): map a list of input terms into the target ontologies,
 * returning the matching mappings plus the inputs that mapped to nothing.
 */
export function batchMap(terms: string[], objectPrefixes?: string[], inferenceType?: InferenceType[],
                         page: number = 0, size: number = 10, groupBySpo: boolean = true): Promise<BatchMapResponse> {
    return post<object, BatchMapResponse>('/api/v2/mappings/batch-map', {
        terms,
        page,
        size,
        groupBySpo,
        ...(objectPrefixes && objectPrefixes.length > 0 ? { objectPrefixes } : {}),
        ...(inferenceType && inferenceType.length > 0 ? { inferenceType } : {}),
    });
}
