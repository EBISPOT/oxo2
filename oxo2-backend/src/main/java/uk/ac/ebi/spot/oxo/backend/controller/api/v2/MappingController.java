package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@Tag(name = "Mappings", description = "Search ontology mappings and list the mappings for a subject term.")
@RestController
@RequestMapping(path="/api/v2/mappings", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingController {
    static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private OxOSolrClient solrClient;
    private static final Logger logger = LoggerFactory.getLogger(MappingController.class);

    private static ResponseEntity<MappingSearchResponse> validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        return null;
    }

    @Operation(
            summary = "List mappings for a subject",
            description = "Returns every mapping whose subject_id equals the given term. The path "
                    + "variable is URL-decoded, so a CURIE or full IRI may be supplied "
                    + "(e.g. `MONDO:0005148` or its percent-encoded IRI). Results are paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid paging parameters", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{subjectId}")
    public ResponseEntity<MappingSearchResponse> getMappingsById(
                                                         @Parameter(description = "Subject term to match on (CURIE or IRI), URL-encoded.", example = "MONDO:0005148")
                                                         @PathVariable String subjectId,
                                                         @Parameter(description = "Zero-based page index.")
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @Parameter(description = "Page size (1–100).")
                                                         @RequestParam(defaultValue = "10") int size) {
        ResponseEntity<MappingSearchResponse> pagingError = validatePaging(page, size);
        if (pagingError != null) {
            return pagingError;
        }
        try {
            String decodedSubjectId = URLDecoder.decode(subjectId, StandardCharsets.UTF_8.name());
            Pageable pageable = PageRequest.of(page, size);
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(MappingEnum.SUBJECT_ID.getField()
                    + ":\"" + ClientUtils.escapeQueryChars(decodedSubjectId) + "\"");
            solrQuery.setStart((int) pageable.getOffset());
            solrQuery.setRows(pageable.getPageSize());

            MappingSearchResponse mappingSearchResponse = solrClient.query(solrQuery, pageable);
            return ResponseEntity.ok(mappingSearchResponse);
        } catch (Exception e) {
            logger.error("Error while fetching mappings for subjectId: {}", subjectId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Search mappings",
            description = """
                    Full mapping search: free-text queries over selected fields, column/advanced \
                    field filters, multi-select inference-type filter, sorting, paging, and optional \
                    same-SPO row collapsing.

                    **Default predicate filtering.** By convention `rdfs:subClassOf` and \
                    `oboInOwl:hasDbXref` mappings are hidden by default, matched on the canonical \
                    `predicate_iri`. This exclusion is lifted automatically as soon as the request \
                    explicitly filters on any predicate field (`predicate_id`, `predicate_label`, \
                    `predicate_iri` or `predicate_modifier`) with a non-blank value, via either a \
                    column filter or an advanced field query — so a caller who asks for these \
                    predicates still gets them.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid paging parameters", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping(path = "/search",
            /*consumes = {MediaType.APPLICATION_JSON_VALUE},*/ produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<MappingSearchResponse> getMappings(@RequestBody MappingSearchRequest mappingSearchRequest) {

        logger.info("Mapping search request: {}", mappingSearchRequest);

        ResponseEntity<MappingSearchResponse> pagingError =
                validatePaging(mappingSearchRequest.getPage(), mappingSearchRequest.getSize());
        if (pagingError != null) {
            return pagingError;
        }

        Pageable pageable = PageRequest.of(mappingSearchRequest.getPage(), mappingSearchRequest.getSize());
        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(mappingSearchRequest, pageable);

        logger.trace("Solr query={}", solrQuery.toString());

        try {
            MappingSearchResponse mappingSearchResponse = solrClient.query(solrQuery, pageable);
            logger.trace("mappingSearchResponse={}", mappingSearchResponse);
            return ResponseEntity.ok(mappingSearchResponse);
        } catch (Exception e) {
            logger.error("Error while fetching mappings for subjectId: {}", mappingSearchRequest, e);
            return ResponseEntity.status(500).build();
        }
    }
}
