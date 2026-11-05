const OLS_BASE = "https://www.ebi.ac.uk/ols4";

interface OlsTerm {
    iri: string;
    obo_id?: string;
    ontology_name: string;
    is_defining_ontology?: boolean;
}

/**
 * Resolve the exact entity against OLS before offering a link. A CURIE prefix alone neither proves
 * the term exists in OLS nor identifies its ontology (it may only be present as an imported term).
 * Prefer the supplied IRI; only use the CURIE when no IRI is available. Never fall back to fuzzy
 * search, which can give an unrelated hit for an absent resource such as OMIMPS:116200.
 */
export async function fetchOlsTermUrl(id?: string, iri?: string, signal?: AbortSignal): Promise<string | null> {
    const isIri = (value?: string) => !!value && /^https?:\/\//.test(value);
    const termIri = isIri(iri) ? iri : isIri(id) ? id : undefined;
    const curie = !termIri && id && /^[^\s:]+:[^\s]+$/.test(id) ? id : undefined;
    if (!termIri && !curie) return null;

    const params = new URLSearchParams(termIri ? {iri: termIri} : {obo_id: curie!});
    params.set('size', '100');
    const response = await fetch(`${OLS_BASE}/api/terms?${params}`, {signal});
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`OLS term lookup failed (${response.status})`);
    const data: { _embedded?: { terms?: OlsTerm[] } } = await response.json();
    const terms = (data._embedded?.terms ?? []).filter(term =>
        term.ontology_name && isIri(term.iri) &&
        (termIri ? term.iri === termIri : term.obo_id === curie));
    const term = terms.find(term => term.is_defining_ontology) ?? terms[0];
    if (!term) return null;
    return `${OLS_BASE}/ontologies/${encodeURIComponent(term.ontology_name)}/classes/${encodeURIComponent(encodeURIComponent(term.iri))}`;
}
