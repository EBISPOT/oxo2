package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.DataContentResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants;

import java.util.Date;

/**
 * Serves the landing page's Data Content summary (ADR-0043): the release date of the loaded corpus,
 * mapping counts by origin, and mapping-set counts by curation category.
 *
 * <p>Three cheap Solr queries settle it, all {@code rows=0} except the release-date read, which returns
 * a single document. Every count is a {@code numFound} or a bucket of a facet over a single-valued
 * string field, so nothing here scales with corpus size. In particular this endpoint deliberately uses
 * no {@code unique()} aggregation: a full-collection distinct count over the mappings collection is
 * what makes the SSSOM {@code /stats} endpoint expensive, and a landing page cannot afford it.
 */
@Tag(name = "Data content", description = "Summary of the currently loaded corpus, for the landing page.")
@RestController
@RequestMapping(path = "/api/v2/data-content", produces = {MediaType.APPLICATION_JSON_VALUE})
public class DataContentController {

    private static final Logger logger = LoggerFactory.getLogger(DataContentController.class);

    /** Restricts the mapping-set counts to real loaded corpora, excluding the synthetic inferences set. */
    private static final String ASSERTED_SETS_ONLY =
            MappingSetConstants.INFERENCE_TYPE + ":" + InferenceType.ASSERTED.getCode();

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "Data content summary",
            description = "The release date of the loaded corpus, the mapping count split into asserted "
                    + "and inferred, and the mapping-set count split into curated sets and ontologies. "
                    + "`releaseDate` is null on data indexed before the release-date field existed; it "
                    + "appears after the next full dataload. Mapping-set counts cover asserted sets only, "
                    + "so the synthetic cross-set inferences set is excluded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corpus summary"),
            @ApiResponse(responseCode = "500", description = "Error querying Solr", content = @Content)
    })
    @GetMapping
    public ResponseEntity<DataContentResponse> dataContent() {
        try {
            return ResponseEntity.ok(new DataContentResponse(
                    readReleaseDate(), readMappingCounts(), readMappingSetCounts()));
        } catch (Exception e) {
            logger.error("Error computing the data content summary", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Total mappings plus the asserted/inferred split, from one facet over {@code inference_type}.
     * {@code numFound} is the total rather than the sum of the buckets, so an unexpected third
     * inference_type value could never make the parts silently exceed the whole.
     */
    private DataContentResponse.Mappings readMappingCounts() throws Exception {
        SolrQuery mappingsQuery = new SolrQuery("*:*");
        mappingsQuery.setRows(0);
        mappingsQuery.setFacet(true);
        mappingsQuery.setFacetMinCount(0);
        mappingsQuery.setFacetLimit(-1);
        mappingsQuery.addFacetField(MappingSetConstants.INFERENCE_TYPE);

        QueryResponse response = solrClient.queryMappings(mappingsQuery);
        FacetField inferenceTypeFacet = response.getFacetField(MappingSetConstants.INFERENCE_TYPE);
        return new DataContentResponse.Mappings(
                response.getResults().getNumFound(),
                bucketCount(inferenceTypeFacet, InferenceType.ASSERTED.getCode()),
                bucketCount(inferenceTypeFacet, InferenceType.SSSOM_INFERENCE.getCode()));
    }

    /**
     * Asserted mapping sets plus the curated/ontology split, from one facet over
     * {@code mapping_set_category} on the asserted-only subset.
     */
    private DataContentResponse.MappingSets readMappingSetCounts() throws Exception {
        SolrQuery setsQuery = new SolrQuery("*:*");
        setsQuery.setRows(0);
        setsQuery.addFilterQuery(ASSERTED_SETS_ONLY);
        setsQuery.setFacet(true);
        setsQuery.setFacetMinCount(0);
        setsQuery.setFacetLimit(-1);
        setsQuery.addFacetField(MappingSetConstants.MAPPING_SET_CATEGORY);

        QueryResponse response = solrClient.queryMappingSets(setsQuery);
        FacetField categoryFacet = response.getFacetField(MappingSetConstants.MAPPING_SET_CATEGORY);
        return new DataContentResponse.MappingSets(
                response.getResults().getNumFound(),
                bucketCount(categoryFacet, MappingSetCategory.CURATED.getCode()),
                bucketCount(categoryFacet, MappingSetCategory.ONTOLOGY.getCode()));
    }

    /**
     * The newest {@code data_release_date} across the mapping sets, as an ISO-8601 string, or null when
     * no set carries one.
     *
     * <p>Read by sorting rather than by a {@code max()} aggregation, and filtered to documents that have
     * the field. The existence filter is what makes the sort unambiguous: with no missing values in the
     * result there is no dependence on how the date field orders documents that lack it, so the first
     * document is the newest release, full stop.
     *
     * <p>Taking the newest — rather than assuming one shared value — is what makes a partial reload
     * read correctly: a resumed run that re-stamps only some sets still reports the release the newest
     * indexed data belongs to.
     */
    private String readReleaseDate() throws Exception {
        SolrQuery releaseDateQuery = new SolrQuery("*:*");
        releaseDateQuery.addFilterQuery(MappingSetConstants.DATA_RELEASE_DATE + ":[* TO *]");
        releaseDateQuery.setRows(1);
        releaseDateQuery.setFields(MappingSetConstants.DATA_RELEASE_DATE);
        releaseDateQuery.addSort(MappingSetConstants.DATA_RELEASE_DATE, SolrQuery.ORDER.desc);

        QueryResponse response = solrClient.queryMappingSets(releaseDateQuery);
        SolrDocumentList documents = response.getResults();
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        Object value = documents.get(0).getFieldValue(MappingSetConstants.DATA_RELEASE_DATE);
        // SolrJ returns a java.util.Date for a date field; normalise to the ISO-8601 UTC form the
        // dataload stamped rather than Date.toString(), which is locale- and timezone-dependent.
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        // A stored-as-string value (a hand-loaded document, or a schema change) is already ISO-8601.
        return value == null ? null : value.toString();
    }

    /** Count in a named facet bucket, 0 when the facet or the bucket is absent. */
    private static long bucketCount(FacetField facetField, String bucketName) {
        if (facetField == null || facetField.getValues() == null) {
            return 0;
        }
        for (FacetField.Count bucket : facetField.getValues()) {
            if (bucketName.equals(bucket.getName())) {
                return bucket.getCount();
            }
        }
        return 0;
    }
}
