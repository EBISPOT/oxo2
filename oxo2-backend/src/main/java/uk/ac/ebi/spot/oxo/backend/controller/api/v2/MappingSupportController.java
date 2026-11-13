package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;

/** Dataset-wide evidence for one mapping group, independent of search visibility filters. */
@RestController
@RequestMapping(path = "/api/v2/mappings", produces = "application/json")
public class MappingSupportController {
    private static final Logger logger = LoggerFactory.getLogger(MappingSupportController.class);
    @Autowired private OxOSolrClient solrClient;

    public record SupportResponse(MappingSearchResponse.MappingPage mappings,
                                  long justificationMethodCount, long assertedSourceCount) {}

    @GetMapping("/{mappingId}/support")
    @Operation(summary = "All supporting records for a mapping",
            description = "Paged records sharing the mapping's subject, predicate, modifier and object. "
                    + "Counts cover the whole group across the dataset, including records hidden by search filters. "
                    + "Asserted sources are distinct mapping sets, not a claim of independent evidence.")
    public ResponseEntity<SupportResponse> support(@PathVariable String mappingId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MappingController.MAX_PAGE_SIZE
                || (long) page * size > Integer.MAX_VALUE) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // Resolve the indexed key, not a reconstruction from labels/IRIs. This preserves
            // prefix normalisation, negation and literal subjects exactly as dataload defines them.
            SolrQuery lookup = new SolrQuery("mapping_id:\"" + ClientUtils.escapeQueryChars(mappingId) + "\"");
            lookup.setFields("spo_key");
            lookup.setRows(1);
            var found = solrClient.queryMappings(lookup).getResults();
            if (found.isEmpty()) return ResponseEntity.notFound().build();
            Object key = found.getFirst().getFieldValue("spo_key");
            if (key == null) throw new IllegalStateException("Mapping has no group key");

            SolrQuery query = new SolrQuery("spo_key:\"" + ClientUtils.escapeQueryChars(key.toString()) + "\"");
            query.set("defType", "edismax");
            SolrQueryBuilder.applyProvenanceRanking(query);
            query.setSort("score", SolrQuery.ORDER.desc);
            query.addSort("id", SolrQuery.ORDER.asc);
            query.setStart(page * size);
            query.setRows(size);
            // The evidence table links to each full record; do not fetch twenty potentially large
            // explanation trees just to show provenance and justification summaries.
            query.setFields("mapping_id", "subject_id", "subject_label", "predicate_id", "predicate_modifier",
                    "object_id", "object_label", "mapping_justification", "mapping_set_id", "mapping_set_title",
                    "mapping_provider", "confidence", "inference_type", "distance");
            // Deliberately no collapse or search filters. Facets count the entire group, not this page.
            query.set("json.facet", "{methods:{type:terms,field:mapping_justification,limit:-1},"
                    + "asserted:{type:query,q:'inference_type:ASSERTED',"
                    + "facet:{sources:{type:terms,field:mapping_set_id,limit:-1}}}}");
            var result = solrClient.querySssomMappings(query, PageRequest.of(page, size));
            QueryResponse response = result.response();
            var facets = response.getJsonFacetingResponse();
            long methods = facets.getBucketBasedFacets("methods").getBuckets().stream()
                    .filter(bucket -> !bucket.getVal().toString().isBlank()).count();
            var asserted = facets.getQueryFacet("asserted");
            // Solr omits subfacets for an empty query domain. Inferred-only groups have no
            // directly asserted records, so their missing sources facet means zero, not an error.
            long sources = asserted.getCount() == 0 ? 0 : asserted.getBucketBasedFacets("sources").getBuckets().stream()
                    .filter(bucket -> !bucket.getVal().toString().isBlank()).count();
            return ResponseEntity.ok(new SupportResponse(new MappingSearchResponse(result.page()).getMappings(),
                    methods, sources));
        } catch (Exception e) {
            logger.error("Error fetching supporting mappings for {}", mappingId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
