package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.ReleaseResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Which OxO2 software release this backend is (ADR-0052).
 *
 * <p>The answer is the {@code oxo2-release.json} bundled on the classpath: the release workflow
 * writes the release manifest there before it builds the versioned images, so a released image
 * knows its own version, DOI and history with no call to GitHub or Zenodo at runtime. The
 * checked-in copy is the development placeholder, which reads as "Development build" in the
 * frontend. Compare {@link HealthController}: that says whether the stack is up, this says what
 * it is.
 */
@Tag(name = "Release", description = "The software release this deployment was built from.")
@RestController
@RequestMapping(path = ReleaseController.PATH, produces = {MediaType.APPLICATION_JSON_VALUE})
public class ReleaseController {

    public static final String PATH = "/api/v2/release";

    /** The bundled manifest, written by the release workflow; the placeholder in development. */
    static final String MANIFEST_RESOURCE = "oxo2-release.json";

    private final ReleaseResponse release;

    /** The injected constructor; the second one exists so tests can feed a manifest of their own. */
    @Autowired
    public ReleaseController(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(MANIFEST_RESOURCE));
    }

    ReleaseController(ObjectMapper objectMapper, Resource manifest) {
        this.release = readManifest(objectMapper, manifest);
    }

    /**
     * Read the manifest once, at startup: it is baked into the image and cannot change while the
     * process runs. A missing or malformed manifest fails startup loudly rather than serving a
     * misleading "development build" from a released image.
     */
    static ReleaseResponse readManifest(ObjectMapper objectMapper, Resource manifest) {
        try (InputStream manifestStream = manifest.getInputStream()) {
            return objectMapper.readValue(manifestStream, ReleaseResponse.class);
        } catch (IOException readFailure) {
            throw new UncheckedIOException("Cannot read the bundled release manifest " + manifest, readFailure);
        }
    }

    @Operation(
            summary = "Software release of this deployment",
            description = "The OxO2 software release this backend was built from: its version, "
                    + "commit, release notes, DOI and the release history, newest first. A "
                    + "development build answers with a null current release and an empty history. "
                    + "Mapping data releases are separate from software releases and are not "
                    + "reported here.")
    @GetMapping
    public ReleaseResponse release() {
        return release;
    }
}
