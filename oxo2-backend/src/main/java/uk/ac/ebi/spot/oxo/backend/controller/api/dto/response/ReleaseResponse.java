package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import java.util.List;

/**
 * The software release this backend was built from (ADR-0052), read from the
 * {@code oxo2-release.json} the release workflow bundles into the image before building it.
 *
 * <p>A development or local build carries the checked-in placeholder: {@code current} and
 * {@code conceptDoi} are {@code null} and {@code releases} is empty. A released image carries the
 * release it belongs to as {@code current}, the Zenodo concept DOI of the whole series, and the
 * history of releases up to and including it, newest first.
 *
 * @param current    the release this build is, or {@code null} for a development build
 * @param conceptDoi the Zenodo DOI identifying every archived version; {@code null} when none exists
 * @param releases   release history, newest first; empty for a development build
 */
public record ReleaseResponse(
        ReleaseEntry current,
        String conceptDoi,
        List<ReleaseEntry> releases) {

    /**
     * One software release.
     *
     * @param version    {@code MAJOR.MINOR.PATCH}, without the {@code v} the git tag carries
     * @param commit     the released commit SHA on {@code stable}
     * @param releasedAt publication time of the GitHub release, ISO-8601 UTC
     * @param notes      the release notes, Markdown
     * @param doi        this version's Zenodo DOI, or {@code null} when archiving was off
     * @param releaseUrl the GitHub release page
     */
    public record ReleaseEntry(
            String version,
            String commit,
            String releasedAt,
            String notes,
            String doi,
            String releaseUrl) {
    }
}
