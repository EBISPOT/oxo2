package uk.ac.ebi.spot.oxo.backend.service.export;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import static org.assertj.core.api.Assertions.assertThat;

class MappingTsvExporterTest {

    @Test
    void formatRowEmitsSssomColumnsInOrder() {
        Mapping mapping = Mapping.builder()
                .mappingSetId("https://example.org/set")
                .subjectId("DOID:9352")
                .subjectLabel("diabetes")
                .predicateId("skos:exactMatch")
                .objectId("EFO:0000400")
                .objectLabel("diabetes mellitus")
                .mappingJustification("semapv:LexicalMatching")
                .build();

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
