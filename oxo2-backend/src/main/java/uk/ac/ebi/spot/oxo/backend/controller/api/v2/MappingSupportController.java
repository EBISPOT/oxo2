package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;

/** Dataset-wide evidence for one mapping group, independent of search visibility filters. */
@Tag(name = "Mappings")
@RestController
@RequestMapping(path = "/api/v2/mappings", produces = "application/json")
public class MappingSupportController {
    private static final Logger logger = LoggerFactory.getLogger(MappingSupportController.class);
    @Autowired private OxOSolrClient solrClient;

    @Schema(description = "Dataset-wide support for one subject/predicate/modifier/object group.")
    public record SupportResponse(
            @Schema(description = "Page of summary records, including the selected record in the group. "
                    + "totalElements counts all group records; number is the zero-based page. "
                    + "Explanation trees are omitted; fetch each record's details to inspect them.")
            MappingSearchResponse.MappingPage mappings,
            @Schema(description = "Distinct nonblank mapping_justification identifiers across the entire group, "
                    + "not just this page.", minimum = "0")
            long justificationMethodCount,
            @Schema(description = "Distinct nonblank mapping_set_id values among ASSERTED records in the "
                    + "entire group. Inference premises making other statements are excluded. "
                    + "Zero for inferred-only groups; not a count of independent evidence.", minimum = "0")
            long assertedSourceCount) {}

    @GetMapping("/{mappingId}/support")
    @Operation(summary = "All supporting records for a mapping",
            description = """
                    All records sharing the selected mapping's indexed subject, predicate, modifier and object, \
                    including the selected record itself. Positive and negated statements remain separate.

                    This endpoint is independent of search filters: corpus, mapping-set, justification, \
                    weak-predicate and obsolete-term filters do not restrict the group or its counts. \
                    Records are not collapsed. They are ordered by the default ranking, then index ID \
                    for stable paging. Pages beyond the end return empty content with the full totals.

                    Counts cover the entire dataset-wide group, not the current page. Asserted sources \
                    are distinct mapping sets directly asserting this statement, not inference premises \
                    or a claim of independent evidence. Summary records omit explanation trees. Use \
                    `GET /api/sssom/mappings/{id}` for an individual record's full details.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of supporting records and group-wide counts"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size, or page × size exceeds 2147483647",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Mapping ID not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve supporting records", content = @Content)
    })
    public ResponseEntity<SupportResponse> support(
            @Parameter(description = "ID of any mapping record in the group (not a subject CURIE).")
            @PathVariable String mappingId,
            @Parameter(description = "Zero-based page number.", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Records per page.", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
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
