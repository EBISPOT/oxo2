package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SearchEntityRequest;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder;

import java.io.IOException;

/**
 * SSSOM-API {@code /entities} endpoint (ADR-0032): every mapping in which one of the given entities
 * appears as {@code subject_id} or {@code object_id} — the reference API's undirected entity lookup,
 * optionally narrowed by justification and predicate.
 */
@Tag(name = "SSSOM entities", description = "SSSOM-API entity lookup (mapping-commons spec).")
@RestController
@RequestMapping(path = "/api/sssom/entities", produces = {MediaType.APPLICATION_JSON_VALUE})
public class EntitiesController {

    @Autowired
    private SssomMappingService mappingService;

    @Operation(
            summary = "Get mappings by entity",
            description = "Returns every mapping where one of the request's `curies` (CURIEs or IRIs) "
                    + "appears as subject_id or object_id, optionally restricted to the given "
                    + "`mapping_justification` and `predicate_id` values. Same-SPO rows are collapsed. "
                    + "`?format=sssom-tsv` / `tsv` / `csv` streams the whole result set as an SSSOM file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid paging", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> mappingsByEntity(
            @RequestBody SearchEntityRequest request,
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
        SolrQuery solrQuery = SssomQueryBuilder.buildEntitiesQuery(
                request, true, PageRequest.of(page - 1, limit));
        return mappingService.respond(solrQuery, page, limit, format, httpResponse);
    }
}
