package uk.ac.ebi.spot.oxo.backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.FacetedMappingResponse;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Service
public class OxOSolrClient {
    private SolrClient solrMappingClient;

    @Value("${solr.url}")
    private String solrUrl;

    @Value("${connectionTimeoutMillis}")
    private int connectionTimeoutMillis;

    @Value("${socketTimeoutMillis}")
    private int socketTimeoutMillis;

    private static final Logger logger = LoggerFactory.getLogger(OxOSolrClient.class);

    @PostConstruct
    public void init() {
        logger.debug("Init solrMappingClient: oxoSolrUrl={}", solrUrl);
        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
    }

    public FacetedMappingResponse query(SolrParams params, Pageable pageable) throws Exception {
        QueryResponse response = solrMappingClient.query(params);

        List<Mapping.Builder> mappingBuilders = response.getBeans(Mapping.Builder.class);
        List<Mapping> mappings = mappingBuilders.stream()
                .map(Mapping.Builder::build)
                .collect(Collectors.toList());

        Page<Mapping> mappingPage = new PageImpl<>(mappings, pageable, response.getResults().getNumFound());

        Map<String, Map<String, Long>> facetFieldToCounts = getFacetFieldToCounts(response);

        return new FacetedMappingResponse(mappingPage, facetFieldToCounts);
    }

    private static Map<String, Map<String, Long>> getFacetFieldToCounts(QueryResponse response) {
        Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
        if (response.getFacetFields() != null) {
            for (FacetField facetField : response.getFacetFields()) {
                Map<String, Long> valueToCount = new LinkedHashMap<>();
                for(FacetField.Count count : facetField.getValues()) {
                    if (count.getCount() > 0)
                        valueToCount.put(count.getName(), count.getCount());
                }
                facetFieldToCounts.put(facetField.getName(), valueToCount);
            }
        }
        return facetFieldToCounts;
    }

    @PreDestroy
    public void close() throws Exception {
        solrMappingClient.close();
    }
}
