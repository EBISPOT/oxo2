package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.ReleaseResponse;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The release endpoint (ADR-0052). The MockMvc test exercises what a development build serves: the
 * checked-in placeholder. The released shape cannot be checked in (the workflow writes it), so it is
 * fed to the manifest reader as bytes, in the exact layout {@code release.py} produces.
 */
@WebMvcTest(ReleaseController.class)
class ReleaseControllerTest {

    private static final String RELEASED_MANIFEST = """
            {
              "conceptDoi": "10.5281/zenodo.100",
              "current": {
                "commit": "e920779bdae870fc4d94bf858d46c14d691c4712",
                "doi": "10.5281/zenodo.201",
                "notes": "### Added\\n- Version in the footer",
                "releaseUrl": "https://github.com/EBISPOT/oxo2/releases/tag/v2.1.0",
                "releasedAt": "2026-10-23T10:00:00Z",
                "version": "2.1.0"
              },
              "releases": [
                {
                  "commit": "e920779bdae870fc4d94bf858d46c14d691c4712",
                  "doi": "10.5281/zenodo.201",
                  "notes": "### Added\\n- Version in the footer",
                  "releaseUrl": "https://github.com/EBISPOT/oxo2/releases/tag/v2.1.0",
                  "releasedAt": "2026-10-23T10:00:00Z",
                  "version": "2.1.0"
                },
                {
                  "commit": "7dd129bb0c66f82be970f1a8297deaaf0f311f41",
                  "doi": null,
                  "notes": "First numbered OxO2 software release.",
                  "releaseUrl": "https://github.com/EBISPOT/oxo2/releases/tag/v2.0.0",
                  "releasedAt": "2026-10-20T09:00:00Z",
                  "version": "2.0.0"
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void developmentBuildServesTheCheckedInPlaceholder() throws Exception {
        mockMvc.perform(get(ReleaseController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value((Object) null))
                .andExpect(jsonPath("$.conceptDoi").value((Object) null))
                .andExpect(jsonPath("$.releases").isEmpty());
    }

    @Test
    void checkedInPlaceholderIsTheDevelopmentShape() {
        ReleaseResponse placeholder = ReleaseController.readManifest(objectMapper,
                new ClassPathResource(ReleaseController.MANIFEST_RESOURCE));
        assertThat(placeholder.current()).isNull();
        assertThat(placeholder.conceptDoi()).isNull();
        assertThat(placeholder.releases()).isEmpty();
    }

    @Test
    void releasedManifestIsReadInFull() {
        ReleaseResponse released = ReleaseController.readManifest(objectMapper, resource(RELEASED_MANIFEST));

        assertThat(released.conceptDoi()).isEqualTo("10.5281/zenodo.100");
        assertThat(released.current().version()).isEqualTo("2.1.0");
        assertThat(released.current().doi()).isEqualTo("10.5281/zenodo.201");
        assertThat(released.current().releaseUrl()).endsWith("/releases/tag/v2.1.0");
        assertThat(released.current().notes()).startsWith("### Added");
        assertThat(released.releases()).extracting(ReleaseResponse.ReleaseEntry::version)
                .containsExactly("2.1.0", "2.0.0");
        // A release made with archiving off has no DOI; the field is nullable, not absent.
        assertThat(released.releases().get(1).doi()).isNull();
    }

    @Test
    void malformedManifestFailsStartupLoudly() {
        assertThatThrownBy(() -> ReleaseController.readManifest(objectMapper, resource("not json")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ReleaseController.readManifest(objectMapper,
                new ClassPathResource("no-such-manifest.json")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("no-such-manifest.json");
    }

    private static Resource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
