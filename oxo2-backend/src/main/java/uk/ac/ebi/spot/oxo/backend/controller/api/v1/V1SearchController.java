package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.HalSearchResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1MappingResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1SearchResult;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.export.ExportFormat;
import uk.ac.ebi.spot.oxo.backend.service.export.MappingTsvExporter;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * OxO v1 {@code /api/search} compatibility adapter (ADR-0004 / ADR-0024). Reproduces v1's request and
 * HAL {@code SearchResult} response wire-for-wire over the OxO2 mappings index, so existing v1 clients
 * keep working. Directional: each input is a source term matched on the subject side, objects
 * restricted to the {@code mappingTarget} datasources. v1's {@code distance} degrades to an inference
 * tier toggle (1 = asserted; otherwise asserted ∪ inferred) since the closure is precomputed, and the
 * per-mapping {@code distance} is a coarse direct/indirect sentinel — both documented breaks in ADR-0024.
 */
@Tag(name = "OxO v1 compatibility",
        description = "Wire-compatible OxO v1 /api/search (ADR-0024). Existing v1 clients unchanged.")
@RestController
@RequestMapping(path = "/api/search")
public class V1SearchController {

    private static final Logger logger = LoggerFactory.getLogger(V1SearchController.class);

    static final int MAX_IDS = 1000;
    static final int MAX_MAPPINGS_PER_TERM = 1000;

