package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.helper.SuggestFields;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

/**
 * Warms the facet caches the suggest endpoints depend on (ADR-0034).
 *
 * <p><b>Why this is needed.</b> The mapping schema's {@code string} fields declare no docValues, so
 * the first {@code facet.field} on one makes Solr uninvert it — build the field-cache entry by
 * walking the whole index. On a high-cardinality field that costs seconds, and the person who pays
 * it is whoever types into a filter box first after a deploy.
 *
 * <p><b>Why it is here and not in solrconfig.xml.</b> A {@code firstSearcher} listener would be the
 * natural home, but {@code solrconfig.xml} only reaches Solr through {@code copySolrConfig.sh}, which
 * runs only on a wipe — i.e. only on a full reindex. Putting the warm-up in the backend means it
 * takes effect on a restart, which is the event that actually invalidates the caches.
 *
 * <p>The index is static between dataloads, so this is a one-off per searcher, not a poll. Failures
 * are logged and swallowed: a cold cache is slow, not broken, and it must never stop the app coming
 * up (Solr may legitimately not be ready yet, or be empty before the first load).
 */
@Component
public class SuggestFacetWarmup {

    private static final Logger logger = LoggerFactory.getLogger(SuggestFacetWarmup.class);

    /**
     * The fields worth warming: the ones the result-table column filters facet, which are the ones a
     * user reaches within seconds of a search. The rest of the vocabulary fields are low-cardinality
     * enough that uninverting them is not noticeable.
     */
    private static final MappingEnum[] WARM_FIELDS = {
            MappingEnum.OBJECT_ID,
            MappingEnum.OBJECT_LABEL,
            MappingEnum.PREDICATE_ID,
            MappingEnum.PREDICATE_LABEL,
            MappingEnum.MAPPING_JUSTIFICATION,
    };

    @Autowired
    private OxOSolrClient solrClient;

    /**
     * Warm on a daemon thread of our own rather than with {@code @Async}: the application does not
     * enable async execution, so {@code @Async} would be silently ignored and this would run ON the
     * startup thread — holding the app in "starting" for exactly the seconds it exists to hide.
     * A daemon thread also cannot keep a shutting-down JVM alive mid-warm.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread warmer = new Thread(this::warmFacetCaches, "suggest-facet-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    void warmFacetCaches() {
        for (MappingEnum field : WARM_FIELDS) {
            if (!SuggestFields.CONTEXTUAL_FIELDS.contains(field)) {
                continue;
            }
            try {
                long start = System.currentTimeMillis();
                // facet.limit=1: the cost is the uninversion, not the buckets returned.
                SolrQuery warmQuery = SolrQueryBuilder.buildDistinctValuesQuery(field, 1);
                solrClient.queryMappings(warmQuery);
                logger.info("Warmed the facet cache for {} in {} ms",
                        SuggestFields.facetFieldFor(field), System.currentTimeMillis() - start);
            } catch (Exception warmFailure) {
                // Not fatal, and deliberately not retried: the next real request pays the uninversion
                // it would have paid anyway.
                logger.warn("Could not warm the facet cache for {} ({}). The first filter suggestion "
                                + "on this field will be slower.",
                        SuggestFields.facetFieldFor(field), warmFailure.toString());
            }
        }
    }
}
