package uk.ac.ebi.spot.oxo.dataload.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DataloadSolr {
    private SolrClient solrMappingClient;

    private String solrUrl = System.getenv("SOLR_URL") != null ? System.getenv("SOLR_URL") : "http://localhost:8983/solr";

    private final int connectionTimeoutMillis = envInt("OXO2_SOLR_CONNECT_TIMEOUT_MS", 10000);

    private final int socketTimeoutMillis = envInt("OXO2_SOLR_SOCKET_TIMEOUT_MS", 60000);

    private final Map<String, EntityDetails> entityDetailsCache = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(DataloadSolr.class);

    public DataloadSolr() {

        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .useHttp1_1(true)
                .build();
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(DataloadSolr.class)
                    .warn("Invalid integer for env var {}={}, using default {}", name, value, defaultValue);
            return defaultValue;
        }
    }

    public List<Mapping> querySubjectPredicateObjectIRI(String subjectIRI, String predicateIRI, String objectIRI)  {
        try {
            SolrQuery query = new SolrQuery();

            query.setQuery(String.format("{!term f=%s}%s", MappingEnum.SUBJECT_IRI.getField(),subjectIRI));
            query.addFilterQuery(String.format("{!term f=%s}%s", MappingEnum.PREDICATE_IRI.getField(), predicateIRI));
            query.addFilterQuery(String.format("{!term f=%s}%s", MappingEnum.OBJECT_IRI.getField(), objectIRI));

            QueryResponse response = solrMappingClient.query(query);

            List<Mapping.Builder> mappingBuilders = response.getBeans(Mapping.Builder.class);
            List<Mapping> mappings = mappingBuilders.stream()
                    .map(Mapping.Builder::build)
                    .collect(Collectors.toList());

            return mappings;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EntityDetails queryEntityDetailsForIRI(String iriField, String iriValue, String curieField, String labelField)  {
        String cacheKey = iriField + '\0' + iriValue;
        EntityDetails cached = entityDetailsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:\"%s\"",iriField, iriValue));
            query.setFields(curieField, iriField, labelField);

            QueryResponse response = solrMappingClient.query(query);

            SolrDocumentList docs = response.getResults();

            EntityDetails entityDetails = new EntityDetails();
            entityDetails.setIri(iriValue);

            for (SolrDocument doc : docs) {
                if (!entityDetails.isCuriePresent() && doc.getFieldValue(curieField) != null) {
                    entityDetails.setCurie(doc.getFieldValue(curieField).toString());
                }
                if (!entityDetails.isLabelPresent() && doc.getFieldValue(labelField) != null) {
                    entityDetails.setLabel(doc.getFieldValue(labelField).toString());
                }
                if (entityDetails.areAllFieldsPresent()) {
                    break;
                }
            }

            entityDetailsCache.put(cacheKey, entityDetails);
            return entityDetails;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws Exception {
        solrMappingClient.close();
    }
}
