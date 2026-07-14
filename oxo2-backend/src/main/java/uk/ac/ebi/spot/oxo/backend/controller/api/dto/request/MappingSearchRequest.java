package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.FilterMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.LabelMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;

import java.util.List;

@Schema(description = "Mapping-search request: free-text queries, filters, sorting and paging.")
public class MappingSearchRequest {

    @Schema(description = "Search terms, OR-ed together. A mapping search is asked from the "
            + "subject's perspective, so by default each term matches the subject side only: an IRI "
            + "→ `subject_iri`, a CURIE → `subject_id` (prefix normalised), anything else → the "
            + "subject label field selected by `labelMatch`.",
            example = "[\"diabetes\"]")
    private List<String> queries;

    @Schema(description = "Fields the free-text `queries` are matched against, overriding the "
            + "default subject-side matching.")
    private List<MappingEnum> queryFields;
    @Schema(description = "Fields to return for each mapping. Defaults to the minimal result set when omitted.")
    private List<MappingEnum> fieldList;

    @Schema(description = "Sort order, applied in list order (later entries break ties).")
    private List<SortedField> sortedFields;

    @Schema(description = "Reserved: maximum inference chain length to consider.")
    private int distance;

    @Schema(description = "Zero-based page index.", defaultValue = "1")
    private int page = 1;
    @Schema(description = "Page size (1–100).", defaultValue = "10")
    private int size = 10;

    @Schema(description = "Per-column \"contains\" filters. A non-blank filter on any predicate field "
            + "(`predicate_id`, `predicate_label`, `predicate_iri`, `predicate_modifier`) lifts the "
            + "default hiding of `rdfs:subClassOf` and `oboInOwl:hasDbXref`.")
    private List<ColumnFilter> columnFilters;

    @Schema(description = "Restrict results to these mapping-set ids (full IRIs).")
    private List<String> mappingSetIds;

    @Schema(description = "Advanced per-field queries (field id + value). Like column filters, a "
            + "non-blank predicate-field query lifts the default predicate hiding.")
    private List<FieldQuery> advancedFieldQueries;

    // Multi-select inference-type filter (ADR-0011): null/empty = all types; otherwise restrict to
    // the listed types (ASSERTED / SSSOM_INFERENCE). Replaces the old tri-state
    // boolean `inferred`.
    @Schema(description = "Restrict to these inference types; null/empty returns all types.")
    private List<InferenceType> inferenceType;

    // Which asserted corpora to search (ADR-0027): null/empty = all. Named for the Solr field, not
    // "source", because mapping_set_source is already an SSSOM slot meaning something else.
    // Orthogonal to inferenceType: an inferred mapping chains premises from several sets and carries
    // no category, so it is never removed by this filter — use inferenceType to exclude inferences.
    @Schema(description = "Restrict asserted mappings to these corpora (ONTOLOGY = an ontology's own "
            + "cross-references, CURATED = a curated SSSOM file); null/empty searches both. Inferred "
            + "mappings carry no category and always pass this filter — exclude them via inferenceType.")
    private List<MappingSetCategory> mappingSetCategory;

    // Cross-ontology mapping (ADR-0024): restrict subjects/objects to these ontologies (CURIE
    // prefixes). Each list becomes an OR'd exact-term filter on subject_prefix / object_prefix
    // (subject = source, object = target). The GET /api/v2/mappings?from=&to= view and the frontend
    // from/to selectors set these; null/empty means unrestricted on that side.
    @Schema(description = "Restrict subjects to these ontologies (CURIE prefixes), e.g. [\"DOID\"].")
    private List<String> subjectPrefixes;
    @Schema(description = "Restrict objects to these ontologies (CURIE prefixes), e.g. [\"EFO\",\"MONDO\"].")
    private List<String> objectPrefixes;

    // Collapse same-SPO mappings into one row via Solr result grouping (ADR-0013). Default false;
    // the normal/inferences result tables opt in, the Advanced tab stays flat.
    @Schema(description = "Collapse mappings that share the same subject/predicate/object into one "
            + "representative row.", defaultValue = "false")
    private boolean groupBySpo = false;

