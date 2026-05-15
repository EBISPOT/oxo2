package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Queries Solr's `oxo2-mappings` and `oxo2-mappingsets` collections for the numFound of
 * documents whose `mapping_set_id` matches the test fixture's set id. Uses the standard
 * Solr HTTP select endpoint to avoid pulling SolrJ as a heavyweight dependency here.
 */
public final class SolrCheck {

    public static final String MAPPINGS_COLLECTION = "oxo2-mappings";
    public static final String MAPPINGSETS_COLLECTION = "oxo2-mappingsets";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SolrCheck() {}

    public static int numFound(String collection, String mappingSetId) throws IOException, InterruptedException {
        String query = "mapping_set_id:\"" + mappingSetId + "\"";
        String url = Env.solrHost().replaceAll("/+$", "") + "/" + collection +
                "/select?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&rows=0&wt=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Solr query failed (" + response.statusCode() + "): " + url + "\n" + response.body());
        }
        JsonNode body = MAPPER.readTree(response.body());
        JsonNode numFoundNode = body.path("response").path("numFound");
        if (numFoundNode.isMissingNode() || !numFoundNode.isNumber()) {
            throw new IOException("No numFound in Solr response from " + url + ": " + response.body());
        }
        return numFoundNode.asInt();
    }

    /** Returns the canonical mapping_set_id baked into rule fixtures. Must match what
     *  the TSVs use. Single source of truth. */
    public static String mappingSetIdForRule(String rule) {
        return "https://w3id.org/oxo2/test/minimal/rules/" + rule;
    }

    /** Inferred-mapping-set id produced by explanations2json.nf. Pattern verified against
     *  Solr facet on $OXO2_DATA/inferences/solr/mappingSet/*-mappingSet.json content:
     *  the inferred set id is the asserted mapping_set_id URL-encoded and prefixed with
     *  the oxo inferences namespace. */
    public static String inferredMappingSetIdForRule(String rule) {
        String asserted = mappingSetIdForRule(rule);
        return "https://www.ebi.ac.uk/spot/oxo/inferences/" +
                URLEncoder.encode(asserted, StandardCharsets.UTF_8);
    }
}
