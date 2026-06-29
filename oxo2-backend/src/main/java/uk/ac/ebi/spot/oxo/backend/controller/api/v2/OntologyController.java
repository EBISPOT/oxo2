package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologySummary;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologyTarget;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the set of ontologies (CURIE prefixes) present in the mappings index, faceting over the
 * denormalised {@code subject_prefix} / {@code object_prefix} fields. Drives the cross-ontology
 * mapping source/target selectors (ADR-0024).
 */
@Tag(name = "Ontologies", description = "Ontologies (CURIE prefixes) present in the mappings index, "
        + "for driving cross-ontology mapping source/target selectors (ADR-0024).")
@RestController
@RequestMapping(path = "/api/v2/ontologies", produces = {MediaType.APPLICATION_JSON_VALUE})
public class OntologyController {

    private static final Logger logger = LoggerFactory.getLogger(OntologyController.class);

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "List ontologies (CURIE prefixes)",
            description = "Without parameters, returns every CURIE prefix in the mappings index with "
                    + "the number of mappings using it as subject and as object. With `forSubject`, "
                    + "returns instead the target ontologies (object prefixes) reachable from that "
                    + "source ontology, each with the number of mappings into it.")
    @ApiResponse(responseCode = "200", description = "Ontology prefixes with counts")
    @GetMapping
    public ResponseEntity<?> listOntologies(
            @Parameter(description = "Source ontology prefix. When present, the response is the list of "
                    + "reachable target ontologies with per-target mapping counts.", example = "DOID")
            @RequestParam(required = false) String forSubject) {
        try {
            if (forSubject != null && !forSubject.isBlank()) {
                return ResponseEntity.ok(targetsForSubject(forSubject.trim()));
            }
            return ResponseEntity.ok(allOntologies());
        } catch (Exception e) {
            logger.error("Error listing ontologies (forSubject={})", forSubject, e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Facet over both prefix fields in one query, merging subject/object counts per prefix. */
    private List<OntologySummary> allOntologies() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(MappingConstants.SUBJECT_PREFIX, MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(solrQuery);
        Map<String, long[]> counts = new LinkedHashMap<>(); // prefix -> [asSubject, asObject]
        accumulate(response.getFacetField(MappingConstants.SUBJECT_PREFIX), counts, 0);
        accumulate(response.getFacetField(MappingConstants.OBJECT_PREFIX), counts, 1);

        List<OntologySummary> summaries = new ArrayList<>(counts.size());
        counts.forEach((prefix, subjectObject) ->
                summaries.add(new OntologySummary(prefix, subjectObject[0], subjectObject[1])));
        summaries.sort(Comparator.comparing(OntologySummary::prefix));
        return summaries;
    }

    private static void accumulate(FacetField facetField, Map<String, long[]> counts, int slot) {
        if (facetField == null) return;
        for (FacetField.Count value : facetField.getValues()) {
            if (value.getName() == null || value.getName().isBlank()) continue;
            counts.computeIfAbsent(value.getName(), key -> new long[2])[slot] = value.getCount();
        }
    }

    /** Targets reachable from a source ontology: facet object_prefix over the subject_prefix subset. */
    private List<OntologyTarget> targetsForSubject(String subjectPrefix) throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.addFilterQuery(
                MappingConstants.SUBJECT_PREFIX + ":" + ClientUtils.escapeQueryChars(subjectPrefix));
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(solrQuery);
        FacetField objectFacet = response.getFacetField(MappingConstants.OBJECT_PREFIX);
        List<OntologyTarget> targets = new ArrayList<>();
        if (objectFacet != null) {
            for (FacetField.Count value : objectFacet.getValues()) {
                if (value.getName() == null || value.getName().isBlank()) continue;
                targets.add(new OntologyTarget(value.getName(), value.getCount()));
            }
        }
        targets.sort(Comparator.comparing(OntologyTarget::prefix));
        return targets;
    }
}
