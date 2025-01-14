package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.SolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.List;

@Service
public class OxOSolrClient {
    private SolrClient solrMappingsClient;

    @Value("${solr.url}")
    private String solrUrl;

    @Value("${connectionTimeoutMillis}")
    private int connectionTimeoutMillis;

    @Value("${socketTimeoutMillis}")
    private int socketTimeoutMillis;

    @PostConstruct
    public void init() {
        this.solrMappingsClient = new HttpSolrClient.Builder(solrUrl+"/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis)
                .withSocketTimeout(socketTimeoutMillis)
                .build();
    }

    public List<Mapping> query(SolrParams params) throws Exception {
        QueryResponse response = solrMappingsClient.query(params);
        return response.getBeans(Mapping.class);
    }

    public long count(SolrParams params) throws Exception {
        QueryResponse response = solrMappingsClient.query(params);
        return response.getResults().getNumFound();
    }

    @PreDestroy
    public void close() throws Exception {
        solrMappingsClient.close();
    }
}
