package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.Facets;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SssomPage;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.export.ExportFormat;
import uk.ac.ebi.spot.oxo.backend.service.export.MappingTsvExporter;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomResultMapper;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

/**
 * Turns a SSSOM-API mapping query into the shared response: either the {@code {data, pagination,
 * facets}} JSON envelope, or — when {@code ?format=} names a table type — a streamed SSSOM export
 * (ADR-0032). Used by every endpoint that returns mappings ({@code /mappings} — including its
 * {@code ?mapping_set_id=} set scope — and {@code /entities}), so they page, facet, link and export
 * identically.
 */
@Service
public class SssomMappingService {

    /** Default page size, matching the reference API. */
    public static final int DEFAULT_LIMIT = 20;
    /** Upper bound on {@code limit}, as on the OxO2 v2 endpoints. */
    public static final int MAX_LIMIT = 100;

    private static final Logger logger = LoggerFactory.getLogger(SssomMappingService.class);

    @Autowired
    private OxOSolrClient solrClient;
    @Autowired
    private MappingTsvExporter exporter;

    /** True when the 1-based page / limit are in range. */
    public static boolean validPaging(int page, int limit) {
        return page >= 1 && limit >= 1 && limit <= MAX_LIMIT;
    }

    /**
     * Run {@code pagedQuery} and respond. When {@code format} is a streamed export, the query's page
     * and collapse are dropped and the whole result set is written as a file; otherwise the page is
     * wrapped in the SSSOM envelope with its facets and previous/next links.
     */
    public ResponseEntity<?> respond(SolrQuery pagedQuery, int page, int limit, String format,
                                     HttpServletResponse httpResponse) throws IOException {
        ExportFormat exportFormat = ExportFormat.fromParam(format);
        if (exportFormat.isStreamed()) {
            writeExport(SssomQueryBuilder.toExportQuery(pagedQuery), exportFormat, httpResponse);
            return null;
        }
        try {
            Pageable pageable = PageRequest.of(page - 1, limit);
            OxOSolrClient.SssomMappingResult result = solrClient.querySssomMappings(pagedQuery, pageable);
            Facets facets = SssomResultMapper.facetsFrom(result.response());
            SssomPage<Mapping> envelope = SssomResultMapper.page(
                    result.page().getContent(), result.page().getTotalElements(),
                    page, limit, facets, currentRequestPageUrl());
            return ResponseEntity.ok(envelope);
        } catch (Exception e) {
            logger.error("Error running SSSOM mapping query {}", pagedQuery, e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Absolute URL of an adjacent page: the current request URL with {@code page} replaced. */
    public static IntFunction<String> currentRequestPageUrl() {
        return page -> ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .build(true)
                .toUriString();
    }

    private void writeExport(SolrQuery exportQuery, ExportFormat format, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"oxo2-sssom." + format.fileExtension() + "\"");
        try {
            exporter.stream(exportQuery, format, response.getWriter());
        } catch (IOException ioException) {
            throw ioException;
        } catch (Exception e) {
            throw new IOException("Export failed", e);
        }
    }
}
