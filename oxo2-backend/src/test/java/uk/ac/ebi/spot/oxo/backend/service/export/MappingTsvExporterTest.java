package uk.ac.ebi.spot.oxo.backend.service.export;

import org.junit.jupiter.api.Test;
import org.apache.solr.common.SolrDocument;

import static org.assertj.core.api.Assertions.assertThat;

class MappingTsvExporterTest {

    @Test
    void formatRowEmitsSssomColumnsInOrder() {
        SolrDocument mapping = new SolrDocument();
        mapping.setField("mapping_set_id", "https://example.org/set");
        mapping.setField("subject_id", "DOID:9352");
        mapping.setField("subject_label", "diabetes");
        mapping.setField("predicate_id", "SKOS:exactMatch");
        mapping.setField("object_id", "EFO:0000400");
        mapping.setField("object_label", "diabetes mellitus");
        mapping.setField("mapping_justification", "SEMAPV:LexicalMatching");

        String row = MappingTsvExporter.formatRow(mapping, '\t');

        // EntityReference upper-cases CURIE prefixes; predicate_modifier is empty (unset).
        assertThat(row).isEqualTo(String.join("\t",
                "DOID:9352", "diabetes", "SKOS:exactMatch", "",
                "EFO:0000400", "diabetes mellitus", "SEMAPV:LexicalMatching",
                "https://example.org/set"));
    }

    @Test
    void tsvEscapeStripsTabsAndNewlines() {
        assertThat(MappingTsvExporter.escape("a\tb\nc\rd", '\t')).isEqualTo("a b c d");
    }

    @Test
    void literalSubjectsAndMissingFieldsKeepTheirColumnPositions() {
        SolrDocument mapping = new SolrDocument();
        mapping.setField("subject_label", "HP:123 is text\twith a tab");
        mapping.setField("predicate_id", "SKOS:exactMatch");
        mapping.setField("predicate_modifier", "Not");
        mapping.setField("object_id", "https://example.org/entity");
        String row = MappingTsvExporter.formatRow(mapping, '\t');
        assertThat(row.split("\t", -1)).containsExactly("", "HP:123 is text with a tab", "SKOS:exactMatch",
                "Not", "https://example.org/entity", "", "", "");
    }

    @Test
    void streamedCsvRowsStillQuoteLabelsAndDefuseFormulaCells() {
        SolrDocument mapping = new SolrDocument();
        mapping.setField("subject_label", "=SUM(1,2)");
        mapping.setField("object_label", "a\"b\nc");
        assertThat(MappingTsvExporter.formatRow(mapping, ','))
                .isEqualTo(",\"'=SUM(1,2)\",,,,\"a\"\"b\nc\",,");
    }

    @Test
    void csvEscapeQuotesSeparatorAndQuotes() {
        assertThat(MappingTsvExporter.escape("a,b", ',')).isEqualTo("\"a,b\"");
        assertThat(MappingTsvExporter.escape("a\"b", ',')).isEqualTo("\"a\"\"b\"");
        assertThat(MappingTsvExporter.escape("plain", ',')).isEqualTo("plain");
    }

    @Test
    void neutralisesLeadingFormulaTriggers() {
        // Each formula-trigger character at the start is defanged with a leading apostrophe, in both formats.
        assertThat(MappingTsvExporter.escape("=1+2", '\t')).isEqualTo("'=1+2");
        assertThat(MappingTsvExporter.escape("+1", '\t')).isEqualTo("'+1");
        assertThat(MappingTsvExporter.escape("-1", '\t')).isEqualTo("'-1");
        assertThat(MappingTsvExporter.escape("@cmd", '\t')).isEqualTo("'@cmd");
        assertThat(MappingTsvExporter.escape("=1+2", ',')).isEqualTo("'=1+2");
    }

    @Test
    void formulaNeutralisationComposesWithCsvQuoting() {
        // Starts with '=' AND contains a comma: neutralise first, then CSV-quote the result.
        assertThat(MappingTsvExporter.escape("=SUM(1,2)", ',')).isEqualTo("\"'=SUM(1,2)\"");
    }

    @Test
    void leavesTriggerCharacterUntouchedWhenNotLeading() {
        assertThat(MappingTsvExporter.escape("a=b", ',')).isEqualTo("a=b");
        assertThat(MappingTsvExporter.escape("EFO:0000400", '\t')).isEqualTo("EFO:0000400");
    }
}
