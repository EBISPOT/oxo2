package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_ID;

@RestController
@RequestMapping("/api/v2/mappings")
public class MappingController {
    @Autowired
    private OxOSolrClient solrClient;
    private static final Logger logger = LoggerFactory.getLogger(MappingController.class);
    @GetMapping("/{subjectId}")
    public ResponseEntity<Page<Mapping>> getMappingsById(@PathVariable String subjectId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        try {
            String decodedSubjectId = URLDecoder.decode(subjectId, StandardCharsets.UTF_8.name());
            Pageable pageable = PageRequest.of(page, size);
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(SUBJECT_ID + ":\"" + decodedSubjectId + "\"");
            solrQuery.setStart((int) pageable.getOffset());
            solrQuery.setRows(pageable.getPageSize());

            List<Mapping.Builder> mappingBuilders = solrClient.query(solrQuery);
            List<Mapping> mappings = mappingBuilders.stream()
                    .map(Mapping.Builder::build)
                    .collect(Collectors.toList());
            long total = solrClient.count(solrQuery);

            Page<Mapping> mappingPage = new PageImpl<>(mappings, pageable, total);
            return ResponseEntity.ok(mappingPage);
        } catch (Exception e) {
            logger.error("Error while fetching mappings for subjectId: {}", subjectId, e);
            return ResponseEntity.status(500).build();
        }
    }

}
