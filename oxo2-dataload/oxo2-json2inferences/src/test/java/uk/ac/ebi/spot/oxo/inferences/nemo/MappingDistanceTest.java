package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Distance = the number of distinct ontologies a mapping spans, minus one, floored at 1 (ADR-0031).
 * OxO2's notion of a term's ontology is its CURIE prefix (ADR-0024), so distance is counted over the
 * prefixes of every subject/object in the explanation DAG.
 *
 * <p>These pin the semantics the old {@code extractParts}/{@code calculateMappingDistance} pair got
 * wrong: it only recognised OBO {@code PREFIX_NUMBER} IRIs (missing MeSH/UMLS and any non-OBO scheme)
 * and only descended one premise level, so it under-counted and could underflow to {@code 0}/{@code -1}
 * — which the {@code SolrQueryBuilder} decay {@code div(100, pow(5, distance-1))} would have turned
 * into a boost <em>above</em> the curated/asserted tier.
 */
class MappingDistanceTest {

    /** A node carrying CURIE subject/object ids — the ontology signal distance counts. */
    private static InferredMapping node(String subjectCurie, String objectCurie,
                                        ChainRulesEnum rule, InferredMapping... premises) {
        InferredMapping node = new InferredMapping();
        node.setSubjectId(subjectCurie);
        node.setObjectId(objectCurie);
        // IRIs back the node's identity (equals/hashCode) and the surrounding pipeline; the distance
        // calc reads the CURIE ids, so throwaway IRIs suffice here.
        node.setSubjectIRI(new Uri("urn:iri:" + subjectCurie));
        node.setPredicateIRI(new Uri("urn:p"));
        node.setObjectIRI(new Uri("urn:iri:" + objectCurie));
        InferredMapping.ChainRuleApplications applications =
                new InferredMapping.ChainRuleApplications(Optional.ofNullable(rule));
        applications.setPremises(List.of(premises));
        node.setChainRuleApplications(Optional.of(applications));
        return node;
    }

    /** An asserted leaf: a present chain rule with no premises. */
    private static InferredMapping leaf(String subjectCurie, String objectCurie) {
        return node(subjectCurie, objectCurie, ChainRulesEnum.ASSERTED);
    }

    @Test
    void assertedSingleOntologyMappingIsDistanceOne() {
        // Both terms in one ontology — at most two ontologies is distance 1.
        assertEquals(1, ExplainInferredMappings.calculateMappingDistance(leaf("EX:A", "EX:B")));
    }

    @Test
    void twoOntologiesIsDistanceOne() {
        assertEquals(1, ExplainInferredMappings.calculateMappingDistance(leaf("EX:A", "EY:B")));
    }

    @Test
    void threeOntologiesIsDistanceTwo() {
        // EX:A -> EZ:C  proved by  EX:A -> EY:B  and  EY:B -> EZ:C  spans {EX, EY, EZ}.
        InferredMapping root = node("EX:A", "EZ:C", ChainRulesEnum.T1,
                leaf("EX:A", "EY:B"), leaf("EY:B", "EZ:C"));
        assertEquals(2, ExplainInferredMappings.calculateMappingDistance(root));
    }

    @Test
    void fourOntologiesIsDistanceThree() {
        InferredMapping root = node("EX:A", "EW:D", ChainRulesEnum.T1,
                leaf("EX:A", "EY:B"), leaf("EY:B", "EZ:C"), leaf("EZ:C", "EW:D"));
        assertEquals(3, ExplainInferredMappings.calculateMappingDistance(root));
    }

    @Test
    void ontologyBuriedDeepInTheDagIsStillCounted() {
        // EY appears only two premise levels down — the old one-level walk would have missed it.
        //   root:  EX:A -> EW:D
        //     mid: EX:A -> EZ:C  (proved by EX:A -> EY:B , EY:B -> EZ:C)
        //     leaf: EZ:C -> EW:D
        InferredMapping mid = node("EX:A", "EZ:C", ChainRulesEnum.T1,
                leaf("EX:A", "EY:B"), leaf("EY:B", "EZ:C"));
        InferredMapping root = node("EX:A", "EW:D", ChainRulesEnum.T1, mid, leaf("EZ:C", "EW:D"));
        assertEquals(3, ExplainInferredMappings.calculateMappingDistance(root)); // {EX,EY,EZ,EW}
    }

    @Test
    void unresolvableEntitiesFloorToDistanceOne() {
        // Bare IRIs never resolve to a CURIE prefix -> zero ontologies -> floored to 1, never 0/-1.
        assertEquals(1, ExplainInferredMappings.calculateMappingDistance(
                leaf("http://example.org/a", "http://example.org/b")));
    }

    @Test
    void sharedSubDagIsCountedOnceAndTerminates() {
        // Diamond: the shared leaf is reachable by two paths; the identity-visited walk counts it once
        // and does not loop.
        InferredMapping shared = leaf("EY:B", "EZ:C");
        InferredMapping left = node("EX:A", "EZ:C", ChainRulesEnum.T1, leaf("EX:A", "EY:B"), shared);
        InferredMapping right = node("EZ:C", "EW:D", ChainRulesEnum.T1, shared, leaf("EZ:C", "EW:D"));
        InferredMapping root = node("EX:A", "EW:D", ChainRulesEnum.T1, left, right);
        assertEquals(3, ExplainInferredMappings.calculateMappingDistance(root)); // {EX,EY,EZ,EW}
    }
}
