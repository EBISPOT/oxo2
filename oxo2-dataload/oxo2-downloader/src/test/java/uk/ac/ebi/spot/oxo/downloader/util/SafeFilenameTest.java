package uk.ac.ebi.spot.oxo.downloader.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFilenameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "mappings.tsv",
            "a-b_c.1.tsv",
            "X.json",
            "abc",
            "a",
            "1",
            "a.b.c.d.tsv",
            "snomed-to-hpo.tsv",
            "mapping_set_42.yml"
    })
    void acceptsSafeNames(String name) {
        assertTrue(SafeFilename.isSafe(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x\"; touch /pwn; #.tsv",
            "a$(id).tsv",
            "a`id`.tsv",
            "a;b.tsv",
            "a|b.tsv",
            "a\nb.tsv",
            "a b.tsv",
            "a&b.tsv",
            "a>b.tsv",
            "a'b.tsv",
            "$IFS.tsv",
            "a\\b.tsv"
    })
    void rejectsShellMetacharacters(String name) {
        assertFalse(SafeFilename.isSafe(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "/etc/passwd",
            "a/b.tsv",
            "..",
            ".",
            "./a.tsv"
    })
    void rejectsPathTraversal(String name) {
        assertFalse(SafeFilename.isSafe(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".hidden",
            ".env",
            "-rf",
            "--config"
    })
    void rejectsDotfilesAndFlagLikeNames(String name) {
        assertFalse(SafeFilename.isSafe(name), name);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void rejectsNullAndEmpty(String name) {
        assertFalse(SafeFilename.isSafe(name));
    }

    @Test
    void acceptsNameAt255Bytes() {
        String name = "a".repeat(255);
        assertTrue(SafeFilename.isSafe(name));
    }

    @Test
    void rejectsNameOver255Bytes() {
        String name = "a".repeat(256);
        assertFalse(SafeFilename.isSafe(name));
    }
}
