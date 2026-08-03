package uk.ac.ebi.spot.oxo.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    /** The per-entity typeahead read model (ADR-0034). */
    public static final String ENTITIES_COLLECTION = "oxo2-entities";
    /** numFound.json key holding {@link #distinctSpoKeys()} — not a collection, so named apart. */
    public static final String SPO_GROUPS_KEY = "oxo2-mappings-spo-groups";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SolrCheck() {}

    /** Document count in a collection carrying the given inference_type code (ASSERTED /
     *  SSSOM_INFERENCE). Codes are safe enum names, so no escaping is needed. */
    /**
     * Total documents in a collection. For oxo2-entities this is the DISTINCT entity count — the
     * property that makes the collection worth having, so it is the thing worth pinning in a golden.
     */
    public static int numFound(String collection) throws IOException, InterruptedException {
        return numFoundForQuery(collection, "*:*");
    }

    public static int numFoundByInferenceType(String collection, String inferenceTypeCode)
            throws IOException, InterruptedException {
        return numFoundForQuery(collection, "inference_type:" + inferenceTypeCode);
    }

    /**
     * Distinct {@code spo_key} values in {@code oxo2-mappings} — the number of rows the collapsed
     * result views render for a whole-collection query (ADR-0013 / ADR-0023).
     *
     * <p>Worth pinning separately from the document counts because the two move independently: a
     * key that conflates unrelated mappings leaves every document count untouched while silently
     * merging rows. That is exactly how literal subjects — free text with no {@code subject_id} —
     * hashed identically until ADR-0042, collapsing thousands of distinct mappings into one row.
     *
     * <p>Counted by enumerating the facet buckets rather than with {@code unique()}, which switches
     * to a HyperLogLog estimate above a threshold; a golden must be exact.
     */
    public static int distinctSpoKeys() throws IOException, InterruptedException {
        JsonNode body = query(MAPPINGS_COLLECTION,
                "q=*:*&rows=0&facet=true&facet.field=spo_key&facet.limit=-1&facet.mincount=1&wt=json");
        JsonNode buckets = body.path("facet_counts").path("facet_fields").path("spo_key");
        if (!buckets.isArray()) {
            throw new IOException("No spo_key facet in Solr response: " + body);
        }
        // Solr renders a facet field as a flat [value, count, value, count, ...] array.
        return buckets.size() / 2;
    }

    private static int numFoundForQuery(String collection, String query)
            throws IOException, InterruptedException {
        JsonNode body = query(collection,
                "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&rows=0&wt=json");
        JsonNode numFoundNode = body.path("response").path("numFound");
        if (numFoundNode.isMissingNode() || !numFoundNode.isNumber()) {
            throw new IOException("No numFound in Solr response for " + collection + ": " + body);
        }
        return numFoundNode.asInt();
    }

    private static JsonNode query(String collection, String queryString)
            throws IOException, InterruptedException {
        String url = Env.solrHost().replaceAll("/+$", "") + "/" + collection + "/select?" + queryString;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Solr query failed (" + response.statusCode() + "): " + url + "\n" + response.body());
        }
        return MAPPER.readTree(response.body());
    }
}
