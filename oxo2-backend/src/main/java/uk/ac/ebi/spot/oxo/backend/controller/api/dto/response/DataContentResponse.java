package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

/**
 * The landing page's Data Content summary (ADR-0043): what the currently loaded corpus consists of.
 *
 * <p>Distinct from the SSSOM-API {@code /stats} response, which is fixed by the mapping-commons spec
 * and counts providers and distinct entities instead. This is OxO2's own shape, so it breaks the counts
 * down the way the landing page presents them: mappings by origin, mapping sets by curation category.
 *
 * @param releaseDate the UTC instant of the dataload run that produced the corpus, as an ISO-8601
 *                    string, or {@code null} when no loaded mapping set carries one (data indexed
 *                    before the field existed — it appears after the next full dataload)
 */
public record DataContentResponse(
        String releaseDate,
        Mappings mappings,
        MappingSets mappingSets) {

    /**
     * Mapping counts by origin. {@code asserted} and {@code inferred} partition {@code total} exactly:
     * they are facet buckets over {@code inference_type}, whose only values are ASSERTED and
     * SSSOM_INFERENCE, and every document has one (the Solr field defaults to ASSERTED).
     *
     * <p>{@code total} counts mapping documents, not distinct subject-predicate-object triples. The
     * distinct-triple count would need a collapse over the whole collection, which is far too expensive
     * for a landing-page widget.
     */
    public record Mappings(long total, long asserted, long inferred) {
    }

    /**
     * Mapping-set counts by curation category (ADR-0027). {@code total} counts asserted sets only — the
     * synthetic cross-set inferences set is a real document in {@code oxo2-mappingsets} but is not a
     * loaded corpus, so it is excluded and {@code curated + ontologies} accounts for {@code total}.
     *
     * <p>{@code ontologies} is the number of ONTOLOGY-category sets, one per ontology OxO2 has loaded.
     * That is deliberately not the {@code /api/v2/ontologies} number, which counts every CURIE prefix
     * appearing anywhere in the mappings index — including prefixes OxO2 holds no mapping set for.
     *
     * <p>A set indexed before {@code mapping_set_category} existed falls into neither bucket, so the two
     * can sum to less than {@code total} until the next full dataload.
     */
    public record MappingSets(long total, long curated, long ontologies) {
    }
}
