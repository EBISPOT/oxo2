package uk.ac.ebi.spot.oxo.sssom2json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixDivergenceDetectorTest {

    @Test
    void flagsAPrefixTwoSetsExpandDifferently() {
        Map<String, Map<String, String>> perSet = Map.of(
                "setA", Map.of("MESH", "http://id.nlm.nih.gov/mesh/", "HP", "http://purl.obolibrary.org/obo/HP_"),
                "setB", Map.of("MESH", "http://identifiers.org/mesh/", "HP", "http://purl.obolibrary.org/obo/HP_"));

        SortedMap<String, SortedMap<String, List<String>>> divergent =
                PrefixDivergenceDetector.findDivergentPrefixes(perSet);

        assertTrue(divergent.containsKey("MESH"), "MESH diverges across the two sets");
        assertEquals(2, divergent.get("MESH").size());
        assertFalse(divergent.containsKey("HP"), "HP agrees, so must not be flagged");
    }

    @Test
    void curieMapStringSplitsOnFirstColonSoStemsKeepTheirColons() {
        Map<String, String> parsed =
                PrefixDivergenceDetector.parseCurieMap("MESH:http://id.nlm.nih.gov/mesh/, HP:http://purl.obolibrary.org/obo/HP_");

        assertEquals("http://id.nlm.nih.gov/mesh/", parsed.get("MESH"));
        assertEquals("http://purl.obolibrary.org/obo/HP_", parsed.get("HP"));
    }

    @Test
    void doubleExpandedOboStemIsRecognisedAsJunk() {
        assertTrue(PrefixDivergenceDetector.isJunkStem("http://purl.obolibrary.org/obo/http://purl.obolibrary.org/obo/BFO_"));
        assertTrue(PrefixDivergenceDetector.isJunkStem("http://purl.obolibrary.org/obo/mondo/mappings/unknown_prefix/ICD9/"));
        assertFalse(PrefixDivergenceDetector.isJunkStem("http://identifiers.org/mesh/"));
    }
}
