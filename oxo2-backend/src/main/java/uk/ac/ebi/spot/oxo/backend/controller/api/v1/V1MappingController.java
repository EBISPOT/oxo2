package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.HalSearchResponse;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1Datasource;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1Mapping;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1PagedMappings;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1Scope;
import uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto.V1Term;
import uk.ac.ebi.spot.oxo.backend.service.EntityLabelResolver;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OxO v1 {@code GET /api/mappings} compatibility adapter (ADR-0004 / ADR-0025). Reproduces v1's
 * read-only mapping-listing endpoint and its HAL {@code PagedResources<Mapping>} envelope over the
 * OxO2 mappings index, so existing v1 clients keep working. Read-only: v1's create/delete verbs are
 * not ported. Filtering by {@code fromId} / {@code toId} is undirected (a term matches on either the
 * subject or object side), matching v1; results are restricted to asserted mappings, since v1 exposed
 * no inference tier here. Term {@code uri} is the full IRI (from the {@code subject_iri} /
 * {@code object_iri} index fields) and the mapping-level {@code datasource} identifies the SSSOM
 * mapping set the mapping came from ({@code mapping_set_id} + title). Several v1 fields have no OxO2
 * equivalent and are documented breaks (see ADR-0025 and the {@link V1Mapping} DTO): {@code mappingId}
 * is always null, {@code scope} is derived from the SSSOM predicate, and per-item HAL links are omitted.
 */
@Tag(name = "OxO v1 compatibility",
        description = "Wire-compatible OxO v1 read APIs (ADR-0025). Existing v1 clients unchanged.")
@RestController
@RequestMapping(path = "/api/mappings", produces = {MediaType.APPLICATION_JSON_VALUE})
public class V1MappingController {

    private static final Logger logger = LoggerFactory.getLogger(V1MappingController.class);

    static final int MAX_PAGE_SIZE = 1000;

    @Autowired
    private OxOSolrClient solrClient;

    @Autowired
    private EntityLabelResolver entityLabelResolver;

    @Operation(summary = "OxO v1 list mappings",
            description = "Wire-compatible v1 GET /api/mappings. Returns a HAL "
                    + "PagedResources<Mapping> envelope. Optional fromId / toId filter by term "
                    + "(CURIE or IRI), undirected as in v1: fromId alone matches the term on the "
                    + "subject or object side; fromId+toId matches mappings between the two terms in "
                    + "either direction; toId alone is ignored (a v1 quirk, reproduced). Results are "
                    + "asserted mappings only. The weak predicates rdfs:subClassOf / oboInOwl:hasDbXref "
                    + "are included by default (v1 was built on xrefs); set hideWeakPredicates=true to "
                    + "exclude them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of mappings in the v1 HAL envelope"),
            @ApiResponse(responseCode = "400", description = "Invalid paging parameters",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "500", description = "Error querying Solr",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping
    public ResponseEntity<V1PagedMappings> mappings(
            @Parameter(description = "Filter: source term (CURIE or IRI), matched undirected.",
                    example = "DOID:0001816")
            @RequestParam(required = false) String fromId,
            @Parameter(description = "Filter: target term (CURIE or IRI); only applied alongside fromId.",
                    example = "EFO:0000400")
            @RequestParam(required = false) String toId,
            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1–1000).")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Hide the weak predicates rdfs:subClassOf and oboInOwl:hasDbXref "
                    + "(shown by default for v1 compatibility).")
            @RequestParam(defaultValue = "false") boolean hideWeakPredicates) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Pageable pageable = PageRequest.of(page, size);
            SolrQuery solrQuery =
                    SolrQueryBuilder.buildV1MappingsQuery(fromId, toId, hideWeakPredicates, pageable);
            MappingSearchResponse response = solrClient.query(solrQuery, pageable);
            MappingSearchResponse.MappingPage mappingPage = response.getMappings();

            // One entity lookup for the whole page, before any label is read off a row.
            Map<String, String> labels =
                    entityLabelResolver.resolveLabels(termCuries(mappingPage.content()));
            List<V1Mapping> mappings = mappingPage.content().stream()
                    .map(mapping -> toV1Mapping(mapping, labels)).toList();
            HalSearchResponse.PageMetadata pageMetadata = new HalSearchResponse.PageMetadata(
                    size, mappingPage.totalElements(), mappingPage.totalPages(), page);
            V1PagedMappings body = new V1PagedMappings(
                    new V1PagedMappings.Embedded(mappings),
                    Map.of("self", new HalSearchResponse.Link("/api/mappings")),
                    pageMetadata);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("Error listing v1 mappings (fromId={}, toId={})", fromId, toId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Every subject/object CURIE on a page, for one batched entity-label lookup. */
    private static List<String> termCuries(List<Mapping> mappings) {
        List<String> curies = new ArrayList<>();
        for (Mapping mapping : mappings) {
            mapping.subjectId().map(EntityReference::getDataAsString).ifPresent(curies::add);
            mapping.objectId().map(EntityReference::getDataAsString).ifPresent(curies::add);
        }
        return curies;
    }

    /**
     * Adapt one SSSOM {@link Mapping} to the v1 {@link V1Mapping} wire shape (ADR-0025).
     *
     * <p>Labels come from {@code resolvedLabels} first, not from the mapping row: a label in
     * {@code oxo2-mappings} is a property of the ROW, so the same term was reported as
     * "Cardiomyopathies" on rows from {@code mesh.ols.sssom.tsv} and {@code null} on rows from
     * {@code mondo.sssom.tsv} — within a single response. See {@link EntityLabelResolver#labelFor}.
     */
    static V1Mapping toV1Mapping(Mapping mapping, Map<String, String> resolvedLabels) {
        String subjectPrefix = mapping.subjectPrefix();
        String objectPrefix = mapping.objectPrefix();
        String subjectCurie = mapping.subjectId().map(EntityReference::getDataAsString).orElse(null);
        String objectCurie = mapping.objectId().map(EntityReference::getDataAsString).orElse(null);
        V1Term fromTerm = new V1Term(
                subjectCurie,
                null, mapping.subjectIRI().map(Uri::getDataAsString).orElse(null),
                EntityLabelResolver.labelFor(
                        subjectCurie, mapping.subjectLabel().orElse(null), resolvedLabels),
                V1Datasource.ofPrefix(subjectPrefix));
        V1Term toTerm = new V1Term(
                objectCurie,
                null, mapping.objectIRI().map(Uri::getDataAsString).orElse(null),
                EntityLabelResolver.labelFor(
                        objectCurie, mapping.objectLabel().orElse(null), resolvedLabels),
                V1Datasource.ofPrefix(objectPrefix));
        String scope = V1Scope.of(mapping.predicateIRI(), mapping.predicateId()).name();
        String date = mapping.mappingDate().map(mappingDate -> mappingDate.getDataAsString()).orElse(null);
        // The mapping-level datasource identifies the SSSOM mapping set this mapping came from (v1 left
        // it null here); sourcePrefix stays the subject ontology prefix, matching v1. mappingId,
        // sourceType and predicate have no OxO2 equivalent.
        String mappingSetId = mapping.mappingSetId() == null ? null : mapping.mappingSetId().getDataAsString();
        V1Datasource datasource =
                V1Datasource.ofMappingSet(mappingSetId, mapping.mappingSetTitle().orElse(null));
        return new V1Mapping(null, fromTerm, toTerm, subjectPrefix, null, null,
                datasource, scope, date);
    }
}
