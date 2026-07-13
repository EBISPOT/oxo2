package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SSSOM-API {@code /mappings} endpoints (ADR-0032), matching the mapping-commons reference API:
 * filtered listing, single-mapping lookup, and the field/value equality path. Set-scoped access (the
 * reference's {@code /mapping_sets/{id}/mappings}) is served here as a {@code mapping_set_id} query
 * parameter, because a mapping-set id is a full IRI that cannot sit in a path variable. List responses
 * use the SSSOM envelope; same-SPO rows are collapsed (ADR-0023) and no predicate is hidden by default.
 */
@Tag(name = "SSSOM mappings", description = "SSSOM-API mapping access (mapping-commons spec).")
@RestController
@RequestMapping(path = "/api/sssom/mappings", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingsController {

    private static final Logger logger = LoggerFactory.getLogger(MappingsController.class);

    @Autowired
    private SssomMappingService mappingService;
    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "List and filter mappings",
            description = """
                    Returns mappings, optionally narrowed by one or more `filter` parameters in the \
                    form `field|operator|value` (repeatable, AND-joined). Operators: `eq`, `ge`, \
                    `gt`, `le`, `lt`, `contains`. `field` is any SSSOM slot or OxO2 extension slot \
                    (e.g. `predicate_id`, `confidence`, `inference_type`). Values for `subject_id` / \
                    `object_id` / `predicate_id` may be a CURIE or a full IRI. The `mapping_set_id` \
                    query parameter scopes the result to one mapping set (the reference's \
                    `/mapping_sets/{id}/mappings`, expressed as a filter because the id is an IRI) and \
                    is equivalent to `filter=mapping_set_id|eq|<iri>`. Same-SPO rows are collapsed \
                    into one representative. `?format=sssom-tsv` / `tsv` / `csv` streams the whole \
                    result set as an SSSOM file instead of a page.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or paging", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> listMappings(
            @Parameter(description = "Repeatable filter, `field|operator|value`.", example = "predicate_id|eq|skos:exactMatch")
            @RequestParam(required = false) List<String> filter,
            @Parameter(description = "Scope to one mapping set by its full IRI (the mappings of that set).",
                    example = "https://w3id.org/sssom/commons/disease")
            @RequestParam(name = "mapping_set_id", required = false) String mappingSetId,
            @Parameter(description = "1-based page number.")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size (1–100).")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Output format: `json` (default) or `sssom-tsv` / `tsv` / `csv`.")
            @RequestParam(required = false) String format,
            HttpServletResponse httpResponse) throws IOException {
        if (!SssomMappingService.validPaging(page, limit)) {
            return ResponseEntity.badRequest().build();
        }
        List<SssomQueryBuilder.SssomFilter> filters;
        try {
            filters = SssomQueryBuilder.parseFilters(filter, SssomQueryBuilder::resolveMappingField);
        } catch (IllegalArgumentException e) {
            logger.info("Rejecting SSSOM mappings filter: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        if (mappingSetId != null && !mappingSetId.isBlank()) {
            filters = new ArrayList<>(filters);
            filters.add(new SssomQueryBuilder.SssomFilter(
                    MappingEnum.MAPPING_SET_ID, SssomQueryBuilder.Operator.EQ, mappingSetId.strip()));
        }
        SolrQuery solrQuery = SssomQueryBuilder.buildMappingsQuery(
                filters, true, PageRequest.of(page - 1, limit));
        return mappingService.respond(solrQuery, page, limit, format, httpResponse);
    }

    @Operation(
            summary = "Get a mapping by id",
            description = "Returns the single mapping whose `mapping_id` (a stable name-based UUID) "
                    + "matches, or 404. The response is a bare SSSOM mapping, not an envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The mapping"),
            @ApiResponse(responseCode = "404", description = "No mapping with that id", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Mapping> getMappingById(
            @Parameter(description = "The mapping_id UUID.", example = "0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c")
            @PathVariable String id) {
        try {
            SolrQuery solrQuery = SssomQueryBuilder.buildByMappingIdQuery(id);
            OxOSolrClient.SssomMappingResult result =
                    solrClient.querySssomMappings(solrQuery, PageRequest.of(0, 1));
            List<Mapping> mappings = result.page().getContent();
            if (mappings.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(mappings.get(0));
        } catch (Exception e) {
            logger.error("Error fetching mapping by id {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Get mappings by field and value",
            description = "Returns mappings where `field` equals `value` — the equality shorthand of "
                    + "the filter grammar. `value` may contain slashes (a full IRI) and is URL-decoded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Unknown field or invalid paging", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping(path = "/{field}/{*value}", produces = {MediaType.APPLICATION_JSON_VALUE,
            "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> getMappingsByField(
            @Parameter(description = "SSSOM slot to match on.", example = "object_id")
            @PathVariable String field,
            @Parameter(description = "Value the field must equal (URL-encoded).", example = "MONDO:0005148")
            @PathVariable String value,
            @Parameter(description = "1-based page number.")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size (1–100).")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Output format: `json` (default) or `sssom-tsv` / `tsv` / `csv`.")
            @RequestParam(required = false) String format,
            HttpServletResponse httpResponse) throws IOException {
        if (!SssomMappingService.validPaging(page, limit)) {
            return ResponseEntity.badRequest().build();
        }
        MappingEnum resolvedField = SssomQueryBuilder.resolveMappingField(field);
        if (resolvedField == null) {
            return ResponseEntity.badRequest().body("Unknown field: " + field);
        }
        String decodedValue = decodePathValue(value);
        if (decodedValue.isBlank()) {
            return ResponseEntity.badRequest().body("Value must not be blank");
        }
        SolrQuery solrQuery = SssomQueryBuilder.buildFieldValueQuery(
                resolvedField, decodedValue, true, PageRequest.of(page - 1, limit));
        return mappingService.respond(solrQuery, page, limit, format, httpResponse);
    }

    /** Strip the leading slash the {@code {*value}} capture keeps, then URL-decode. */
    private static String decodePathValue(String value) {
        String trimmed = value.startsWith("/") ? value.substring(1) : value;
        return URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
    }
}
