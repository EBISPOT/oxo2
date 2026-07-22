package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSetSummary;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants.*;

@Tag(name = "Mapping sets", description = "List and resolve the SSSOM mapping sets loaded into OxO2.")
@RestController
@RequestMapping(path = "/api/v2/mapping-sets", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingSetController {

    private static final Logger logger = LoggerFactory.getLogger(MappingSetController.class);

    private static final int MAX_ROWS = 10_000;
    private static final String TITLE_SORT_FIELD = MAPPING_SET_TITLE + "_str";
    private static final String[] SUMMARY_FIELDS = {
            MAPPING_SET_ID, MAPPING_SET_TITLE, MAPPING_SET_DESCRIPTION,
            CREATOR_LABEL, MAPPING_PROVIDER, INFERENCE_TYPE, MAPPING_SET_SOURCE,
            MAPPING_SET_CATEGORY, PREFIX, ONTOLOGY
    };

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "List mapping sets",
            description = "Returns a summary of every mapping set (up to 10 000), sorted by title. "
                    + "Optionally restrict to one or more inference types.")
    @ApiResponse(responseCode = "200", description = "Mapping-set summaries")
    @GetMapping
    public ResponseEntity<List<MappingSetSummary>> listMappingSets(
            @Parameter(description = "Restrict to these inference types (`ASSERTED`, `SSSOM_INFERENCE`); "
                    + "absent/empty returns all sets. Unknown values are ignored.")
            @RequestParam(required = false) List<String> inferenceType) {
        try {
            SolrQuery solrQuery = new SolrQuery("*:*");
            solrQuery.setRows(MAX_ROWS);
            solrQuery.setFields(SUMMARY_FIELDS);
            // Multi-select inference-type filter (ADR-0011): absent/empty = all sets; otherwise
            // restrict to the listed types (ASSERTED / SSSOM_INFERENCE).
            String inferenceTypeFilter = inferenceTypeFilterClause(inferenceType);
            if (inferenceTypeFilter != null) {
                solrQuery.addFilterQuery(inferenceTypeFilter);
            }
            solrQuery.addSort(TITLE_SORT_FIELD, SolrQuery.ORDER.asc);

            QueryResponse response = solrClient.queryMappingSets(solrQuery);
            List<MappingSetSummary> summaries = new ArrayList<>(response.getResults().size());
            for (SolrDocument doc : response.getResults()) {
                summaries.add(toSummary(doc));
            }
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            logger.error("Error listing mapping sets", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Fetch a single mapping set by id. Backs the frontend inferred-set resolution surface
     * (e.g. resolving https://www.ebi.ac.uk/oxo2/inferences and per-source inferred sets), so the
     * response carries inference_type and the source-set union (ADR-0012).
     *
     * <p>The id is taken as a query parameter, not a path variable: mapping_set_id is a full IRI,
     * and Tomcat rejects the encoded slashes ({@code %2F}) an IRI path variable would require.
     * Spring already URL-decodes the query parameter value, so no manual decode is needed.
     */
    @Operation(
            summary = "Get a mapping set by id",
            description = "Fetches a single mapping set by its id. The id is a full IRI passed as a "
                    + "query parameter (not a path variable), because Tomcat rejects the encoded "
                    + "slashes an IRI path variable would require.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The mapping set"),
            @ApiResponse(responseCode = "404", description = "No mapping set with that id", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/by-id")
    public ResponseEntity<MappingSetSummary> getMappingSet(
            @Parameter(description = "Full IRI of the mapping set.", example = "https://www.ebi.ac.uk/oxo2/inferences")
            @RequestParam String mappingSetId) {
        try {
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(MAPPING_SET_ID + ":\"" + ClientUtils.escapeQueryChars(mappingSetId) + "\"");
            solrQuery.setRows(1);
            solrQuery.setFields(SUMMARY_FIELDS);

            QueryResponse response = solrClient.queryMappingSets(solrQuery);
            if (response.getResults().isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(toSummary(response.getResults().get(0)));
        } catch (Exception e) {
            logger.error("Error fetching mapping set {}", mappingSetId, e);
            return ResponseEntity.status(500).build();
        }
    }

    private static MappingSetSummary toSummary(SolrDocument doc) {
        return new MappingSetSummary(
                asString(doc.getFieldValue(MAPPING_SET_ID)),
                asString(doc.getFieldValue(MAPPING_SET_TITLE)),
                asString(doc.getFieldValue(MAPPING_SET_DESCRIPTION)),
                asStringList(doc.getFieldValues(CREATOR_LABEL)),
                asString(doc.getFieldValue(MAPPING_PROVIDER)),
                asString(doc.getFieldValue(INFERENCE_TYPE)),
                asStringList(doc.getFieldValues(MAPPING_SET_SOURCE)),
                asString(doc.getFieldValue(MAPPING_SET_CATEGORY)),
                asString(doc.getFieldValue(PREFIX)),
                asString(doc.getFieldValue(ONTOLOGY))
        );
    }

    /**
     * Build an OR-of-exact-terms filter clause over inference_type, or null if no valid type was
     * supplied. Values are validated against {@link InferenceType} (the codes are safe enum names),
     * so unknown values are ignored rather than injected into the query.
     */
    private static String inferenceTypeFilterClause(List<String> inferenceType) {
        if (inferenceType == null || inferenceType.isEmpty()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String value : inferenceType) {
            if (value == null || value.isBlank()) continue;
            try {
                codes.add(InferenceType.fromCode(value.trim()).getCode());
            } catch (IllegalArgumentException e) {
                logger.warn("Ignoring unknown inferenceType filter value: {}", value);
            }
        }
        if (codes.isEmpty()) {
            return null;
        }
        String clause = codes.stream()
                .distinct()
                .map(code -> INFERENCE_TYPE + ":" + code)
                .collect(Collectors.joining(" OR "));
        return "(" + clause + ")";
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> asStringList(Collection<Object> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(values.size());
        for (Object v : values) {
            if (v != null) out.add(v.toString());
        }
        return out;
    }
}
