package uk.ac.ebi.spot.oxo.entities;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shard name must be a safe filename that {@link FilenameGuard} accepts, and the mapping from
 * prefix to name must be injective — two prefixes sharing a name would make one shard's
 * {@code publishDir} clobber the other's, silently dropping an ontology from the typeahead.
 */
class ShardNameTest {

    /** FilenameGuard's own rule, as the oracle — every shard name must satisfy it. */
    private static boolean isSafeFilename(String name) {
        return name.matches("[A-Za-z0-9._-]+")
                && !name.startsWith(".")
                && !name.startsWith("-")
                && name.length() <= 255;
    }

    @Test
    void safePrefixesAreUsedVerbatim() {
        for (String prefix : List.of("MONDO", "DOID", "EX", "__none__", "a.b", "a_b", "a-b", "CL")) {
            assertEquals(prefix, ShardName.of(prefix),
                    "a filesystem-safe prefix should name its shard verbatim, for readable files "
                            + "and a stable -resume cache");
        }
    }

    @Test
    void unsafePrefixesBecomeSafeFilenames() {
        // The two real culprits from the OLS-scale load: a ':' with no "//", so isCurie accepts them.
        for (String prefix : List.of("SRAO_/SRAO", "CPONT_/VOCAB/CPONT")) {
            String name = ShardName.of(prefix);
            assertTrue(isSafeFilename(name),
                    "ShardName.of(" + prefix + ") = " + name + " must be a safe filename");
            assertNotEquals(prefix, name, "an unsafe prefix must not be used verbatim");
        }
    }

    @Test
    void encodingRoundTripsToTheOriginalPrefix() {
        // enc-<hex> carries the prefix losslessly, so distinct prefixes cannot collide by truncation.
        String prefix = "SRAO_/SRAO";
        String name = ShardName.of(prefix);
        assertTrue(name.startsWith("enc-"));
        assertEquals(prefix, decode(name));
    }

    @Test
    void aPrefixShapedLikeAnEncodedNameCannotCollideWithARealEncoding() {
        // The unsafe prefix "A/B" (bytes 0x41 0x2f 0x42) encodes to exactly "enc-412f42".
        String encodedOfSlash = ShardName.of("A/B");
        assertEquals("enc-412f42", encodedOfSlash);

        // If the equally-valid prefix "enc-412f42" were used verbatim it would collide with the name
        // minted for "A/B". Excluding the encoded shape from the verbatim branch is what prevents it:
        // the lookalike is itself encoded, to a different name.
        String lookalike = "enc-412f42";
        String encodedOfLookalike = ShardName.of(lookalike);
        assertNotEquals(lookalike, encodedOfLookalike, "a lookalike must not be used verbatim");
        assertNotEquals(encodedOfSlash, encodedOfLookalike, "the two must not collide");
    }

    @Test
    void distinctPrefixesGetDistinctNames() {
        List<String> prefixes = List.of(
                "MONDO", "DOID", "SRAO_/SRAO", "CPONT_/VOCAB/CPONT", "__none__",
                "A/B", "enc-412f42", "AB", "a\\b", "café/x", "-lead", ".lead");
        Map<String, String> nameToPrefix = new HashMap<>();
        for (String prefix : prefixes) {
            String name = ShardName.of(prefix);
            assertTrue(isSafeFilename(name),
                    "ShardName.of(" + prefix + ") = " + name + " must be a safe filename");
            String clash = nameToPrefix.put(name, prefix);
            assertEquals(null, clash,
                    "prefixes " + clash + " and " + prefix + " both mapped to shard name " + name);
        }
    }

    @Test
    void leadingDotOrDashPrefixesAreEncoded() {
        // FilenameGuard rejects a leading '.' or '-', so these cannot be used verbatim even though
        // every character is in the safe alphabet.
        assertFalse(ShardName.of("-lead").startsWith("-"));
        assertTrue(isSafeFilename(ShardName.of("-lead")));
        assertTrue(isSafeFilename(ShardName.of(".lead")));
    }

    private static String decode(String name) {
        String hex = name.substring("enc-".length());
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