    // How free-text (label) queries are matched in the classified/normal path (ADR-0026). Only
    // affects terms that are neither an IRI nor a CURIE; IRI/CURIE terms remain exact subject_iri /
    // subject_id lookups regardless (ADR-0030). Defaults to case-insensitive exact match when omitted.
    @Schema(description = "How free-text label queries are matched against the subject label: "
            + "PARTIAL (analyzed subsequence), EXACT_CASE_INSENSITIVE (whole label, case-folded), or "
            + "EXACT_CASE_SENSITIVE (whole label, case-sensitive). Ignored for IRI/CURIE terms.",
            defaultValue = "EXACT_CASE_INSENSITIVE")
    private LabelMatchType labelMatch = LabelMatchType.DEFAULT;


    /**
     * A shallow copy. Used by the contextual value suggest (ADR-0034), which needs to build the live
     * search's query with one column filter removed — and must not mutate the caller's request to do
     * it, or it would change the very results the user is looking at.
     *
     * <p>Shallow is enough because the caller replaces whole list references rather than mutating
     * their contents.
     */
    public MappingSearchRequest copy() {
        MappingSearchRequest copy = new MappingSearchRequest();
        copy.queries = this.queries;
        copy.queryFields = this.queryFields;
        copy.fieldList = this.fieldList;
        copy.sortedFields = this.sortedFields;
        copy.distance = this.distance;
        copy.page = this.page;
        copy.size = this.size;
        copy.columnFilters = this.columnFilters;
        copy.mappingSetIds = this.mappingSetIds;
        copy.advancedFieldQueries = this.advancedFieldQueries;
        copy.inferenceType = this.inferenceType;
        copy.mappingSetCategory = this.mappingSetCategory;
        copy.subjectPrefixes = this.subjectPrefixes;
        copy.objectPrefixes = this.objectPrefixes;
        copy.groupBySpo = this.groupBySpo;
        copy.labelMatch = this.labelMatch;
        return copy;
    }

    public List<String> getQueries() {
        return queries;
    }

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public List<MappingEnum> getQueryFields() {
        return queryFields;
    }

    public void setQueryFields(List<MappingEnum> queryFields) {
        this.queryFields = queryFields;
    }

    public List<MappingEnum> getFieldList() {
        return fieldList;
    }

    public void setFieldList(List<MappingEnum> fieldList) {
        this.fieldList = fieldList;
    }

    public List<SortedField> getSortedFields() {
        return sortedFields;
    }

