package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SssomPage;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomResultMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSSOM-API {@code /mapping_sets} endpoint (ADR-0032): the mapping-set listing. The reference's
 * "mappings of one set" ({@code /mapping_sets/{id}/mappings}) is served instead as {@code
 * /api/sssom/mappings?mapping_set_id=<iri>}, because a mapping-set id is a full IRI that cannot sit in
 * a path variable and the result is a list of mappings, not sets — see {@link MappingsController}.
 */
@Tag(name = "SSSOM mapping sets", description = "SSSOM-API mapping-set access (mapping-commons spec).")
@RestController
@RequestMapping(path = "/api/sssom/mapping_sets", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingSetsController {

    private static final Logger logger = LoggerFactory.getLogger(MappingSetsController.class);

    private static final int MAX_ROWS = 10_000;

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "List and filter mapping sets",
            description = "Returns the mapping sets, optionally narrowed by `filter` parameters "
                    + "(`field|operator|value`, repeatable, AND-joined) over mapping-set slots such as "
                    + "`mapping_provider`, `license` or `inference_type`. Sorted by title. The envelope "
                    + "carries no `facets` block (a mapping set has none of the facetted slots).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope of mapping sets"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or paging", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> listMappingSets(
            @Parameter(description = "Repeatable filter, `field|operator|value`.", example = "inference_type|eq|ASSERTED")
            @RequestParam(required = false) List<String> filter,
            @Parameter(description = "1-based page number.")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size (1–100).")
            @RequestParam(defaultValue = "20") int limit) {
        if (!SssomMappingService.validPaging(page, limit)) {
            return ResponseEntity.badRequest().build();
        }
        List<SssomQueryBuilder.SssomFilter> filters;
        try {
            filters = SssomQueryBuilder.parseFilters(filter, SssomQueryBuilder::resolveMappingSetField);
        } catch (IllegalArgumentException e) {
            logger.info("Rejecting SSSOM mapping_sets filter: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        try {
            SolrQuery solrQuery = SssomQueryBuilder.buildMappingSetsQuery(
                    filters, PageRequest.of(page - 1, Math.min(limit, MAX_ROWS)));
            QueryResponse response = solrClient.queryMappingSets(solrQuery);
            List<Map<String, Object>> data = new ArrayList<>(response.getResults().size());
            for (SolrDocument doc : response.getResults()) {
                data.add(cleanSetDocument(doc));
            }
            SssomPage<Map<String, Object>> envelope = SssomResultMapper.page(
                    data, response.getResults().getNumFound(), page, limit, null,
                    SssomMappingService.currentRequestPageUrl());
            return ResponseEntity.ok(envelope);
        } catch (Exception e) {
            logger.error("Error listing SSSOM mapping sets", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * A mapping set as its SSSOM slots: the stored Solr fields (whose names already are the slot
     * names), minus the Solr-internal bookkeeping ({@code _version_}, {@code _text_}, the {@code id}
     * unique key, and the {@code *_str} sort copies). Returned as a map rather than a typed MappingSet
     * because the shared MappingSet model carries no Solr field bindings; the map is faithful to every
     * stored slot and robust to schema additions.
     */
    private static Map<String, Object> cleanSetDocument(SolrDocument doc) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (String fieldName : doc.getFieldNames()) {
            if (isInternalField(fieldName)) {
                continue;
            }
            Object value = doc.getFieldValue(fieldName);
            if (value != null) {
                clean.put(fieldName, value);
            }
        }
        return clean;
    }

    private static boolean isInternalField(String fieldName) {
        return fieldName.startsWith("_")
                || fieldName.equals("id")
                || fieldName.endsWith("_str")
                || fieldName.endsWith("_ngram")
                || fieldName.endsWith("_ci");
    }
}
