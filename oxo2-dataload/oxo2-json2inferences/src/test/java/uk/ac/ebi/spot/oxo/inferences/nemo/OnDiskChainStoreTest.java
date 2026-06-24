package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnDiskChainStoreTest {

    @TempDir
    Path tempDir;

    private File records(String name) {
        return tempDir.resolve(name).toFile();
    }

    @Test
    void roundTripsRuleNameAndPremises() throws IOException {
        OnDiskChainStore store;
        try (OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(records("a.bin"))) {
            builder.add("c1", "T1", List.of("p1", "p2"));
            builder.add("c2", "T2", List.of("p3"));
            store = builder.build();
        }
        try (store) {
            assertEquals(2, store.size());

            NemoInferences.NemoInference c1 = store.find("c1").orElseThrow();
            assertEquals("c1", c1.getConclusion());
            assertEquals("T1", c1.getRuleName());
            assertEquals(List.of("p1", "p2"), c1.getPremises());

            NemoInferences.NemoInference c2 = store.find("c2").orElseThrow();
            assertEquals("T2", c2.getRuleName());
            assertEquals(List.of("p3"), c2.getPremises());
        }
    }

    @Test
    void preservesNullRuleNameAndEmptyPremises() throws IOException {
        OnDiskChainStore store;
        try (OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(records("b.bin"))) {
            builder.add("seed", null, List.of());
            store = builder.build();
        }
        try (store) {
            NemoInferences.NemoInference seed = store.find("seed").orElseThrow();
            assertNull(seed.getRuleName());
            assertTrue(seed.getPremises().isEmpty());
        }
    }

    @Test
    void returnsEmptyForMissingConclusion() throws IOException {
        OnDiskChainStore store;
        try (OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(records("c.bin"))) {
            builder.add("present", "T1", List.of());
            store = builder.build();
        }
        try (store) {
            assertFalse(store.find("absent").isPresent());
        }
    }

    @Test
    void survivesManyEntriesAndDirectoryGrowth() throws IOException {
        // Start the directory tiny so a few thousand inserts force repeated rehashing, then prove
        // every key still resolves to its own record through the probe/grow path.
        int count = 5_000;
        OnDiskChainStore store;
        try (OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(records("d.bin"), 16)) {
            for (int i = 0; i < count; i++) {
                List<String> premises = new ArrayList<>();
                for (int p = 0; p <= (i % 4); p++) {
                    premises.add("premise-" + i + "-" + p);
                }
                builder.add("conclusion-" + i, i % 3 == 0 ? null : "rule-" + (i % 7), premises);
            }
            store = builder.build();
        }
        try (store) {
            assertEquals(count, store.size());
            for (int i = 0; i < count; i++) {
                Optional<NemoInferences.NemoInference> found = store.find("conclusion-" + i);
                assertTrue(found.isPresent(), "missing conclusion-" + i);
                assertEquals(i % 3 == 0 ? null : "rule-" + (i % 7), found.get().getRuleName());
                assertEquals((i % 4) + 1, found.get().getPremises().size());
            }
            assertFalse(store.find("conclusion-" + count).isPresent());
        }
    }

    @Test
    void deletesRecordsFileOnClose() throws IOException {
        File recordsFile = records("e.bin");
        OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(recordsFile);
        builder.add("c", "T1", List.of("p"));
        OnDiskChainStore store = builder.build();
        assertTrue(recordsFile.exists());
        store.close();
        assertFalse(recordsFile.exists());
    }
}