    public void setSortedFields(List<SortedField> sortedFields) {
        this.sortedFields = sortedFields;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<ColumnFilter> getColumnFilters() {
        return columnFilters;
    }

    public void setColumnFilters(List<ColumnFilter> columnFilters) {
        this.columnFilters = columnFilters;
    }

    public List<String> getMappingSetIds() {
        return mappingSetIds;
    }

    public void setMappingSetIds(List<String> mappingSetIds) {
        this.mappingSetIds = mappingSetIds;
    }

    public List<FieldQuery> getAdvancedFieldQueries() {
        return advancedFieldQueries;
    }

    public void setAdvancedFieldQueries(List<FieldQuery> advancedFieldQueries) {
        this.advancedFieldQueries = advancedFieldQueries;
    }

    public List<InferenceType> getInferenceType() {
        return inferenceType;
    }

    public void setInferenceType(List<InferenceType> inferenceType) {
        this.inferenceType = inferenceType;
    }

    public List<MappingSetCategory> getMappingSetCategory() {
        return mappingSetCategory;
    }

    public void setMappingSetCategory(List<MappingSetCategory> mappingSetCategory) {
        this.mappingSetCategory = mappingSetCategory;
    }

    public List<String> getSubjectPrefixes() {
        return subjectPrefixes;
    }

    public void setSubjectPrefixes(List<String> subjectPrefixes) {
        this.subjectPrefixes = subjectPrefixes;
    }

    public List<String> getObjectPrefixes() {
        return objectPrefixes;
    }

    public void setObjectPrefixes(List<String> objectPrefixes) {
        this.objectPrefixes = objectPrefixes;
    }

    public boolean isGroupBySpo() {
        return groupBySpo;
    }

    public void setGroupBySpo(boolean groupBySpo) {
        this.groupBySpo = groupBySpo;
    }

    public LabelMatchType getLabelMatch() {
        return labelMatch;
    }

    public void setLabelMatch(LabelMatchType labelMatch) {
        // Null-tolerant: an explicit JSON null falls back to the default rather than NPE-ing the
        // classified path.
        this.labelMatch = labelMatch == null ? LabelMatchType.DEFAULT : labelMatch;
    }

    @Override
    public String toString() {
        return "MappingSearchRequest{" +
                "columnFilters=" + columnFilters +
                ", queries=" + queries +
                ", queryFields=" + queryFields +
                ", fieldList=" + fieldList +
                ", sortedFields=" + sortedFields +
                ", distance=" + distance +
                ", page=" + page +
                ", size=" + size +
                ", mappingSetIds=" + mappingSetIds +
                ", advancedFieldQueries=" + advancedFieldQueries +
                ", inferenceType=" + inferenceType +
                ", mappingSetCategory=" + mappingSetCategory +
                ", subjectPrefixes=" + subjectPrefixes +
                ", objectPrefixes=" + objectPrefixes +
                ", groupBySpo=" + groupBySpo +
                ", labelMatch=" + labelMatch +
                '}';
    }

    @Schema(description = "A filter on a single column. Substring (\"contains\") by default; EXACT "
            + "when the value was picked from a suggestion rather than typed (ADR-0034).")
    public static class ColumnFilter {
        @Schema(description = "Solr field id to filter on.", example = "predicate_id")
        private String id;
        @Schema(description = "Value the column must contain (or equal, when match is EXACT).",
                example = "skos:exactMatch")
        private String value;

        /**
         * Defaults to CONTAINS, so every existing caller keeps the behaviour it had before
         * autocomplete existed. Only a client that knows the value came out of a suggestion list
         * sets EXACT.
         */
        @Schema(description = "How the value matches. CONTAINS (default) is a substring match, for a "
                + "value the user typed. EXACT is a whole-value match, for one they PICKED from a "
                + "suggestion — that value came out of the index, so matching it as a substring would "
                + "silently also return values they did not pick.",
                defaultValue = "CONTAINS")
        private FilterMatchType match = FilterMatchType.DEFAULT;

        /**
         * The creator is annotated EXPLICITLY, not left implicit. With a single constructor Jackson
         * infers a properties-based creator from the {@code -parameters} names; adding a second
         * constructor makes that inference ambiguous and deserialization of EVERY column filter fails
         * with a type-definition error — including ones that never mention {@code match}. Naming the
         * creator and its properties removes the ambiguity.
         */
        @JsonCreator
        public ColumnFilter(@JsonProperty("id") String id,
                            @JsonProperty("value") String value,
                            @JsonProperty("match") FilterMatchType match) {
            this.id = id;
            this.value = value;
            this.match = match == null ? FilterMatchType.DEFAULT : match;
        }

        /** Convenience for callers (and tests) that mean the default CONTAINS behaviour. */
        public ColumnFilter(String id, String value) {
            this(id, value, FilterMatchType.DEFAULT);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public FilterMatchType getMatch() {
            return match == null ? FilterMatchType.DEFAULT : match;
        }

        public void setMatch(FilterMatchType match) {
            this.match = match == null ? FilterMatchType.DEFAULT : match;
        }

        @Override
        public String toString() {
            return "ColumnFilter{" +
                    "id='" + id + '\'' +
                    ", value='" + value + '\'' +
                    ", match=" + match +
                    '}';
        }
    }
}
