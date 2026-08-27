package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.ShardAssertedIndex;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shard corpus index behind ADR-0049: the same (s, p, o) asserted in several sets produces
 * several corpus quads differing only in the mapping_id graph term, and an asserted leaf must
 * expand to all of them — deterministically — rather than to whichever quad Nemo's trace walked.
 */
class ShardAssertedIndexTest {

    private static final String SUBJECT = "http://purl.obolibrary.org/obo/DOID_0050043";
    private static final String PREDICATE = "http://www.w3.org/2004/02/skos/core#exactMatch";
    private static final String OBJECT = "http://purl.obolibrary.org/obo/MONDO_0000616";

    private static Path writeShard(Path directory, String name, String... quadLines)
            throws IOException {
        Path shardFile = directory.resolve(name);
        Files.write(shardFile, List.of(quadLines));
        return shardFile;
    }

    private static String quad(String subject, String predicate, String object, String mappingId) {
        return "<%s> <%s> <%s> <urn:uuid:%s> .".formatted(subject, predicate, object, mappingId);
    }

    @Test
    void duplicateTriplesCollectAllTheirMappingIdsSorted(@TempDir Path tempDir) throws IOException {
        // setB's quad first, so the sorted order below is provably not insertion order.
        writeShard(tempDir, "shard00000.nq",
                quad(SUBJECT, PREDICATE, OBJECT, "ffffffff-0000-0000-0000-000000000000"),
                quad(SUBJECT, PREDICATE, OBJECT, "00000000-0000-0000-0000-00000000ffff"),
                quad(OBJECT, PREDICATE, SUBJECT, "11111111-0000-0000-0000-000000000000"));
        Path chainFile = tempDir.resolve("shard00000-chains.json");
        Files.writeString(chainFile, "{}");

        ShardAssertedIndex index =
                ShardAssertedIndex.loadForChainFiles(new String[] {chainFile.toString()});

        assertEquals(List.of("00000000-0000-0000-0000-00000000ffff",
                        "ffffffff-0000-0000-0000-000000000000"),
                index.idsFor(SUBJECT, PREDICATE, OBJECT),
                "both duplicates, lowest mapping_id first");
        assertEquals(List.of("11111111-0000-0000-0000-000000000000"),
                index.idsFor(OBJECT, PREDICATE, SUBJECT),
                "the reverse direction is a different assertion, not a duplicate");
        assertEquals(3, index.allMappingIds().size());
    }

    @Test
    void aTripleAbsentFromTheCorpusYieldsAnEmptyList(@TempDir Path tempDir) throws IOException {
        writeShard(tempDir, "shard00000.nq", quad(SUBJECT, PREDICATE, OBJECT, "aaaaaaaa-0000-0000-0000-000000000000"));
        Path chainFile = tempDir.resolve("shard00000-chains.json");
        Files.writeString(chainFile, "{}");

        ShardAssertedIndex index =
                ShardAssertedIndex.loadForChainFiles(new String[] {chainFile.toString()});

        assertTrue(index.idsFor(SUBJECT, PREDICATE, "http://example.org/other").isEmpty());
    }

    @Test
    void noResolvableShardFileMeansNoIndex(@TempDir Path tempDir) throws IOException {
        // Legacy layout: chain file with no .nq anywhere. The caller must get null and fall back
        // to the trace's own mapping_id — old pipelines keep working.
        Path chainFile = tempDir.resolve("shard00000-chains.json");
        Files.writeString(chainFile, "{}");

        assertNull(ShardAssertedIndex.loadForChainFiles(new String[] {chainFile.toString()}));
    }

    @Test
    void shardFileIsFoundInTheSiblingShardsDirectory(@TempDir Path tempDir) throws IOException {
        // The on-disk pipeline layout: crossSet/shardChains beside crossSet/shards.
        Path chainsDir = Files.createDirectories(tempDir.resolve("shardChains"));
        Path shardsDir = Files.createDirectories(tempDir.resolve("shards"));
        writeShard(shardsDir, "shard00042.nq", quad(SUBJECT, PREDICATE, OBJECT, "aaaaaaaa-0000-0000-0000-000000000000"));
        Path chainFile = chainsDir.resolve("shard00042-chains.json");
        Files.writeString(chainFile, "{}");

        ShardAssertedIndex index =
                ShardAssertedIndex.loadForChainFiles(new String[] {chainFile.toString()});

        assertEquals(List.of("aaaaaaaa-0000-0000-0000-000000000000"),
                index.idsFor(SUBJECT, PREDICATE, OBJECT));
    }

    @Test
    void evidenceExpandsToTheDuplicatesAndDeduplicatesById() {
        // The consumption side: a leaf carrying equivalent duplicates contributes all of them to
        // asserted evidence, and canonicalEvidence orders by mapping_id and drops repeats — never
        // keying on InferredMapping.equals, which is (s, p, o) identity and would conflate the
        // very duplicates being collected.
        InferredMapping canonicalLeaf = assertedLeaf("00000000-0000-0000-0000-00000000ffff");
        InferredMapping duplicateLeaf = assertedLeaf("ffffffff-0000-0000-0000-000000000000");
        canonicalLeaf.setEquivalentAssertedLeaves(List.of(canonicalLeaf, duplicateLeaf));

        InferredMapping conclusion = new InferredMapping();
        conclusion.setSubjectIRI(new Uri(OBJECT));
        conclusion.setPredicateIRI(new Uri(PREDICATE));
        conclusion.setObjectIRI(new Uri(SUBJECT));
        InferredMapping.ChainRuleApplications applications =
                new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.SYM_EXACT_MATCH));
        // The same leaf as two premises, as a shared branch in a proof DAG would produce.
        applications.setPremises(List.of(canonicalLeaf, canonicalLeaf));
        conclusion.setChainRuleApplications(Optional.of(applications));

        List<InferredMapping> evidence = ExplainInferredMappings.canonicalEvidence(
                ExplainInferredMappings.determineAssertedMappingsForExplanation(
                        conclusion, new java.util.IdentityHashMap<>()));

        assertEquals(List.of("00000000-0000-0000-0000-00000000ffff",
                        "ffffffff-0000-0000-0000-000000000000"),
                evidence.stream().map(InferredMapping::getMappingId).toList(),
                "each duplicate exactly once, ordered by mapping_id");
    }

    private static InferredMapping assertedLeaf(String mappingId) {
        InferredMapping leaf = new InferredMapping();
        leaf.setMappingId(mappingId);
        leaf.setSubjectIRI(new Uri(SUBJECT));
        leaf.setPredicateIRI(new Uri(PREDICATE));
        leaf.setObjectIRI(new Uri(OBJECT));
        InferredMapping.ChainRuleApplications applications =
                new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
        applications.setPremises(List.of());
        leaf.setChainRuleApplications(Optional.of(applications));
        return leaf;
    }
}