    private static final String[] V1_CSV_COLUMNS = {
            "curie_id", "label", "mapped_curie", "mapped_label",
            "mapping_source_prefix", "mapping_target_prefix", "distance"
    };

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(summary = "OxO v1 batch search (form-encoded)",
            description = "Wire-compatible v1 /api/search; accepts a form-encoded MappingSearchRequest.")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, "text/csv", "text/tab-separated-values"})
    public ResponseEntity<?> searchForm(@ModelAttribute V1MappingSearchRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String format,
            HttpServletResponse response) throws IOException {
        return doSearch(request, page, size, format, response);
    }

    @Operation(summary = "OxO v1 batch search (JSON)",
            description = "Wire-compatible v1 /api/search; accepts a JSON MappingSearchRequest and "
                    + "returns the v1 HAL SearchResult envelope. ?format=csv|tsv streams the v1 columns.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, "text/csv", "text/tab-separated-values"})
    public ResponseEntity<?> searchJson(@RequestBody V1MappingSearchRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String format,
            HttpServletResponse response) throws IOException {
        return doSearch(request, page, size, format, response);
    }

    @Operation(summary = "OxO v1 batch search (GET)",
            description = "Wire-compatible v1 /api/search via query parameters.")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "text/csv", "text/tab-separated-values"})
    public ResponseEntity<?> searchGet(@ModelAttribute V1MappingSearchRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String format,
            HttpServletResponse response) throws IOException {
        return doSearch(request, page, size, format, response);
    }

    private ResponseEntity<?> doSearch(V1MappingSearchRequest request, int page, int size,
            String format, HttpServletResponse response) throws IOException {
        List<String> ids = cleanIds(request.getIds());
        if (ids.size() > MAX_IDS) {
            return ResponseEntity.badRequest().build();
        }
        int distance = request.getDistance();
        List<String> mappingTarget = request.getMappingTarget();
        String inputSource = request.getInputSource();

        ExportFormat exportFormat = ExportFormat.fromParam(format);
        if (exportFormat.isStreamed()) {
            writeV1Export(ids, inputSource, mappingTarget, distance, exportFormat, response);
            return null;
        }

        if (page < 0 || size < 1 || size > MAX_IDS) {
            return ResponseEntity.badRequest().build();
        }
        int from = Math.min(page * size, ids.size());
        int to = Math.min(from + size, ids.size());
        List<V1SearchResult> results = new ArrayList<>();
        for (String id : ids.subList(from, to)) {
            results.add(searchOne(id, inputSource, mappingTarget, distance));
        }
        int totalPages = (int) Math.ceil((double) ids.size() / size);
        HalSearchResponse.PageMetadata pageMetadata =
                new HalSearchResponse.PageMetadata(size, ids.size(), totalPages, page);
        HalSearchResponse hal = new HalSearchResponse(
                new HalSearchResponse.Embedded(results),
                Map.of("self", new HalSearchResponse.Link("/api/search")),
                pageMetadata);
        return ResponseEntity.ok(hal);
    }

    /** Map one input term to its v1 SearchResult; an input with no mapping returns an empty result. */
    private V1SearchResult searchOne(String id, String inputSource, List<String> mappingTarget,
            int distance) {
        try {
            SolrQuery query = SolrQueryBuilder.buildV1TermQuery(
                    id, mappingTarget, distance, MAX_MAPPINGS_PER_TERM);
            QueryResponse response = solrClient.queryMappings(query);
            List<Mapping> hits = response.getBeans(Mapping.Builder.class).stream()
                    .map(Mapping.Builder::build).toList();
            if (hits.isEmpty()) {
                return new V1SearchResult(id, inputSource, null, null, List.of());
            }
            Mapping first = hits.get(0);
            String curie = first.subjectId().map(EntityReference::getDataAsString).orElse(id);
            String label = first.subjectLabel().orElse(null);
            String querySource = (inputSource != null && !inputSource.isBlank())
                    ? inputSource : first.subjectPrefix();
            List<V1MappingResponse> mappingResponses = hits.stream()
                    .map(V1SearchController::toMappingResponse).toList();
            return new V1SearchResult(id, querySource, curie, label, mappingResponses);
        } catch (Exception e) {
            logger.error("v1 search failed for id {}", id, e);
            return new V1SearchResult(id, inputSource, null, null, List.of());
        }
    }

    private static V1MappingResponse toMappingResponse(Mapping mapping) {
        String curie = mapping.objectId().map(EntityReference::getDataAsString).orElse(null);
        String label = mapping.objectLabel().orElse(null);
        String targetPrefix = mapping.objectPrefix();
        // v1's source-datasource provenance has no exact OxO2 equivalent; approximate with the source
        // (subject) ontology prefix (ADR-0024).
        String subjectPrefix = mapping.subjectPrefix();
        List<String> sourcePrefixes = subjectPrefix == null ? List.of() : List.of(subjectPrefix);
        int distance = mapping.inferenceType() == InferenceType.ASSERTED ? 1 : 2;
        return new V1MappingResponse(curie, label, sourcePrefixes, targetPrefix, distance);
    }

    /** Stream the v1 CSV/TSV columns over every input term (paging ignored), mirroring v1's export. */
    private void writeV1Export(List<String> ids, String inputSource, List<String> mappingTarget,
            int distance, ExportFormat format, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"oxo-search." + format.fileExtension() + "\"");
        char separator = format.separator();
        Writer writer = response.getWriter();
        writer.write(String.join(String.valueOf(separator), V1_CSV_COLUMNS));
        writer.write("\n");
        for (String id : ids) {
            V1SearchResult result = searchOne(id, inputSource, mappingTarget, distance);
            for (V1MappingResponse mappingResponse : result.mappingResponseList()) {
                String[] values = {
                        result.queryId(), nullToEmpty(result.label()),
                        nullToEmpty(mappingResponse.curie()), nullToEmpty(mappingResponse.label()),
                        String.join("|", mappingResponse.sourcePrefixes()),
                        nullToEmpty(mappingResponse.targetPrefix()),
                        String.valueOf(mappingResponse.distance())
                };
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        row.append(separator);
                    }
                    row.append(MappingTsvExporter.escape(values[i], separator));
                }
                writer.write(row.toString());
                writer.write("\n");
            }
        }
        writer.flush();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> cleanIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null) continue;
            String stripped = id.strip();
            if (!stripped.isEmpty()) {
                cleaned.add(stripped);
            }
        }
        return new ArrayList<>(cleaned);
    }
}
