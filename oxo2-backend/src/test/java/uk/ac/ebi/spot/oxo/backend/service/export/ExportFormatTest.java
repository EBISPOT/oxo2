package uk.ac.ebi.spot.oxo.backend.service.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFormatTest {

    @Test
    void absentOrUnknownDefaultsToJson() {
        assertThat(ExportFormat.fromParam(null)).isEqualTo(ExportFormat.JSON);
        assertThat(ExportFormat.fromParam("")).isEqualTo(ExportFormat.JSON);
        assertThat(ExportFormat.fromParam("  ")).isEqualTo(ExportFormat.JSON);
        assertThat(ExportFormat.fromParam("xml")).isEqualTo(ExportFormat.JSON);
    }

    @Test
    void parsesKnownFormatsCaseInsensitively() {
        assertThat(ExportFormat.fromParam("sssom-tsv")).isEqualTo(ExportFormat.SSSOM_TSV);
        assertThat(ExportFormat.fromParam("SSSOM")).isEqualTo(ExportFormat.SSSOM_TSV);
        assertThat(ExportFormat.fromParam("tsv")).isEqualTo(ExportFormat.TSV);
        assertThat(ExportFormat.fromParam("CSV")).isEqualTo(ExportFormat.CSV);
    }

    @Test
    void jsonIsNotStreamedOthersAre() {
        assertThat(ExportFormat.JSON.isStreamed()).isFalse();
        assertThat(ExportFormat.SSSOM_TSV.isStreamed()).isTrue();
        assertThat(ExportFormat.TSV.isStreamed()).isTrue();
        assertThat(ExportFormat.CSV.isStreamed()).isTrue();
    }

    @Test
    void separatorsContentTypesAndExtensions() {
        assertThat(ExportFormat.SSSOM_TSV.separator()).isEqualTo('\t');
        assertThat(ExportFormat.CSV.separator()).isEqualTo(',');
        assertThat(ExportFormat.SSSOM_TSV.contentType()).isEqualTo("text/tab-separated-values");
        assertThat(ExportFormat.CSV.contentType()).isEqualTo("text/csv");
        assertThat(ExportFormat.SSSOM_TSV.fileExtension()).isEqualTo("tsv");
        assertThat(ExportFormat.CSV.fileExtension()).isEqualTo("csv");
    }
}
