package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplainInferredMappingsTest {

    /** Build a leaf node tagged ASSERTED. */
    private static InferredMapping asserted(String s, String p, String o) {
        InferredMapping node = new InferredMapping();
        node.setSubjectIRI(new Uri(s));
        node.setPredicateIRI(new Uri(p));
        node.setObjectIRI(new Uri(o));
        InferredMapping.ChainRuleApplications cra =
                new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
        cra.setPremises(List.of());
        node.setChainRuleApplications(Optional.of(cra));
        return node;
    }

    /** Build an inferred node with a chain rule and the given premises. */
    private static InferredMapping inferred(String s, String p, String o,
                                             ChainRulesEnum rule,
                                             InferredMapping... premises) {
        InferredMapping node = new InferredMapping();
        node.setSubjectIRI(new Uri(s));
        node.setPredicateIRI(new Uri(p));
        node.setObjectIRI(new Uri(o));
        InferredMapping.ChainRuleApplications cra =
                new InferredMapping.ChainRuleApplications(Optional.of(rule));
        cra.setPremises(List.of(premises));
        node.setChainRuleApplications(Optional.of(cra));
        return node;
    }

    @Test
    void explanationLengthCountsChainNodesIncludingRoot() {
        InferredMapping leaf = asserted("urn:a", "urn:p", "urn:b");
        InferredMapping mid = inferred("urn:a", "urn:p", "urn:c", ChainRulesEnum.T1, leaf);
        InferredMapping root = inferred("urn:a", "urn:p", "urn:d", ChainRulesEnum.T1, mid);

        IdentityHashMap<InferredMapping, Integer> memo = new IdentityHashMap<>();

        // leaf: ASSERTED chain present but with no premises is still a non-empty
        // chain rule application, so length counts the leaf node itself.
        assertEquals(1, ExplainInferredMappings.explanationLength(leaf, memo));
        assertEquals(2, ExplainInferredMappings.explanationLength(mid, memo));
        assertEquals(3, ExplainInferredMappings.explanationLength(root, memo));
    }

    @Test
    void explanationLengthMemoizesSharedSubChain() {
        // Diamond: root has two parents, both pointing at the same shared leaf.
        //   root -> leftMid \
        //                    -> sharedLeaf
        //   root -> rightMid /
        InferredMapping sharedLeaf = asserted("urn:s", "urn:p", "urn:o");
        InferredMapping leftMid = inferred("urn:s", "urn:p", "urn:l",
                ChainRulesEnum.T1, sharedLeaf);
        InferredMapping rightMid = inferred("urn:s", "urn:p", "urn:r",
                ChainRulesEnum.T1, sharedLeaf);
        InferredMapping root = inferred("urn:s", "urn:p", "urn:root",
                ChainRulesEnum.T1, leftMid, rightMid);

        IdentityHashMap<InferredMapping, Integer> memo = new IdentityHashMap<>();
        int rootLen = ExplainInferredMappings.explanationLength(root, memo);

        // root(1) + leftMid(1) + rightMid(1) + sharedLeaf(1) + sharedLeaf(1) = 5
        assertEquals(5, rootLen);
        // sharedLeaf walked once even though referenced twice.
        assertEquals(4, memo.size());
        assertEquals(1, memo.get(sharedLeaf));
    }

    @Test
    void assertedMappingsCollectsAssertedLeavesInTraversalOrder() {
        InferredMapping leafA = asserted("urn:s", "urn:p", "urn:a");
        InferredMapping leafB = asserted("urn:s", "urn:p", "urn:b");
        InferredMapping mid = inferred("urn:s", "urn:p", "urn:m",
                ChainRulesEnum.T1, leafA, leafB);
        InferredMapping root = inferred("urn:s", "urn:p", "urn:root",
                ChainRulesEnum.T1, mid);

        IdentityHashMap<InferredMapping, List<InferredMapping>> memo = new IdentityHashMap<>();
        List<InferredMapping> result = ExplainInferredMappings
                .determineAssertedMappingsForExplanation(root, memo);

        assertEquals(List.of(leafA, leafB), result);
    }

    @Test
    void assertedMappingsMemoSharesListInstanceForSharedSubChain() {
        InferredMapping sharedLeaf = asserted("urn:s", "urn:p", "urn:o");
        InferredMapping leftMid = inferred("urn:s", "urn:p", "urn:l",
                ChainRulesEnum.T1, sharedLeaf);
        InferredMapping rightMid = inferred("urn:s", "urn:p", "urn:r",
                ChainRulesEnum.T1, sharedLeaf);
        InferredMapping root = inferred("urn:s", "urn:p", "urn:root",
                ChainRulesEnum.T1, leftMid, rightMid);

        IdentityHashMap<InferredMapping, List<InferredMapping>> memo = new IdentityHashMap<>();
        List<InferredMapping> rootResult = ExplainInferredMappings
                .determineAssertedMappingsForExplanation(root, memo);

        // Both diamond legs surface the same asserted leaf, so it appears twice
        // at the root level — addAll preserves order without dedup, matching the
        // pre-memoization behaviour.
        assertEquals(List.of(sharedLeaf, sharedLeaf), rootResult);

        // sharedLeaf cached once and the cached entry is reused (not recomputed)
        // when leftMid and rightMid pull it in.
        assertSame(memo.get(leftMid).get(0), memo.get(rightMid).get(0));
        assertEquals(4, memo.size());
    }

    /**
     * An asserted leaf's chain rule is *present* with an empty premise list. A dangling node —
     * NemoHelper could not resolve its derivation — has *no* chain rule application at all. Only the
     * latter means the shard was not self-contained (ADR-0028); confusing them would either reject
     * every explanation or silently ship truncated ones.
     */
    @Test
    void assertedLeafIsNotDanglingButAnUnresolvedNodeIs() {
        InferredMapping leaf = asserted("urn:a", "urn:p", "urn:b");
        InferredMapping root = inferred("urn:a", "urn:p", "urn:c", ChainRulesEnum.T1, leaf);
        assertFalse(ExplainInferredMappings.hasDanglingPremise(root, new IdentityHashMap<>()),
                "a chain bottoming out in ASSERTED leaves is complete");

        InferredMapping unresolved = new InferredMapping();
        unresolved.setSubjectIRI(new Uri("urn:b"));
        unresolved.setPredicateIRI(new Uri("urn:p"));
        unresolved.setObjectIRI(new Uri("urn:c"));
        unresolved.setChainRuleApplications(Optional.empty());   // NemoHelper found no derivation
        InferredMapping truncated = inferred("urn:a", "urn:p", "urn:c", ChainRulesEnum.T1, leaf, unresolved);

        assertTrue(ExplainInferredMappings.hasDanglingPremise(truncated, new IdentityHashMap<>()),
                "a premise with no chainRuleApplications means a premise is missing from the trace");
        assertTrue(ExplainInferredMappings.hasDanglingPremise(unresolved, new IdentityHashMap<>()));
    }
}
