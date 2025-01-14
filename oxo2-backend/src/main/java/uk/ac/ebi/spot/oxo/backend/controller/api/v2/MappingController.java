package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.List;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_TYPE;

@RestController
@RequestMapping("/api/v2/mappings")
public class MappingController {
    @Autowired
    private OxOSolrClient solrClient;

    @GetMapping("/{subjectId}")
    public ResponseEntity<Page<Mapping>> getMappingsById(@PathVariable String subjectId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(SUBJECT_TYPE + ":" + subjectId);
            solrQuery.setStart((int) pageable.getOffset());
            solrQuery.setRows(pageable.getPageSize());

            List<Mapping> mappings = solrClient.query(solrQuery);
            long total = solrClient.count(solrQuery);

            Page<Mapping> mappingPage = new PageImpl<>(mappings, pageable, total);
            return ResponseEntity.ok(mappingPage);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
