package uk.ac.ebi.spot.oxo.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.FacetedMappingResponse;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Service
public class OxOSolrClient {
    private SolrClient solrMappingClient;
    private SolrClient solrMappingSetClient;

    @Value("${solr.url}")
    private String solrUrl;

    @Value("${connectionTimeoutMillis}")
    private int connectionTimeoutMillis;

    @Value("${socketTimeoutMillis}")
    private int socketTimeoutMillis;

    // The app-configured Jackson mapper (Optionals unwrapped, same as response serialisation), used
    // to build the group_members JSON string on a grouped query's representative (ADR-0013).
    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(OxOSolrClient.class);

    @PostConstruct
    public void init() {
        logger.debug("Init solrMappingClient: oxoSolrUrl={}", solrUrl);
        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
        this.solrMappingSetClient = new HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappingsets")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
    }

    public QueryResponse queryMappingSets(SolrParams params) throws Exception {
        return solrMappingSetClient.query(params);
    }

    public FacetedMappingResponse query(SolrParams params, Pageable pageable) throws Exception {
        QueryResponse response = solrMappingClient.query(params);

        GroupResponse groupResponse = response.getGroupResponse();
        Page<Mapping> mappingPage = (groupResponse != null && !groupResponse.getValues().isEmpty())
                ? buildGroupedPage(groupResponse.getValues().get(0), pageable)
                : buildFlatPage(response, pageable);

        Map<String, Map<String, Long>> facetFieldToCounts = getFacetFieldToCounts(response);

        return new FacetedMappingResponse(mappingPage, facetFieldToCounts);
    }

    private Page<Mapping> buildFlatPage(QueryResponse response, Pageable pageable) {
        List<Mapping> mappings = response.getBeans(Mapping.Builder.class).stream()
                .map(Mapping.Builder::build)
                .collect(Collectors.toList());
        return new PageImpl<>(mappings, pageable, response.getResults().getNumFound());
    }

    /**
     * Build a page of group representatives (ADR-0013). Each group's top document — the highest
     * inference-tier member, via {@code group.sort=score} — becomes the representative row, carrying
     * its members and true size as a group_members JSON string ({@code {"total":N,"members":[...]}}).
     * The page total is the group count ({@code getNGroups}), so pagination counts triples, not
     * documents.
     */
    private Page<Mapping> buildGroupedPage(GroupCommand command, Pageable pageable) throws Exception {
        DocumentObjectBinder binder = solrMappingClient.getBinder();
        List<Mapping> representatives = new ArrayList<>();
        for (Group group : command.getValues()) {
            SolrDocumentList memberDocs = group.getResult();
            List<Mapping.Builder> builders = binder.getBeans(Mapping.Builder.class, memberDocs);
            if (builders.isEmpty()) {
                continue;
            }
            List<Mapping> members = builders.stream()
                    .map(Mapping.Builder::build)
                    .collect(Collectors.toList());
            String groupMembers = serializeGroupMembers(members, memberDocs.getNumFound());
            representatives.add(builders.get(0).groupMembersAsString(groupMembers).build());
        }
        int totalGroups = command.getNGroups() != null ? command.getNGroups() : representatives.size();
        return new PageImpl<>(representatives, pageable, totalGroups);
    }

    private String serializeGroupMembers(List<Mapping> members, long total) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("total", total);
        root.set("members", objectMapper.valueToTree(members));
        return objectMapper.writeValueAsString(root);
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
