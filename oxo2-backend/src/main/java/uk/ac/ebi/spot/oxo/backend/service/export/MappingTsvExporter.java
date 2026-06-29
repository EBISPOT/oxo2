package uk.ac.ebi.spot.oxo.backend.service.export;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CursorMarkParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.BioregistryPrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;
import uk.ac.ebi.spot.oxo.model.sssom.PredicateModifierEnum;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.SSSOMDataType;

import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * Streams a mapping result set as an SSSOM-compliant delimited table (ADR-0024): a YAML metadata
 * header (the {@code curie_map} for the prefixes present) followed by the SSSOM slot columns. The
 * full result is read with Solr cursorMark deep paging so the whole export never sits in memory; the
 * caller supplies a base query already carrying the q + filter queries and a {@code id asc} sort
 * (the unique-key total order cursorMark needs), with no paging or same-SPO collapse.
 */
@Service
public class MappingTsvExporter {

    private static final Logger logger = LoggerFactory.getLogger(MappingTsvExporter.class);

    private static final int CURSOR_BATCH_ROWS = 2000;

    /** SSSOM slot columns emitted, in order. The mandatory slots plus the common, safely-stringable ones. */
    static final String[] SSSOM_COLUMNS = {
            MappingConstants.SUBJECT_ID, MappingConstants.SUBJECT_LABEL,
            MappingConstants.PREDICATE_ID, MappingConstants.PREDICATE_MODIFIER,
            MappingConstants.OBJECT_ID, MappingConstants.OBJECT_LABEL,
            MappingConstants.MAPPING_JUSTIFICATION, MappingConstants.MAPPING_SET_ID
    };

    /**
     * Predicate / justification prefixes always added to the curie_map (uppercased to match the stored
     * CURIEs, whose prefixes EntityReference upper-cases), so predicates like {@code SKOS:exactMatch}
     * and justifications like {@code SEMAPV:LexicalMatching} expand.
     */
    private static final Set<String> COMMON_PREDICATE_PREFIXES =
            Set.of("SKOS", "OWL", "RDFS", "RDF", "SEMAPV", "SSSOM", "OBO");

    @Autowired
    private OxOSolrClient solrClient;

    public void stream(SolrQuery baseQuery, ExportFormat format, Writer writer) throws Exception {
        char separator = format.separator();
        writeCurieMap(baseQuery, writer);
        writer.write(String.join(String.valueOf(separator), SSSOM_COLUMNS));
        writer.write("\n");

        baseQuery.setRows(CURSOR_BATCH_ROWS);
        baseQuery.setFields(SSSOM_COLUMNS);
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        while (true) {
            baseQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrClient.queryMappings(baseQuery);
            List<Mapping.Builder> beans = response.getBeans(Mapping.Builder.class);
            for (Mapping.Builder builder : beans) {
                writer.write(formatRow(builder.build(), separator));
                writer.write("\n");
            }
            String nextCursorMark = response.getNextCursorMark();
            if (nextCursorMark == null || nextCursorMark.equals(cursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
            writer.flush();
        }
        writer.flush();
    }

    /**
     * Write the SSSOM {@code curie_map} metadata block (comment lines) for the prefixes actually
     * present in the export's filter domain, discovered with a single rows=0 facet pass so the header
     * can be written before the rows are streamed.
     */
    private void writeCurieMap(SolrQuery baseQuery, Writer writer) throws Exception {
        SolrQuery facetQuery = new SolrQuery("*:*");
        facetQuery.setRows(0);
        String[] filterQueries = baseQuery.getFilterQueries();
        if (filterQueries != null) {
            for (String filterQuery : filterQueries) {
                facetQuery.addFilterQuery(filterQuery);
            }
        }
        facetQuery.setFacet(true);
        facetQuery.setFacetMinCount(1);
        facetQuery.setFacetLimit(-1);
        facetQuery.addFacetField(MappingConstants.SUBJECT_PREFIX, MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(facetQuery);
        TreeSet<String> prefixes = new TreeSet<>(COMMON_PREDICATE_PREFIXES);
        addFacetPrefixes(response.getFacetField(MappingConstants.SUBJECT_PREFIX), prefixes);
        addFacetPrefixes(response.getFacetField(MappingConstants.OBJECT_PREFIX), prefixes);

        SortedMap<String, String> bioregistry = BioregistryPrefixMap.get();
        Map<String, String> predefined = PrefixMap.getPrefixMap();
        writer.write("# curie_map:\n");
        for (String prefix : prefixes) {
            String base = resolveBase(prefix, bioregistry, predefined);
            if (base != null) {
                writer.write("#   " + prefix + ": " + base + "\n");
            }
        }
        writer.write("# mapping_set_id: https://www.ebi.ac.uk/oxo2/export\n");
    }

    private static void addFacetPrefixes(FacetField facetField, Set<String> prefixes) {
        if (facetField == null) {
            return;
        }
        for (FacetField.Count value : facetField.getValues()) {
            if (value.getName() != null && !value.getName().isBlank()) {
                prefixes.add(value.getName());
            }
        }
    }

    /** Resolve a prefix to its namespace IRI, tolerant of the case mismatch between stored (upper) CURIEs and registry (lower) keys. */
    private static String resolveBase(String prefix, SortedMap<String, String> bioregistry,
                                      Map<String, String> predefined) {
        String lower = prefix.toLowerCase();
        if (predefined.containsKey(lower)) {
            return predefined.get(lower);
        }
        if (bioregistry.containsKey(prefix)) {
            return bioregistry.get(prefix);
        }
        if (bioregistry.containsKey(lower)) {
            return bioregistry.get(lower);
        }
        String upper = prefix.toUpperCase();
        return bioregistry.get(upper);
    }

    /** Format one mapping as a delimited row over {@link #SSSOM_COLUMNS}. Package-visible for unit tests. */
    static String formatRow(Mapping mapping, char separator) {
        String[] values = {
                mapping.subjectId().map(MappingTsvExporter::repr).orElse(""),
                mapping.subjectLabel().orElse(""),
                mapping.predicateId().map(MappingTsvExporter::repr).orElse(""),
                mapping.predicateModifier().map(PredicateModifierEnum::value).orElse(""),
                mapping.objectId().map(MappingTsvExporter::repr).orElse(""),
                mapping.objectLabel().orElse(""),
                mapping.mappingJustification().map(MappingTsvExporter::repr).orElse(""),
                mapping.mappingSetId() == null ? "" : repr(mapping.mappingSetId())
        };
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(separator);
            }
            row.append(escape(values[i], separator));
        }
        return row.toString();
    }

    /** Normalised string form of an SSSOM data value — the representation serialised to Solr (so CURIE prefixes match). */
    private static String repr(SSSOMDataType<?> value) {
        if (value == null) {
            return "";
        }
        String represented = value.getDataRepresentation().map(Object::toString).orElse(null);
        if (represented != null) {
            return represented;
        }
        return value.getDataAsString() == null ? "" : value.getDataAsString();
    }

    /** TSV strips embedded tabs/newlines to spaces; CSV quotes values containing the separator/quote/newline. */
    static String escape(String value, char separator) {
        if (value == null) {
            return "";
        }
        if (separator == ',') {
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
