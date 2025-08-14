package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.FacetedMappingResponse;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@RestController
@RequestMapping(path="/api/v2/mappings", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingController {
    @Autowired
    private OxOSolrClient solrClient;
    private static final Logger logger = LoggerFactory.getLogger(MappingController.class);

    @GetMapping("/{subjectId}")
    public ResponseEntity<FacetedMappingResponse> getMappingsById(@PathVariable String subjectId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        try {
            String decodedSubjectId = URLDecoder.decode(subjectId, StandardCharsets.UTF_8.name());
            Pageable pageable = PageRequest.of(page, size);
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(MappingEnum.SUBJECT_ID.getField() + ":\"" + decodedSubjectId + "\"");
            solrQuery.setStart((int) pageable.getOffset());
            solrQuery.setRows(pageable.getPageSize());

            FacetedMappingResponse facetedMappingResponse = solrClient.query(solrQuery, pageable);
            return ResponseEntity.ok(facetedMappingResponse);
        } catch (Exception e) {
            logger.error("Error while fetching mappings for subjectId: {}", subjectId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping(path = "/search",
            /*consumes = {MediaType.APPLICATION_JSON_VALUE},*/ produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<FacetedMappingResponse> getMappings(@RequestBody MappingSearchRequest mappingSearchRequest) {

        logger.info("Mapping search request: {}", mappingSearchRequest);

        Pageable pageable = PageRequest.of(mappingSearchRequest.getPage(), mappingSearchRequest.getSize());
        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(mappingSearchRequest, pageable);

        logger.trace("Solr query={}", solrQuery.toString());

        try {
            FacetedMappingResponse facetedMappingResponse = solrClient.query(solrQuery, pageable);
            return ResponseEntity.ok(facetedMappingResponse);
        } catch (Exception e) {
            logger.error("Error while fetching mappings for subjectId: {}", mappingSearchRequest, e);
            return ResponseEntity.status(500).build();
        }
    }
}
