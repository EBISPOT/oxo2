package uk.ac.ebi.spot.oxo.entities;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Derives the {@code oxo2-entities} collection — one document per DISTINCT entity — from the
 * already-indexed {@code oxo2-mappings} collection (ADR-0034).
 *
 * <p>{@code oxo2-mappings} is denormalised: one document per mapping, with each endpoint's CURIE,
 * label and IRI copied onto it. So an entity is seen once per mapping it participates in. This tool
 * folds those sightings into one document per entity, carrying the mapping counts that become the
 * typeahead's popularity ranking.
 *
 * <p><b>Why it is sharded by CURIE prefix.</b> Folding requires holding the entity map in memory,
 * and the whole corpus will not fit. Every mapping that touches an entity of prefix {@code P} is
 * matched by {@code subject_prefix:P OR object_prefix:P}, and only the sides whose own prefix is
 * {@code P} are folded — so a shard's counts are exact, and its heap is bounded by ONE ontology's
 * entity count rather than the corpus's. This mirrors the per-file parallelism of {@code
 * sssom2json.nf} and the per-bundle parallelism of {@code explanations2json.nf}.
 *
 * <p>Run once per prefix, plus once for {@link #NO_PREFIX_SHARD} — entities whose CURIE never
 * resolved to a prefix (a bare IRI, ADR-0024) belong to no prefix shard and would otherwise be
 * silently dropped from the collection.
 *
 * <p>Modes:
 * <pre>
 *   --list-prefixes -o prefixes.txt      the distinct CURIE prefixes, one per line, plus __none__
 *   -p MONDO        -o MONDO.json        the entity documents for one prefix shard
 * </pre>
 */
public class Mappings2Entities {

    private static final Logger logger = LoggerFactory.getLogger(Mappings2Entities.class);

    /**
     * Sentinel shard for entities with no CURIE prefix. A bare IRI that never resolved to a CURIE
     * (ADR-0024) has no prefix, so it is matched by no {@code prefix:P} shard. Without this shard
     * such entities would be absent from the typeahead entirely — a silent hole, not an error.
     */
    static final String NO_PREFIX_SHARD = "__none__";

    private static final String MAPPINGS_COLLECTION = "/oxo2-mappings";

    private static final String SUBJECT_ID = "subject_id";
    private static final String SUBJECT_LABEL = "subject_label";
    private static final String SUBJECT_IRI = "subject_iri";
    private static final String SUBJECT_PREFIX = "subject_prefix";
    private static final String OBJECT_ID = "object_id";
    private static final String OBJECT_LABEL = "object_label";
    private static final String OBJECT_IRI = "object_iri";
    private static final String OBJECT_PREFIX = "object_prefix";

    /**
     * Read so each sighting can be bucketed by predicate (ADR-0035). The canonical IRI, not
     * {@code predicate_id}, because the CURIE's prefix and casing vary by source set — the same
     * reason {@code SolrQueryBuilder} matches its exclusion on the IRI.
     */
    private static final String PREDICATE_IRI = "predicate_iri";

    /** Rows per cursorMark page. Seven small stored fields per doc, so a large page is cheap. */
    private static final int PAGE_SIZE = 20_000;

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("p").longOpt("prefix").hasArg()
                .desc("CURIE prefix to shard on, e.g. MONDO, or " + NO_PREFIX_SHARD).build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required()
                .desc("output file").build());
        options.addOption(Option.builder().longOpt("list-prefixes")
                .desc("write the distinct CURIE prefixes (one per line) instead of entities").build());

        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (Exception parseFailure) {
            new HelpFormatter().printHelp("mappings2entities", options);
            throw parseFailure;
        }

        File output = new File(commandLine.getOptionValue("o"));
        String solrUrl = System.getenv("SOLR_URL") != null
                ? System.getenv("SOLR_URL")
                : "http://localhost:8983/solr";

        try (SolrClient solr = new HttpJdkSolrClient.Builder(solrUrl + MAPPINGS_COLLECTION)
                .withConnectionTimeout(envInt("OXO2_SOLR_CONNECT_TIMEOUT_MS", 10_000), MILLISECONDS)
                .withIdleTimeout(envInt("OXO2_SOLR_SOCKET_TIMEOUT_MS", 600_000), MILLISECONDS)
                .useHttp1_1(true)
                .build()) {

            Mappings2Entities mappings2Entities = new Mappings2Entities(solr);
            if (commandLine.hasOption("list-prefixes")) {
                mappings2Entities.writePrefixes(output);
            } else {
                String prefix = commandLine.getOptionValue("p");
                if (prefix == null || prefix.isBlank()) {
                    new HelpFormatter().printHelp("mappings2entities", options);
                    throw new IllegalArgumentException("-p <prefix> is required unless --list-prefixes");
                }
                mappings2Entities.writeEntitiesForPrefix(prefix, output);
            }
        }
    }

    private final SolrClient solr;

    Mappings2Entities(SolrClient solr) {
        this.solr = solr;
    }

    /**
     * Write the distinct CURIE prefixes present on either side of any mapping, plus the
     * {@link #NO_PREFIX_SHARD} sentinel. This is the same facet {@code OntologyController} issues to
     * list ontologies.
     *
     * <p>Each line is {@code <shardName>\t<prefix>}: the {@link ShardName#of safe filename stem} for
     * the shard, then the true prefix that keys its Solr query. The two are decoupled because a
     * prefix may be a valid Solr facet value yet an unsafe filename — a slash-bearing
     * malformed-IRI-as-CURIE like {@code SRAO_/SRAO} (ADR-0031). A CURIE carries no whitespace, so a
     * tab can never occur inside a prefix and the split is unambiguous.
     */
    void writePrefixes(File output) throws Exception {
        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        query.setFacet(true);
        query.setFacetMinCount(1);
        query.setFacetLimit(-1);
        query.addFacetField(SUBJECT_PREFIX, OBJECT_PREFIX);

        QueryResponse response = solr.query(query);

        // TreeSet: the shard list drives one Nextflow task per line, so a stable order keeps the
        // task names (and the -resume cache) stable across runs.
        TreeSet<String> prefixes = new TreeSet<>();
        for (FacetField facetField : response.getFacetFields()) {
            for (FacetField.Count count : facetField.getValues()) {
                if (count.getName() != null && !count.getName().isBlank()) {
                    prefixes.add(count.getName());
                }
            }
        }
        prefixes.add(NO_PREFIX_SHARD);

        StringBuilder lines = new StringBuilder();
        for (String prefix : prefixes) {
            lines.append(ShardName.of(prefix)).append('\t').append(prefix).append('\n');
        }
        Files.write(output.toPath(), lines.toString().getBytes());
        logger.info("Wrote {} prefix shard(s) to {}", prefixes.size(), output);
    }

    /**
     * Fold every mapping touching an entity of {@code prefix} into one document per such entity, and
     * write them as a Solr-update JSON array (the shape {@code json2solr.sh} POSTs).
     */
    void writeEntitiesForPrefix(String prefix, File output) throws Exception {
        // LinkedHashMap: insertion-ordered output makes a shard's JSON diffable run-to-run, which is
        // what lets the integration-test goldens pin this stage.
        Map<String, EntityDoc> entities = new LinkedHashMap<>();

        SolrQuery query = new SolrQuery("*:*");
        query.addFilterQuery(shardFilter(prefix));
        query.setFields(SUBJECT_ID, SUBJECT_LABEL, SUBJECT_IRI, OBJECT_ID, OBJECT_LABEL, OBJECT_IRI,
                PREDICATE_IRI);
        query.setRows(PAGE_SIZE);
        // cursorMark requires a total ordering, so the sort must end in the uniqueKey.
        query.setSort(SolrQuery.SortClause.asc("id"));

        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        long mappingsScanned = 0;
        while (true) {
            query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solr.query(query);
            SolrDocumentList documents = response.getResults();

            for (SolrDocument document : documents) {
                observe(entities, prefix, document, true);
                observe(entities, prefix, document, false);
            }
            mappingsScanned += documents.size();

            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }

        write(entities, output);
        logger.info("Shard {}: scanned {} mappings -> {} distinct entities, written to {}",
                prefix, mappingsScanned, entities.size(), output);
    }

    /**
     * Matches every mapping with an entity of this shard's prefix on EITHER side. The Java fold then
     * keeps only the sides that actually belong to the shard, so counts stay exact and no entity is
     * folded into two shards.
     */
    private static String shardFilter(String prefix) {
        if (NO_PREFIX_SHARD.equals(prefix)) {
            // A prefixless entity has no value in subject_prefix/object_prefix, so it is matched by
            // the ABSENCE of that field. Each negation needs its own *:* base — a bare pure-negative
            // clause inside an OR matches nothing in Lucene syntax.
            return "(*:* -" + SUBJECT_PREFIX + ":[* TO *]) OR (*:* -" + OBJECT_PREFIX + ":[* TO *])";
        }
        String escaped = ClientUtils.escapeQueryChars(prefix);
        return SUBJECT_PREFIX + ":" + escaped + " OR " + OBJECT_PREFIX + ":" + escaped;
    }

    private static void observe(Map<String, EntityDoc> entities, String shardPrefix,
                                SolrDocument document, boolean asSubject) {
        String id = string(document, asSubject ? SUBJECT_ID : OBJECT_ID);
        if (id == null || id.isBlank()) {
            return;
        }

        // Derive the prefix from the CURIE rather than reading subject_prefix/object_prefix, so the
        // writer and the index agree by construction: EntityReference.getCuriePrefix is the single
        // source of truth that populated those fields in the first place (ADR-0031).
        Optional<String> curiePrefix = new EntityReference(id).getCuriePrefix();
        boolean belongsToShard = NO_PREFIX_SHARD.equals(shardPrefix)
                ? curiePrefix.isEmpty()
                : curiePrefix.filter(shardPrefix::equals).isPresent();
        if (!belongsToShard) {
            return;
        }

        // Null for any predicate the default search shows — the common case. Bucketing on the way in
        // is what lets the typeahead offer exactly the entities the user's current predicate
        // checkboxes will actually return (ADR-0035).
        WeakPredicate weakPredicate = weakPredicateOf(string(document, PREDICATE_IRI));

        entities.computeIfAbsent(id, key -> new EntityDoc(key, curiePrefix.orElse(null)))
                .observe(string(document, asSubject ? SUBJECT_LABEL : OBJECT_LABEL),
                        string(document, asSubject ? SUBJECT_IRI : OBJECT_IRI),
                        asSubject, weakPredicate);
    }

    /** The weak predicate this IRI names, or null when it names any other (i.e. a strong) predicate. */
    private static WeakPredicate weakPredicateOf(String predicateIri) {
        if (predicateIri == null) {
            return null;
        }
        for (WeakPredicate candidate : WeakPredicate.values()) {
            if (candidate.iri().equals(predicateIri)) {
                return candidate;
            }
        }
        return null;
    }

    private static String string(SolrDocument document, String field) {
        Object value = document.getFirstValue(field);
        return value == null ? null : value.toString();
    }

    /** Streamed rather than built in memory: a large ontology's shard is a lot of JSON. */
    private void write(Map<String, EntityDoc> entities, File output) throws Exception {
        try (OutputStream out = Files.newOutputStream(output.toPath());
             JsonGenerator json = new JsonFactory().createGenerator(out, JsonEncoding.UTF8)) {
            json.writeStartArray();
            for (EntityDoc entity : entities.values()) {
                json.writeStartObject();
                json.writeStringField(EntityConstants.ID, entity.id());
                if (entity.label() != null) {
                    json.writeStringField(EntityConstants.LABEL, entity.label());
                }
                if (entity.iri() != null) {
                    json.writeStringField(EntityConstants.IRI, entity.iri());
                }
                if (entity.prefix() != null) {
                    json.writeStringField(EntityConstants.PREFIX, entity.prefix());
                }
                json.writeNumberField(EntityConstants.SUBJECT_COUNT, entity.subjectCount());
                json.writeNumberField(EntityConstants.OBJECT_COUNT, entity.objectCount());
                json.writeNumberField(EntityConstants.MAPPING_COUNT, entity.mappingCount());
                json.writeBooleanField(EntityConstants.IS_SUBJECT, entity.subjectCount() > 0);
                json.writeBooleanField(EntityConstants.IS_OBJECT, entity.objectCount() > 0);

                // The buckets the typeahead filters and ranks on (ADR-0035). Written unconditionally,
                // zeros included: the suggest sums the enabled buckets as a function query, and a
                // missing docValue would make the whole sum unusable rather than merely zero.
                json.writeNumberField(EntityConstants.SUBJECT_COUNT_STRONG, entity.subjectCountStrong());
                json.writeNumberField(EntityConstants.OBJECT_COUNT_STRONG, entity.objectCountStrong());
                for (WeakPredicate weakPredicate : WeakPredicate.values()) {
                    json.writeNumberField(EntityConstants.subjectCountField(weakPredicate),
                            entity.subjectCount(weakPredicate));
                    json.writeNumberField(EntityConstants.objectCountField(weakPredicate),
                            entity.objectCount(weakPredicate));
                }
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notAnInt) {
            logger.warn("Invalid integer for env var {}={}, using default {}", name, value, defaultValue);
            return defaultValue;
        }
    }
}
