/**
 * OxO v1 term-page identifiers (ADR-0004).
 *
 * OxO v1 served a page per term at `/terms/<id>`, and external sites still link to it — the GWAS
 * Catalog's "Trait in OXO" button points at `/spot/oxo/terms/EFO_0007837`. v1 accepted the id as a
 * CURIE (`EFO:0007837`) or as the OBO short form (`EFO_0007837`, the IRI fragment with an underscore
 * where the CURIE has its colon). OxO2 has no term page; the nearest surface is the subject-side
 * results page `/search/<curie>`, and the backend only recognises a CURIE by its colon — a short form
 * would be classified as a label and match nothing. This turns whatever v1 accepted into the CURIE
 * that search understands.
 *
 * The first underscore is the prefix boundary, as in OLS's `short_form`: OBO prefixes carry no
 * underscore, while a local id may (`NCBITaxon_9606` is fine either way; `UBERON_0001062` has one).
 * Anything already holding a colon — a CURIE or a full IRI — is passed through untouched, and so is
 * a bare word with no underscore, which search treats as a label. Returns null for a blank id.
 */
export function legacyTermToCurie(id: string | undefined): string | null {
    const trimmed = (id ?? "").trim();
    if (trimmed === "") {
        return null;
    }
    if (trimmed.includes(":")) {
        return trimmed;
    }
    const underscore = trimmed.indexOf("_");
    if (underscore <= 0 || underscore === trimmed.length - 1) {
        return trimmed;
    }
    return `${trimmed.slice(0, underscore)}:${trimmed.slice(underscore + 1)}`;
}
