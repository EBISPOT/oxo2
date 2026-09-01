package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.HealthResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The deep health check (ADR-0051), probed by the EBI traffic manager to decide whether a cluster
 * receives production traffic, and by the backend's Kubernetes readinessProbe.
 *
 * <p>Deliberately deep rather than a bare liveness ping: a Solr whose cores failed to load still
 * answers 200 on its admin endpoints while every query 500s, and a loaded-but-empty index serves an
 * empty site. Both must read as down to a traffic manager choosing between clusters, so healthy
 * means all three cores answer a query <em>and</em> each holds at least one document — the same
 * three-core {@code /select} check every other probe in this repo makes, tightened with a
 * data-presence requirement.
 */
@Tag(name = "Health", description = "Deep health check for traffic routing and readiness probes.")
@RestController
@RequestMapping(path = HealthController.PATH, produces = {MediaType.APPLICATION_JSON_VALUE})
public class HealthController {

    public static final String PATH = "/api/v2/health";

    /**
     * The exact sentence the traffic manager's {@code http_body_regex} matches. A contract with the
     * monitor configuration on the traffic-manager side — never reword it here alone, and never let
     * it appear in an unhealthy response.
     */
    public static final String STATUS_OPERATIONAL = "All systems are operational.";

    static final String STATUS_UNAVAILABLE = "Service unavailable.";

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "Deep health check",
            description = "200 with \"" + STATUS_OPERATIONAL + "\" in the body when all three Solr "
                    + "cores answer a query and each holds at least one document; 503 with per-core "
                    + "detail otherwise. An empty core counts as down: this endpoint decides traffic "
                    + "routing, and a deployment without data cannot serve. The healthy sentence is "
                    + "matched verbatim by the EBI traffic manager's body regex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All cores reachable and populated"),
            @ApiResponse(responseCode = "503", description = "A core is unreachable or empty")
    })
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        Map<String, HealthResponse.CoreHealth> cores = new LinkedHashMap<>();
        cores.put("oxo2-mappings", checkCore(solrClient::queryMappings));
        cores.put("oxo2-mappingsets", checkCore(solrClient::queryMappingSets));
        cores.put("oxo2-entities", checkCore(solrClient::queryEntities));

        boolean allCoresHealthy = cores.values().stream().allMatch(HealthResponse.CoreHealth::ok);
        if (allCoresHealthy) {
            return ResponseEntity.ok(new HealthResponse(STATUS_OPERATIONAL, cores));
        }
        // Loud on purpose: with probes every few seconds this repeats for as long as the outage
        // does, which is the right amount of noise for a service that is off rotation.
        logger.warn("Health check failing: {}", cores);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(STATUS_UNAVAILABLE, cores));
    }

    /** One core's query, allowed to throw — the health check reports the failure instead. */
    @FunctionalInterface
    private interface CoreQuery {
        QueryResponse run(SolrParams params) throws Exception;
    }

    /**
     * Count the core's documents with the cheapest query that proves the core can answer one:
     * {@code *:*} at {@code rows=0}, so only {@code numFound} comes back.
     */
    private HealthResponse.CoreHealth checkCore(CoreQuery coreQuery) {
        SolrQuery countQuery = new SolrQuery("*:*");
        countQuery.setRows(0);
        try {
            SolrDocumentList results = coreQuery.run(countQuery).getResults();
            long documents = results == null ? 0 : results.getNumFound();
            return new HealthResponse.CoreHealth(documents > 0, documents, null);
        } catch (Exception queryFailure) {
            return new HealthResponse.CoreHealth(false, 0, queryFailure.toString());
        }
    }
}
