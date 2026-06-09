package uk.ac.ebi.spot.oxo.model.sssom;

import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioregistryPrefixMapTest {

    @Test
    void loadsPrefixesFromBundledSnapshot() {
        SortedMap<String, String> prefixes = BioregistryPrefixMap.get();

        // Snapshot ships ~2200 prefixes; guard well below that so a refresh doesn't break the test.
        assertTrue(prefixes.size() > 1000,
                "expected a populated Bioregistry prefix map, got " + prefixes.size());
        // The prefixes the biopragmatics SeMRA priority views rely on must resolve.
        assertTrue(prefixes.containsKey("umls"), "missing umls prefix");
        assertTrue(prefixes.containsKey("mesh"), "missing mesh prefix");
        assertEquals("http://purl.obolibrary.org/obo/DOID_", prefixes.get("doid"));
    }

    @Test
    void curiesExpandAgainstTheBundledMap() {
        CurieMap curieMap = new CurieMap(CurieMap.convertMapToString(BioregistryPrefixMap.get()));

        assertEquals("https://meshb.nlm.nih.gov/record/ui?ui=D000050",
                new EntityReference("mesh:D000050").toUri(curieMap).orElseThrow().getDataAsString());
    }
}
