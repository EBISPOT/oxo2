package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.BatchMapRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.BatchMapResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.export.ExportFormat;
import uk.ac.ebi.spot.oxo.backend.service.export.MappingTsvExporter;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;


@Tag(name = "Mappings", description = "Search ontology mappings and list the mappings for a subject term.")
@RestController
@RequestMapping(path="/api/v2/mappings", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingController {
    static final int MAX_PAGE_SIZE = 100;
    /** Cap on batch-map input terms (ADR-0024), to bound a single facet-per-input probe query. */
    static final int MAX_INPUT_TERMS = 1000;

    @Autowired
    private OxOSolrClient solrClient;
    @Autowired
    private MappingTsvExporter exporter;
    private static final Logger logger = LoggerFactory.getLogger(MappingController.class);

    /** Producible types for endpoints that serve JSON or a streamed export (ADR-0024). */
    private static final String[] JSON_OR_EXPORT =
            {MediaType.APPLICATION_JSON_VALUE, "text/tab-separated-values", "text/csv"};

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
    @PostMapping(path = "/search", produces = {MediaType.APPLICATION_JSON_VALUE,
            "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> getMappings(@RequestBody MappingSearchRequest mappingSearchRequest,
            @Parameter(description = "Output format: `json` (default, paged) or `sssom-tsv` / `tsv` / "
                    + "`csv` to stream the full result set as an SSSOM-compliant file.")
            @RequestParam(required = false) String format,
            HttpServletResponse httpResponse) throws IOException {

        logger.info("Mapping search request: {} (format={})", mappingSearchRequest, format);

        ResponseEntity<MappingSearchResponse> pagingError =
                validatePaging(mappingSearchRequest.getPage(), mappingSearchRequest.getSize());
        if (pagingError != null) {
            return pagingError;
        }

        ExportFormat exportFormat = ExportFormat.fromParam(format);
        if (exportFormat.isStreamed()) {
            writeExport(SolrQueryBuilder.buildExportQuery(mappingSearchRequest), exportFormat, httpResponse);
            return null;
        }

        Pageable pageable = PageRequest.of(mappingSearchRequest.getPage(), mappingSearchRequest.getSize());
        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(mappingSearchRequest, pageable);
        logger.trace("Solr query={}", solrQuery.toString());
        try {
            return ResponseEntity.ok(solrClient.query(solrQuery, pageable));
        } catch (Exception e) {
            logger.error("Error while fetching mappings for: {}", mappingSearchRequest, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Map one ontology to another (source → target)",
            description = "Cross-ontology mapping (ADR-0024): returns mappings whose subject is in the "
                    + "source ontologies (`from`) and object is in the target ontologies (`to`), each a "
                    + "CURIE prefix. Directional (subject = source, object = target) — the bookmarkable "
                    + "form, mirroring OxO v1's `GET /api/mappings?fromId=…&toId=…`. Same-SPO rows are "
                    + "collapsed by default. With neither `from` nor `to`, returns a page over all "
                    + "mappings (weak predicates hidden as in /search).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid paging parameters", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> mapOntologies(
            @Parameter(description = "Source ontologies (CURIE prefixes), comma-separated.", example = "DOID")
            @RequestParam(required = false) List<String> from,
            @Parameter(description = "Target ontologies (CURIE prefixes), comma-separated.", example = "EFO,MONDO")
            @RequestParam(required = false) List<String> to,
            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1–100).")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Collapse same subject/predicate/object rows into one representative.")
            @RequestParam(defaultValue = "true") boolean groupBySpo,
            @Parameter(description = "Output format: `json` (default) or `sssom-tsv` / `tsv` / `csv` to stream.")
            @RequestParam(required = false) String format,
            HttpServletResponse httpResponse) throws IOException {
        ResponseEntity<MappingSearchResponse> pagingError = validatePaging(page, size);
        if (pagingError != null) {
            return pagingError;
        }

        MappingSearchRequest request = new MappingSearchRequest();
        request.setSubjectPrefixes(from);
        request.setObjectPrefixes(to);
        request.setPage(page);
        request.setSize(size);
        request.setGroupBySpo(groupBySpo);

        ExportFormat exportFormat = ExportFormat.fromParam(format);
        if (exportFormat.isStreamed()) {
            writeExport(SolrQueryBuilder.buildExportQuery(request), exportFormat, httpResponse);
            return null;
        }

        Pageable pageable = PageRequest.of(page, size);
        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, pageable);
        try {
            return ResponseEntity.ok(solrClient.query(solrQuery, pageable));
        } catch (Exception e) {
            logger.error("Error mapping ontologies from={} to={}", from, to, e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Batch-map a list of terms to target ontologies",
            description = "Cross-ontology mapping (ADR-0024) driven by an explicit list of input terms "
                    + "(CURIEs, IRIs, or labels), matched on the subject side, into the target "
                    + "ontologies (`objectPrefixes`). Returns the paged mappings **and** "
                    + "`unmappedInputs` — the inputs that produced no mapping into the targets, the "
                    + "report only this mode can give. Input is de-duplicated and capped at 1000 terms.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mappings plus the unmapped-input report"),
            @ApiResponse(responseCode = "400", description = "Invalid paging or too many input terms", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping(path = "/batch-map", produces = {MediaType.APPLICATION_JSON_VALUE,
            "text/tab-separated-values", "text/csv"})
    public ResponseEntity<?> batchMap(@RequestBody BatchMapRequest request,
            @Parameter(description = "Output format: `json` (default, with the unmapped report) or "
                    + "`sssom-tsv` / `tsv` / `csv` to stream the matching mappings as a file.")
            @RequestParam(required = false) String format,
            HttpServletResponse httpResponse) throws IOException {
        if (request.getPage() < 0 || request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        List<String> terms = cleanTerms(request.getTerms());
        if (terms.size() > MAX_INPUT_TERMS) {
            logger.warn("batch-map rejected: {} terms exceeds cap {}", terms.size(), MAX_INPUT_TERMS);
            return ResponseEntity.badRequest().build();
        }

        ExportFormat exportFormat = ExportFormat.fromParam(format);
        if (exportFormat.isStreamed()) {
            // The export is the matching mappings as an SSSOM file; the unmapped report is JSON-only.
            writeExport(SolrQueryBuilder.buildBatchExportQuery(
                    terms, request.getObjectPrefixes(), request.getInferenceType()), exportFormat, httpResponse);
            return null;
        }

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        try {
            SolrQuery displayQuery = SolrQueryBuilder.buildBatchMapQuery(
                    terms, request.getObjectPrefixes(), request.getInferenceType(),
                    request.isGroupBySpo(), pageable);
            MappingSearchResponse mappings = solrClient.query(displayQuery, pageable);

            // One probe query whose per-input facet.query counts settle the whole unmapped set,
            // independent of the display page (ADR-0024).
            SolrQuery unmappedQuery = SolrQueryBuilder.buildBatchMapUnmappedQuery(
                    terms, request.getObjectPrefixes(), request.getInferenceType());
            QueryResponse unmappedResponse = solrClient.queryMappings(unmappedQuery);
            Map<String, Integer> facetCounts = unmappedResponse.getFacetQuery();

            List<String> unmappedInputs = new ArrayList<>();
            for (String term : terms) {
                String clause = SolrQueryBuilder.subjectSideClause(term);
                int count = (facetCounts == null || clause == null)
                        ? 0 : facetCounts.getOrDefault(clause, 0);
                if (count == 0) {
                    unmappedInputs.add(term);
                }
            }

            BatchMapResponse.Summary summary = new BatchMapResponse.Summary(
                    terms.size(), terms.size() - unmappedInputs.size(), unmappedInputs.size());
            return ResponseEntity.ok(new BatchMapResponse(mappings, unmappedInputs, summary));
        } catch (Exception e) {
            logger.error("Error in batch-map for {} terms", terms.size(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Stream a cross-ontology result set straight to the response as a downloadable SSSOM-compliant
     * file (ADR-0024). The base query already carries the q + filters and an {@code id asc} sort for
     * cursorMark deep paging; the exporter writes the curie_map header then streams every row, so
     * nothing is buffered server-side. The caller returns {@code null} afterwards — having written the
     * response itself, which Spring treats as handled.
     */
    private void writeExport(SolrQuery exportQuery, ExportFormat format, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"oxo2-mappings." + format.fileExtension() + "\"");
        try {
            exporter.stream(exportQuery, format, response.getWriter());
        } catch (IOException ioException) {
            throw ioException;
        } catch (Exception e) {
            throw new IOException("Export failed", e);
        }
    }

    /** Strip, drop blanks, and de-duplicate input terms, preserving first-seen order. */
    private static List<String> cleanTerms(List<String> terms) {
        if (terms == null) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String term : terms) {
            if (term == null) continue;
            String stripped = term.strip();
            if (!stripped.isEmpty()) {
                cleaned.add(stripped);
            }
        }
        return new ArrayList<>(cleaned);
    }
}
