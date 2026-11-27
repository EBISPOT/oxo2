package uk.ac.ebi.spot.oxo.backend.service.export;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.BioregistryPrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixIriOverrides;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.io.Writer;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
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

    private static final int CURSOR_BATCH_ROWS = 2000;

    /** SSSOM slot columns emitted, in order. The mandatory slots plus the common, safely-stringable ones. */
    static final String[] SSSOM_COLUMNS = {
            MappingConstants.SUBJECT_ID, MappingConstants.SUBJECT_LABEL,
            MappingConstants.PREDICATE_ID, MappingConstants.PREDICATE_MODIFIER,
            MappingConstants.OBJECT_ID, MappingConstants.OBJECT_LABEL,
            MappingConstants.MAPPING_JUSTIFICATION, MappingConstants.MAPPING_SET_ID
    };

    // Low-cardinality identifier fields supplement the indexed subject/object prefixes. Never
    // facet subject_id/object_id: that would materialise every distinct exported entity in memory.
    private static final String[] CURIE_FACET_FIELDS = {
            MappingConstants.PREDICATE_ID, MappingConstants.MAPPING_JUSTIFICATION,
            MappingConstants.MAPPING_SET_ID
    };

    @Autowired
    private OxOSolrClient solrClient;

    public void stream(SolrQuery baseQuery, ExportFormat format, Writer writer) throws Exception {
        char separator = format.separator();
        writeCurieMap(baseQuery, writer);
        writer.write(String.join(String.valueOf(separator), SSSOM_COLUMNS));
        writer.write("\n");

        SolrQuery rowQuery = baseQuery.getCopy();
        rowQuery.setStart(0);
        rowQuery.setRows(CURSOR_BATCH_ROWS);
        rowQuery.setFields(SSSOM_COLUMNS);
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        while (true) {
            rowQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrClient.queryMappings(rowQuery);
            requireComplete(response);
            String nextCursorMark = response.getNextCursorMark();
            if (nextCursorMark == null) {
                throw new IOException("Export response is missing its cursor");
            }
            // Stored values are already normalised. Building Mapping beans here populates the
            // shared EntityReference/Uri caches with every identifier, defeating cursor batching.
            for (SolrDocument document : response.getResults()) {
                writer.write(formatRow(document, separator));
                writer.write("\n");
            }
            flush(writer);
            if (nextCursorMark.equals(cursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }
    }

    private static void requireComplete(QueryResponse response) throws IOException {
        Object partial = response.getResponseHeader() == null ? null
                : response.getResponseHeader().get("partialResults");
        if (partial != null && !"false".equalsIgnoreCase(partial.toString())) {
            throw new IOException("Solr returned partial export results");
        }
    }

    private static void flush(Writer writer) throws IOException {
        writer.flush();
        // HttpServletResponse.getWriter() is a PrintWriter, which swallows IOExceptions.
        // Stop requesting further Solr batches when the client disconnects.
        if (writer instanceof PrintWriter printWriter && printWriter.checkError()) {
            throw new IOException("Download connection closed while exporting");
        }
    }

    /** Discover prefixes over exactly the same query as the rows, before writing the header. */
    private void writeCurieMap(SolrQuery baseQuery, Writer writer) throws Exception {
        SolrQuery facetQuery = baseQuery.getCopy();
        facetQuery.remove(CursorMarkParams.CURSOR_MARK_PARAM);
        facetQuery.setStart(0);
        facetQuery.setRows(0);
        facetQuery.setFacet(true);
        facetQuery.setFacetMinCount(1);
        facetQuery.setFacetLimit(-1);
        facetQuery.addFacetField(MappingConstants.SUBJECT_PREFIX, MappingConstants.OBJECT_PREFIX);
        facetQuery.addFacetField(CURIE_FACET_FIELDS);

        QueryResponse response = solrClient.queryMappings(facetQuery);
        requireComplete(response);
        TreeSet<String> prefixes = new TreeSet<>();
        prefixes.addAll(facetValues(response, MappingConstants.SUBJECT_PREFIX));
        prefixes.addAll(facetValues(response, MappingConstants.OBJECT_PREFIX));
        for (String field : CURIE_FACET_FIELDS) {
            for (String value : facetValues(response, field)) {
                // Full IRIs need no prefix declaration. Labels are never inspected for colons.
                if (StringUtils.isCurie(value) && !value.regionMatches(true, 0, "urn:", 0, 4)) {
                    prefixes.add(value.substring(0, value.indexOf(':')));
                }
            }
        }

        SortedMap<String, String> namespaces = new TreeMap<>();
        Set<String> missing = new TreeSet<>();
        for (String prefix : prefixes) {
            String base = resolveBase(prefix);
            if (base == null) missing.add(prefix);
            else namespaces.put(prefix, base);
        }
        if (!missing.isEmpty()) {
            resolveSourcePrefixes(missing, facetValues(response, MappingConstants.MAPPING_SET_ID), namespaces);
        }
        if (!namespaces.keySet().containsAll(prefixes)) {
            missing.removeAll(namespaces.keySet());
            throw new IOException("Cannot resolve exported prefixes: " + String.join(", ", missing));
        }
        writer.write(namespaces.isEmpty() ? "# curie_map: {}\n" : "# curie_map:\n");
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            writer.write("#   " + yamlString(entry.getKey()) + ": " + yamlString(entry.getValue()) + "\n");
        }
        writer.write("# mapping_set_id: https://www.ebi.ac.uk/oxo2/export\n");
    }

    private static List<String> facetValues(QueryResponse response, String field) {
        FacetField facet = response.getFacetField(field);
        if (facet == null || facet.getValues() == null) return List.of();
        return facet.getValues().stream().filter(value -> value.getCount() > 0)
                .map(FacetField.Count::getName).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static String resolveBase(String prefix) {
        String canonical = PrefixIriOverrides.canonicalStem(prefix);
        if (canonical != null) return canonical;
        String lower = prefix.toLowerCase(Locale.ROOT);
        Map<String, String> predefined = PrefixMap.getPrefixMap();
        if (predefined.containsKey(lower)) return predefined.get(lower);
        if (predefined.containsKey(prefix)) return predefined.get(prefix);
        SortedMap<String, String> bioregistry = BioregistryPrefixMap.get();
        if (bioregistry.containsKey(prefix)) return bioregistry.get(prefix);
        if (bioregistry.containsKey(lower)) return bioregistry.get(lower);
        return bioregistry.get(prefix.toUpperCase(Locale.ROOT));
    }

    /** Custom prefixes need their source declaration. Read only contributing sets, in small batches.
     *  Do not construct CurieMap: its constructor mutates the process-wide PrefixMap. */
    private void resolveSourcePrefixes(Set<String> missing, List<String> setIds,
                                       Map<String, String> namespaces) throws Exception {
        for (int start = 0; start < setIds.size(); start += 50) {
            List<String> batch = setIds.subList(start, Math.min(start + 50, setIds.size()));
            List<String> clauses = new ArrayList<>();
            for (String id : batch) clauses.add("mapping_set_id:" + ClientUtils.escapeQueryChars(id));
            SolrQuery query = new SolrQuery(String.join(" OR ", clauses));
            query.setRows(batch.size());
            query.setFields("curie_map");
            QueryResponse response = solrClient.queryMappingSets(query);
            requireComplete(response);
            for (SolrDocument document : response.getResults()) {
                Object raw = document.getFieldValue("curie_map");
                if (raw == null) continue;
                // This is the stored CurieMap representation (prefix:IRI, prefix:IRI).
                for (String pair : raw.toString().split(", ")) {
                    int colon = pair.indexOf(':');
                    if (colon < 1) continue;
                    String declared = pair.substring(0, colon);
                    String base = pair.substring(colon + 1);
                    if (base.isBlank()) continue;
                    for (String prefix : missing) {
                        if (!declared.equalsIgnoreCase(prefix)) continue;
                        String previous = namespaces.putIfAbsent(prefix, base);
                        if (previous != null && !previous.equals(base)) {
                            throw new IOException("Conflicting namespaces for exported prefix: " + prefix);
                        }
                    }
                }
            }
        }
    }

    private static String yamlString(String value) {
        return "'" + value.replace("'", "''").replace("\r", "").replace("\n", "") + "'";
    }

    /** Values from Solr already have their normalised SSSOM representation. */
    static String formatRow(SolrDocument document, char separator) {
        StringBuilder row = new StringBuilder();
        for (int index = 0; index < SSSOM_COLUMNS.length; index++) {
            if (index > 0) row.append(separator);
            Object value = document.getFieldValue(SSSOM_COLUMNS[index]);
            row.append(escape(value == null ? "" : value.toString(), separator));
        }
        return row.toString();
    }

    /**
     * Characters that make a spreadsheet (Excel, LibreOffice, Sheets) treat a cell as a formula or DDE
     * command when it opens the exported file — the CSV-injection vector. A leading occurrence of any of
     * these is neutralised in {@link #neutraliseFormula}. The tab and carriage-return triggers are the
     * control-character forms of the same attack.
     */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    /** TSV strips embedded tabs/newlines to spaces; CSV quotes values containing the separator/quote/newline. */
    public static String escape(String value, char separator) {
        if (value == null) {
            return "";
        }
        String neutralised = neutraliseFormula(value);
        if (separator == ',') {
            if (neutralised.contains(",") || neutralised.contains("\"")
                    || neutralised.contains("\n") || neutralised.contains("\r")) {
                return "\"" + neutralised.replace("\"", "\"\"") + "\"";
            }
            return neutralised;
        }
        return neutralised.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * Defuse CSV/DDE formula injection: if a value begins with a formula-trigger character, prefix it
     * with a single quote so a spreadsheet renders it as literal text instead of evaluating it. None of
     * the exported SSSOM slots or v1 columns is a signed number, so this never corrupts a legitimate
     * value — CURIEs/IRIs start with a letter, distances are small positives, and only free-text labels
     * could plausibly lead with a trigger, where inertness is the safe outcome.
     */
    private static String neutraliseFormula(String value) {
        if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
