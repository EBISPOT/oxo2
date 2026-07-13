package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FieldStatsInfo;
import org.apache.solr.client.solrj.response.QueryResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.ConfidenceRange;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.Facets;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.PaginationInfo;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SssomPage;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Assembles the SSSOM-API envelope (ADR-0032) from a Solr {@link QueryResponse}: the {@code facets}
 * block from the facet/stats components, and the {@code pagination} block (1-based, absolute
 * previous/next links) around a page of already-materialised data.
 */
public final class SssomResultMapper {

    private SssomResultMapper() {
    }

    /**
     * Wrap a page of data in the SSSOM envelope.
     *
     * @param data       the elements of this page (already fetched and, for mappings, collapsed).
     * @param totalItems the size of the whole filtered result set ({@code numFound} / group count).
     * @param pageNumber the 1-based page number being returned.
     * @param limit      the page size.
     * @param facets     the facet block, or null to omit it (mapping-set listings have no facets).
     * @param pageUrl    maps an adjacent page number to its absolute URL.
     */
    public static <T> SssomPage<T> page(List<T> data, long totalItems, int pageNumber, int limit,
                                        Facets facets, IntFunction<String> pageUrl) {
        int totalPages = limit <= 0 ? 0 : (int) Math.ceil((double) totalItems / (double) limit);
        String previous = pageNumber > 1 ? pageUrl.apply(pageNumber - 1) : null;
        String next = pageNumber < totalPages ? pageUrl.apply(pageNumber + 1) : null;
        PaginationInfo pagination =
                new PaginationInfo(previous, next, pageNumber, totalItems, totalPages);
        return new SssomPage<>(data, pagination, facets);
    }

    /** The envelope facets from a mapping query's facet/stats components. */
    public static Facets facetsFrom(QueryResponse response) {
        Map<String, Long> justifications =
                counts(response.getFacetField(MappingConstants.MAPPING_JUSTIFICATION));
        Map<String, Long> predicates =
                counts(response.getFacetField(MappingConstants.PREDICATE_ID));
        ConfidenceRange confidence = confidenceRange(response);
        return new Facets(justifications, predicates, confidence);
    }

    private static Map<String, Long> counts(FacetField facetField) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (facetField != null) {
            for (FacetField.Count value : facetField.getValues()) {
                if (value.getName() != null) {
                    counts.put(value.getName(), value.getCount());
                }
            }
        }
        return counts;
    }

    private static ConfidenceRange confidenceRange(QueryResponse response) {
        Map<String, FieldStatsInfo> stats = response.getFieldStatsInfo();
        FieldStatsInfo confidence = stats == null ? null : stats.get(MappingConstants.CONFIDENCE);
        if (confidence == null) {
            return new ConfidenceRange(null, null);
        }
        return new ConfidenceRange(asDouble(confidence.getMin()), asDouble(confidence.getMax()));
    }

    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
