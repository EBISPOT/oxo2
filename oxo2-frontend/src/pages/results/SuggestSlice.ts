import { get, post } from '../../app/api';
import { WeakPredicate } from '../../model/WeakPredicate';

/**
 * Typeahead suggestions (ADR-0034).
 *
 * Entity suggestions come from the oxo2-entities collection — one document per DISTINCT entity —
 * rather than from the mappings index, which is denormalised and would offer the same entity once
 * per mapping it participates in.
 */

/** One entity offered as a completion. Rendered as the label > id > IRI stack of EntityRefCell. */
export interface EntitySuggestion {
    id: string;
    label?: string;
    iri?: string;
    prefix?: string;
    /**
     * How many mappings picking this suggestion will actually return — the entity's mappings on the
     * side being searched, counting only the predicates currently ticked (ADR-0035). Not the entity's
     * total: showing that would promise rows the search then hides. Always ≥ 1.
     */
    mappingCount: number;
}

/** Which side of a mapping a suggested entity must appear on. */
export type EntitySide = 'subject' | 'object' | 'any';

/** The wire shape — snake_case, mapped to the camelCase model below. */
interface EntitySuggestionResponse {
    id: string;
    label?: string;
    iri?: string;
    prefix?: string;
    mapping_count: number;
}

function fromEntitySuggestionResponse(response: EntitySuggestionResponse): EntitySuggestion {
    return {
        id: response.id,
        label: response.label,
        iri: response.iri,
        prefix: response.prefix,
        mappingCount: response.mapping_count,
    };
}

/**
 * Shorter than this and the backend returns an empty list without querying Solr: one character is a
 * prefix of a large fraction of the collection. Mirrored here so the client does not make the call
 * at all. Keep in step with EntitySuggestQueryBuilder.MIN_QUERY_LENGTH.
 */
export const MIN_SUGGEST_LENGTH = 2;

/**
 * Entities whose label or CURIE starts with `query`.
 *
 * `side` defaults to subject on the caller's side: the main search box matches the subject side only
 * (ADR-0030), so suggesting an entity that appears solely as an object would complete to zero rows.
 *
 * `includeWeakPredicates` MUST be the same selection the search this completes into will use
 * (ADR-0035). It is not a display preference: it decides which entities are suggestable at all. Pass
 * the wrong set and the dropdown offers entities whose every mapping the search then hides — you pick
 * one, and the table comes back empty.
 */
export function fetchEntitySuggestions(
    query: string,
    side: EntitySide = 'subject',
    prefixes: string[] = [],
    includeWeakPredicates: WeakPredicate[] = [],
    size = 10,
    includeObsolete = false,
): Promise<EntitySuggestion[]> {
    const params = new URLSearchParams();
    params.set('q', query);
    params.set('side', side.toUpperCase());
    params.set('size', String(size));
    for (const prefix of prefixes) {
        params.append('prefix', prefix);
    }
    for (const predicate of includeWeakPredicates) {
        params.append('includeWeakPredicates', predicate);
    }
    // Same rule as the weak predicates (ADR-0041): the suggest must hide/reveal obsolete terms exactly
    // as the search it completes into, or a picked suggestion could land on an empty table.
    if (includeObsolete) {
        params.set('includeObsolete', 'true');
    }
    return get<EntitySuggestionResponse[]>(`/api/v2/suggest/entities?${params.toString()}`)
        .then((response) => response.map(fromEntitySuggestionResponse));
}

/** One value of a field, with how many mappings carry it. */
export interface ValueSuggestion {
    value: string;
    count: number;
}

interface ValueSuggestRequest {
    field: string;
    query: string;
    size: number;
    /** The live search, verbatim — the same body POSTed to /api/v2/mappings/search. */
    search: unknown;
}

/**
 * Values of `field` present in the CURRENT result set, each with the number of rows it would leave.
 *
 * The point of scoping to the live search rather than the whole corpus: a suggested filter value can
 * then never yield zero rows. `search` must be the very request the result table is showing — build
 * it with `buildSearchRequest` so the two cannot diverge.
 */
export function fetchContextualValues(
    field: string,
    query: string,
    search: unknown,
    size = 10,
): Promise<ValueSuggestion[]> {
    return post<ValueSuggestRequest, ValueSuggestion[]>(
        '/api/v2/suggest/values',
        { field, query, size, search },
    );
}
