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
 * Queries Solr's `oxo2-mappings` and `oxo2-mappingsets` collections for document counts by
 * `inference_type` (ADR-0011). Because each fixture is loaded in isolation (one pipeline pass over
 * only its set(s)), a whole-collection count by inference_type is exactly that fixture's
 * asserted / OWL-inferred / SSSOM-inferred totals — no per-set-id scoping needed.
 */
public final class SolrCheck {

    public static final String MAPPINGS_COLLECTION = "oxo2-mappings";
    public static final String MAPPINGSETS_COLLECTION = "oxo2-mappingsets";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SolrCheck() {}

    /** Document count in a collection carrying the given inference_type code (ASSERTED /
     *  OWL_INFERENCE / SSSOM_INFERENCE). Codes are safe enum names, so no escaping is needed. */
    public static int numFoundByInferenceType(String collection, String inferenceTypeCode)
            throws IOException, InterruptedException {
        return numFoundForQuery(collection, "inference_type:" + inferenceTypeCode);
    }

    private static int numFoundForQuery(String collection, String query)
            throws IOException, InterruptedException {
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
}
