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
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DataloadSolr {
    private SolrClient solrMappingClient;

    private String solrUrl = "http://localhost:8983/solr";

    private int connectionTimeoutMillis = 10000;

    private int socketTimeoutMillis = 60000;


    private static final Logger logger = LoggerFactory.getLogger(DataloadSolr.class);

    public DataloadSolr() {

        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
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

            return entityDetails;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws Exception {
        solrMappingClient.close();
    }
}
