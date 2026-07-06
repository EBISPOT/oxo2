const OLS_BASE = "https://www.ebi.ac.uk/ols4";

/**
 * Build a link to an entity's term page in the EBI Ontology Lookup Service (OLS4).
 *
 * OLS4 has no ontology-agnostic term route — its entity page lives at
 * `/ontologies/{ontologyId}/classes/{iri}`, where the IRI is double-URL-encoded.
 * This mirrors the form OLS4 itself emits when linking between terms
 * (see `EntityLink.tsx` in ols4/frontend, which builds the path with
 * `encodeURIComponent(encodeURIComponent(iri))`). The older
 * `?termId={curie}` form we used before is not a route at all, so it silently
 * dropped users on the OLS home page.
 *
 * The ontology id is derived from the CURIE prefix: by convention the OLS
 * ontology id is the lowercased prefix (`HP` → `hp`, `MP` → `mp`,
 * `DOID` → `doid`). We link to `classes` because OxO2 mappings are between
 * ontology classes; an off-by-one prefix guess lands on OLS's term-not-found
 * page rather than an endless spinner.
 *
 * When we lack either a usable prefix or an IRI we fall back to an OLS search
 * (as OLS4's own `EntityLink` does), so the link still lands somewhere useful.
 *
 * @param id  the entity CURIE, e.g. `"HP:0004361"` (may itself be an IRI)
 * @param iri the entity's full IRI, e.g. `"http://purl.obolibrary.org/obo/HP_0004361"`
 * @returns an OLS4 URL, or `undefined` when there is nothing to link to
 */
export function buildOlsTermUrl(id?: string, iri?: string): string | undefined {
    const termIri = iri || (id && isIri(id) ? id : undefined);
    const ontologyId = ontologyIdFromCurie(id);

    if (ontologyId && termIri) {
        // Double-encode the IRI into the path, matching OLS4's own term links.
        const encodedIri = encodeURIComponent(encodeURIComponent(termIri));
        return `${OLS_BASE}/ontologies/${ontologyId}/classes/${encodedIri}`;
    }

    const query = termIri || id;
    return query ? `${OLS_BASE}/search?q=${encodeURIComponent(query)}` : undefined;
}

/**
 * Derive an OLS ontology id from a CURIE by lowercasing its prefix.
 * Returns undefined for IRIs or strings without a prefix.
 */
function ontologyIdFromCurie(id?: string): string | undefined {
    if (!id || isIri(id)) {
        return undefined;
    }
    const colonIndex = id.indexOf(":");
    if (colonIndex <= 0) {
        return undefined;
    }
    return id.slice(0, colonIndex).toLowerCase();
}

function isIri(value: string): boolean {
    return value.startsWith("http://") || value.startsWith("https://");
}
