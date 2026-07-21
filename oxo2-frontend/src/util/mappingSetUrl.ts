/**
 * Deep-link to a mapping set's detail page (/mapping-set, backed by MappingSetDetails). The id is a
 * full IRI, so it travels as a query parameter rather than a path segment — the same reason the
 * backend's by-id endpoint takes a query param: an IRI path variable would need encoded slashes,
 * which Tomcat rejects. URLSearchParams handles the escaping so it round-trips through
 * useSearchParams().get("id") on the other side.
 */
export function mappingSetHref(mappingSetId: string): string {
    return `/mapping-set?${new URLSearchParams({ id: mappingSetId }).toString()}`;
}
