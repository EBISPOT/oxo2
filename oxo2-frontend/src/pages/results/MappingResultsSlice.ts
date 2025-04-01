import { MappingResponse, Mapping/*, MappingFields*/ } from '../../model/Mapping';
// import { SearchInput/*, initialSearchState*/ } from '../../model/Search';
import { post } from '../../app/api';

interface FacetedMappingResponse {
    mappings: {
        content: MappingResponse[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
    facets: Record<string, Record<string, number>>;
}

export interface FacetedMapping {
    mappings: Mapping[];
    facets: Record<string, Record<string, number>>;
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


// interface SearchState {
//     searchInput: SearchInput;
//     mappingResponse: FacetedMapping;
//     status: SearchStatus;
//     error: string;
// }


const emptyFacetedMapping: FacetedMapping = {
    mappings: [],
    facets: {},
}

// const initialState: SearchState = {
//     searchInput: initialSearchState,
//     mappingResponse: emptyFacetedMapping,
//     status: SearchStatus.Idle,
//     error: '',
// };


export function fromJson(json: FacetedMappingResponse|undefined): FacetedMapping {
    if (!json || !json.mappings || !json.mappings.content || !json.facets) {
        return emptyFacetedMapping;
    }
    return {
        mappings: json.mappings.content.map(item => {
            return {
                mappingId: item.mapping_id,
                mappingJustification: item.mapping_justification || '',
                mappingSetId: item.mapping_set_id,
                objectId: item.object_id || '',
                predicateId: item.predicate_id || '',
                subjectId: item.subject_id || ''
            };
        }),
        facets: json.facets
    }
}

// function parseString(input: string): string[] {
//     return input.trim().split('\n').map(item => item.trim());
// }

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
