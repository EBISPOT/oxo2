import { get } from '../../app/api';
import { MappingSet, MappingSetResponse, fromMappingSetResponse } from '../../model/MappingSet';

export async function fetchMappingSets(): Promise<MappingSet[]> {
    const response = await get<MappingSetResponse[]>('/api/v2/mapping-sets');
    return response.map(fromMappingSetResponse);
}
