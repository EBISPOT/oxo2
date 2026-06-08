import { get } from '../../app/api';
import { MappingSet, MappingSetResponse, fromMappingSetResponse } from '../../model/MappingSet';
import { InferenceType } from '../../model/InferenceType';

export async function fetchMappingSets(inferenceType?: InferenceType[]): Promise<MappingSet[]> {
    const params = new URLSearchParams();
    (inferenceType ?? []).forEach((type) => params.append('inferenceType', type));
    const queryString = params.toString();
    const response = await get<MappingSetResponse[]>(
        `/api/v2/mapping-sets${queryString ? `?${queryString}` : ''}`);
    return response.map(fromMappingSetResponse);
}

/**
 * Fetch a single mapping set by its (full IRI) id. Backs the /inferences resolution page (ADR-0012).
 * The id is a query parameter because it is a full IRI and Tomcat rejects the encoded slashes a
 * path variable would require.
 */
export async function fetchMappingSetById(mappingSetId: string): Promise<MappingSet> {
    const response = await get<MappingSetResponse>(
        `/api/v2/mapping-sets/by-id?mappingSetId=${encodeURIComponent(mappingSetId)}`);
    return fromMappingSetResponse(response);
}
