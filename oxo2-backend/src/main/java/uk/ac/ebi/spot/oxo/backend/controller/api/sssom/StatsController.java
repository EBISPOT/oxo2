package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.StatsResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;

/**
 * SSSOM-API {@code /stats} endpoint (ADR-0032): corpus counts, matching the reference API's stats.
 * Two Solr queries settle all four counts.
 */
@Tag(name = "SSSOM stats", description = "SSSOM-API corpus statistics (mapping-commons spec).")
@RestController
@RequestMapping(path = "/api/sssom/stats", produces = {MediaType.APPLICATION_JSON_VALUE})
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "Corpus statistics",
            description = "Counts of mappings, mapping sets, distinct mapping providers and distinct "
                    + "entities. `nb_entity` is a HyperLogLog estimate of the distinct entities "
                    + "appearing as a subject or object, over the `entity_id` field; it reads low until "
                    + "the first full dataload after that field was added, then tracks the corpus.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corpus counts"),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping
    public ResponseEntity<StatsResponse> stats() {
        try {
            // Query 1 (mappings): total mappings + distinct-entity estimate.
            SolrQuery mappingsQuery = new SolrQuery("*:*");
            mappingsQuery.setRows(0);
            mappingsQuery.set("json.facet", "{nb_entity:\"unique(entity_id)\"}");
            QueryResponse mappingsResponse = solrClient.queryMappings(mappingsQuery);
            long nbMapping = mappingsResponse.getResults().getNumFound();
            long nbEntity = readJsonFacetLong(mappingsResponse, "nb_entity");

            // Query 2 (mapping sets): total sets + distinct providers (bucket count).
            SolrQuery setsQuery = new SolrQuery("*:*");
            setsQuery.setRows(0);
            setsQuery.setFacet(true);
            setsQuery.setFacetMinCount(1);
            setsQuery.setFacetLimit(-1);
            setsQuery.addFacetField(MappingConstants.MAPPING_PROVIDER);
            QueryResponse setsResponse = solrClient.queryMappingSets(setsQuery);
            long nbMappingSet = setsResponse.getResults().getNumFound();
            long nbMappingProvider = bucketCount(setsResponse.getFacetField(MappingConstants.MAPPING_PROVIDER));

            return ResponseEntity.ok(
                    new StatsResponse(nbMapping, nbMappingSet, nbMappingProvider, nbEntity));
        } catch (Exception e) {
            logger.error("Error computing SSSOM stats", e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Read a scalar aggregation out of the {@code json.facet} response section, 0 if absent. */
    private static long readJsonFacetLong(QueryResponse response, String key) {
        Object facets = response.getResponse().get("facets");
        if (facets instanceof NamedList<?> namedList) {
            Object value = namedList.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0;
    }

    private static long bucketCount(FacetField facetField) {
        return facetField == null ? 0 : facetField.getValueCount();
    }
}
