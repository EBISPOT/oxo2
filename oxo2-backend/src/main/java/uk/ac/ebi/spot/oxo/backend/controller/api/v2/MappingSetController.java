package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSetSummary;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants.*;

@RestController
@RequestMapping(path = "/api/v2/mapping-sets", produces = {MediaType.APPLICATION_JSON_VALUE})
public class MappingSetController {

    private static final Logger logger = LoggerFactory.getLogger(MappingSetController.class);

    private static final int MAX_ROWS = 10_000;
    private static final String TITLE_SORT_FIELD = MAPPING_SET_TITLE + "_str";

    @Autowired
    private OxOSolrClient solrClient;

    @GetMapping
    public ResponseEntity<List<MappingSetSummary>> listMappingSets(
            @RequestParam(required = false) Boolean inferred) {
        try {
            SolrQuery solrQuery = new SolrQuery("*:*");
            solrQuery.setRows(MAX_ROWS);
            solrQuery.setFields(
                    MAPPING_SET_ID,
                    MAPPING_SET_TITLE,
                    MAPPING_SET_DESCRIPTION,
                    CREATOR_LABEL,
                    MAPPING_PROVIDER,
                    IS_INFERRED);
            // Tri-state filter: null = all sets, true = inferred only, false = asserted only (ADR-0008).
            if (inferred != null) {
                solrQuery.addFilterQuery(IS_INFERRED + ":" + inferred);
            }
            solrQuery.addSort(TITLE_SORT_FIELD, SolrQuery.ORDER.asc);

            QueryResponse response = solrClient.queryMappingSets(solrQuery);
            List<MappingSetSummary> summaries = new ArrayList<>(response.getResults().size());
            for (SolrDocument doc : response.getResults()) {
                summaries.add(new MappingSetSummary(
                        asString(doc.getFieldValue(MAPPING_SET_ID)),
                        asString(doc.getFieldValue(MAPPING_SET_TITLE)),
                        asString(doc.getFieldValue(MAPPING_SET_DESCRIPTION)),
                        asStringList(doc.getFieldValues(CREATOR_LABEL)),
                        asString(doc.getFieldValue(MAPPING_PROVIDER)),
                        Boolean.TRUE.equals(doc.getFieldValue(IS_INFERRED))
                ));
            }
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            logger.error("Error listing mapping sets", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> asStringList(Collection<Object> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(values.size());
        for (Object v : values) {
            if (v != null) out.add(v.toString());
        }
        return out;
    }
}
