package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Resolves a term's display label from the {@code oxo2-entities} collection, for the OxO v1
 * {@code /api/search} adapter.
 *
 * <p><b>Why the label cannot come off the mapping document.</b> A label in {@code oxo2-mappings} is a
 * property of the ROW, not of the term, and a large share of rows carry none — 56% of this corpus, and
 * whole mapping sets (the SeMRA-assembled ones, {@code atlas}, {@code ukbiobank}, {@code mondo.sssom.tsv})
 * ship without labels entirely. Reading the label off whichever row the search happened to rank first
 * therefore returns {@code null} for a term OxO2 can label perfectly well from another row — v1 clients
 * saw {@code null} for e.g. {@code HP:0005978}, which is labelled on 9 of its 12 rows. The entity
 * collection holds one harvested label per term, so it answers the question the row cannot.
 *
 * <p><b>Obsolete terms are deliberately NOT filtered out</b> (contrast
 * {@link uk.ac.ebi.spot.oxo.backend.service.helper.EntitySuggestQueryBuilder}, where ADR-0045's
 * default hides them). OxO v1 has no notion of obsolescence and returns obsolete terms like
 * {@code EFO:0000313} as ordinary results; applying the typeahead's {@code obsolete:false} default
 * here would leave exactly those terms unlabelled — the visible symptom that prompted this fix.
 * v1 itself labels them {@code obsolete_carcinoma}, which is what the entity collection stores.
 */
@Service
public class EntityLabelResolver {

    private static final Logger logger = LoggerFactory.getLogger(EntityLabelResolver.class);

    /**
     * CURIEs per {@code {!terms}} query. Well under Solr's {@code maxBooleanClauses}, and small enough
     * that one chunk stays a modest POST body — unlike the dataload's 20k prefetch, this runs per
     * request.
     */
    static final int CHUNK_SIZE = 500;

    @Autowired
    private OxOSolrClient solrClient;

    /**
     * Labels for the given CURIEs, keyed by CURIE. A term with no document, or a document with a blank
     * label, is simply absent from the map — the caller decides what to fall back to. Never throws: a
     * failed lookup degrades to "no labels resolved" rather than failing the search around it.
     */
    public Map<String, String> resolveLabels(Collection<String> curies) {
        Map<String, String> labels = new HashMap<>();
        if (curies == null || curies.isEmpty()) {
            return labels;
        }
        List<String> lookups = new ArrayList<>(distinctResolvable(curies));
        for (int i = 0; i < lookups.size(); i += CHUNK_SIZE) {
            List<String> chunk = lookups.subList(i, Math.min(i + CHUNK_SIZE, lookups.size()));
            labels.putAll(fetchChunk(chunk));
        }
        return labels;
    }

    /**
     * The {@code {!terms}} parser splits its value on commas, so a CURIE containing one cannot be
     * looked up this way — and would silently match the wrong terms if passed through. Such CURIEs are
     * dropped here and fall back to the caller's default; they are not expected in practice, since a
     * CURIE has no legitimate comma.
     */
    private static Collection<String> distinctResolvable(Collection<String> curies) {
        LinkedHashSet<String> resolvable = new LinkedHashSet<>();
        for (String curie : curies) {
            if (curie != null && !curie.isBlank() && curie.indexOf(',') < 0) {
                resolvable.add(curie);
            }
        }
        return resolvable;
    }

    private Map<String, String> fetchChunk(List<String> chunk) {
        Map<String, String> labels = new HashMap<>();
        SolrQuery query = new SolrQuery();
        query.setQuery("{!terms f=" + EntityConstants.ID + "}" + String.join(",", chunk));
        query.setFields(EntityConstants.ID, EntityConstants.LABEL);
        query.setRows(chunk.size());
        try {
            // POST: a 500-CURIE terms clause overruns the URI cap on a GET.
            QueryResponse response = solrClient.queryEntities(query, SolrRequest.METHOD.POST);
            for (SolrDocument document : response.getResults()) {
                Object id = document.getFieldValue(EntityConstants.ID);
                Object label = document.getFieldValue(EntityConstants.LABEL);
                if (id != null && label != null && !label.toString().isBlank()) {
                    labels.put(id.toString(), label.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Entity label lookup failed for {} curie(s); falling back", chunk.size(), e);
        }
        return labels;
    }
}
