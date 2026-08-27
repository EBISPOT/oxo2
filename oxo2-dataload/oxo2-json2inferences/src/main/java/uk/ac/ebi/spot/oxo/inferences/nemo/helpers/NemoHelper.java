package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.dataload.solr.DataloadSolr;
import uk.ac.ebi.spot.oxo.dataload.solr.EntityDetails;
import uk.ac.ebi.spot.oxo.inferences.nemo.InferenceLookup;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;
import uk.ac.ebi.spot.oxo.model.sssom.*;

import java.util.*;

/**
 * Turns an nmo {@code --trace-output} provenance tree (parsed into {@link NemoInferences})
 * into a DAG of {@link InferredMapping}s for explanation (ADR-0009/0010).
 *
 * <p>Conclusions and premises are 4-arity {@code mapping(<id>, <s>, <p>, <o>)} atoms whose
 * first term is the {@code mapping_id} graph term carried through Nemo as N-Quads. The id term
 * is the source of truth for whether an atom is asserted or inferred:
 * <ul>
 *   <li><b>nil UUID</b> ({@link OXOInferenceConstants#NIL_MAPPING_ID_IRI}) — an inferred
 *       intermediate. Its {@code ruleName} (e.g. {@code "T10"}, {@code "RCE1-1"}) maps directly
 *       to a {@link ChainRulesEnum}; recurse into its {@code mapping(...)} premises.</li>
 *   <li><b>any other UUID</b> — an asserted leaf. The id <i>is</i> the asserted mapping's
 *       {@code mapping_id}, so {@link DataloadSolr#queryByMappingId} recovers its exact
 *       provenance (source set, curie/labels). No recursion; this is a leaf.</li>
 * </ul>
 */
public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    /**
     * Build the {@link InferredMapping} chain for one final conclusion. Inferred intermediates are
     * resolved through {@code lookup} (an {@code OnDiskChainStore} in production — ADR-0018) and
     * asserted leaves through Solr.
     *
     * <p>{@code memo} shares sub-chains across conclusions for speed; pass a <i>bounded</i> cache so
     * heap stays flat on the cross-set closure. The explanation is identity-independent
     * (length/asserted-evidence/distance are structural over the reachable sub-DAG), so a cache
     * eviction that forces a sub-chain to be rebuilt yields an equal result — only the work changes,
     * never the output.
     *
     * <p>{@code shardIndex} (nullable) is the bundle's shard corpus index (ADR-0049): when the
     * corpus asserts a leaf's exact (s, p, o) more than once, the leaf is canonicalised to the
     * lowest mapping_id among the duplicates — rather than whichever one Nemo's trace happened to
     * carry — and the full duplicate set is attached as equivalent asserted evidence. Without an
     * index the leaf keeps the trace's own mapping_id, the pre-ADR-0049 behaviour.
     */
    public static InferredMapping buildInferredMapping(InferenceLookup lookup, String conclusion,
            DataloadSolr solrClient, Map<String, InferredMapping> memo, String inferredMappingSetId,
            ShardAssertedIndex shardIndex) {
        return determineInferencesLeadingToConclusion(lookup, conclusion, solrClient, memo,
                inferredMappingSetId, shardIndex);
    }

    public static void collectAssertedMappingId(String atom, Set<String> assertedMappingIds) {
        String[] parts = parseAtomIris(atom);
        if (parts == null) return;
        if (!OXOInferenceConstants.isInferredIdTerm(parts[0])) {
            assertedMappingIds.add(OXOInferenceConstants.toBareMappingId(parts[0]));
        }
    }

    /**
     * Parse a 4-arity {@code pred(<id>, <s>, <p>, <o>)} atom (either {@code mapping(...)} or
     * {@code assertedMapping(...)}) into {@code [id, s, p, o]} (angle brackets stripped), or
     * {@code null} if the atom does not have exactly four IRI terms.
     */
    private static String[] parseAtomIris(String atom) {
        if (atom == null || atom.isEmpty()) return null;
        int firstAngle = atom.indexOf('<');
        int lastAngle = atom.lastIndexOf('>');
        if (firstAngle < 0 || lastAngle <= firstAngle) return null;
        String[] parts = atom.substring(firstAngle + 1, lastAngle).split(">\\s*,\\s*<");
        if (parts.length != 4) return null;
        return parts;
    }

    private static boolean isMappingAtom(String atom) {
        return atom != null && atom.startsWith("mapping(");
    }

    private static InferredMapping determineInferencesLeadingToConclusion(
            InferenceLookup lookup,
            String conclusion, DataloadSolr solrClient,
            Map<String, InferredMapping> memo,
            String inferredMappingSetId,
            ShardAssertedIndex shardIndex) {

        InferredMapping cached = memo.get(conclusion);
        if (cached != null) return cached;

        String[] parts = parseAtomIris(conclusion);
        if (parts == null) {
            logger.warn("Skipping malformed mapping atom (expected 4 IRI terms): {}", conclusion);
            return null;
        }
        String mappingIdTerm = parts[0];
        String subjectIRI = parts[1];
        String predicateIRI = parts[2];
        String objectIRI = parts[3];

        InferredMapping inferredMapping = new InferredMapping();
        inferredMapping.setSubjectIRI(new Uri(subjectIRI));
        inferredMapping.setPredicateIRI(new Uri(predicateIRI));
        inferredMapping.setObjectIRI(new Uri(objectIRI));

        if (!OXOInferenceConstants.isInferredIdTerm(mappingIdTerm)) {
            // Asserted leaf: the id term is a real mapping_id — but only the one Nemo's derivation
            // happened to use. When the corpus asserts the same (s, p, o) in several sets, the
            // duplicates are indistinguishable premises, so the leaf shown in the chain is
            // canonicalised to the lowest mapping_id and the full set becomes the evidence
            // (ADR-0049). With no shard index, the trace's own id stands, as before.
            String tracedMappingId = OXOInferenceConstants.toBareMappingId(mappingIdTerm);
            List<String> corpusMappingIds = shardIndex == null
                    ? List.of()
                    : shardIndex.idsFor(subjectIRI, predicateIRI, objectIRI);
            String canonicalMappingId;
            if (corpusMappingIds.isEmpty()) {
                if (shardIndex != null) {
                    logger.warn("Asserted leaf <{}> <{}> <{}> (mapping_id {}) is not in the shard "
                            + "corpus index; keeping the trace's own mapping_id.",
                            subjectIRI, predicateIRI, objectIRI, tracedMappingId);
                }
                canonicalMappingId = tracedMappingId;
            } else {
                canonicalMappingId = corpusMappingIds.get(0);
                if (!corpusMappingIds.contains(tracedMappingId)) {
                    logger.warn("Trace mapping_id {} for asserted leaf <{}> <{}> <{}> is not among "
                            + "its shard corpus quads {} — trace and corpus disagree.",
                            tracedMappingId, subjectIRI, predicateIRI, objectIRI, corpusMappingIds);
                }
            }
            populateAssertedLeaf(inferredMapping, canonicalMappingId, solrClient);

            if (corpusMappingIds.size() > 1) {
                List<InferredMapping> equivalentLeaves = new ArrayList<>(corpusMappingIds.size());
                for (String duplicateMappingId : corpusMappingIds) {
                    if (duplicateMappingId.equals(canonicalMappingId)) {
                        equivalentLeaves.add(inferredMapping);
                        continue;
                    }
                    InferredMapping duplicateLeaf = new InferredMapping();
                    duplicateLeaf.setSubjectIRI(new Uri(subjectIRI));
                    duplicateLeaf.setPredicateIRI(new Uri(predicateIRI));
                    duplicateLeaf.setObjectIRI(new Uri(objectIRI));
                    populateAssertedLeaf(duplicateLeaf, duplicateMappingId, solrClient);
                    equivalentLeaves.add(duplicateLeaf);
                }
                inferredMapping.setEquivalentAssertedLeaves(equivalentLeaves);
            }

            memo.put(conclusion, inferredMapping);
            return inferredMapping;
        }

        // Inferred intermediate (nil UUID): identify the rule that derived it and recurse.
        inferredMapping.setMappingSetId(inferredMappingSetId);
        inferredMapping.setMappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL);
        inferredMapping.setMappingJustification(new EntityReference(OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION));

        Optional<NemoInferences.NemoInference> optionalNemoInference =
                lookup.find(conclusion);
        if (optionalNemoInference.isPresent()) {
            NemoInferences.NemoInference nemoInference = optionalNemoInference.get();
            ChainRulesEnum chainRule = resolveChainRule(nemoInference.getRuleName(), conclusion);
            InferredMapping.ChainRuleApplications chainRuleApplications =
                    new InferredMapping.ChainRuleApplications(
                            chainRule == null ? Optional.empty() : Optional.of(chainRule));

            List<InferredMapping> premises = new ArrayList<>();
            if (nemoInference.getPremises() != null) {
                for (String premise : nemoInference.getPremises()) {
                    // Named chain-rule bodies reference only mapping(...) atoms; the assertedMapping(...)
                    // EDB premise lives under the unnamed seed rule, which a real mapping_id short-circuits
                    // before we ever consult it. The guard is belt-and-suspenders.
                    if (!isMappingAtom(premise)) continue;
                    InferredMapping premiseMapping = determineInferencesLeadingToConclusion(
                            lookup, premise, solrClient, memo, inferredMappingSetId, shardIndex);
                    if (premiseMapping != null) {
                        premises.add(premiseMapping);
                    }
                }
            }
            chainRuleApplications.setPremises(premises);
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplications));
        } else {
            logger.warn("No Nemo inference found for inferred conclusion: {}", conclusion);
        }

        enrichEntityDetails(inferredMapping, solrClient);
        memo.put(conclusion, inferredMapping);
        return inferredMapping;
    }

    /**
     * Fill in one asserted leaf from its Solr doc: provenance (source set, curie/labels) via
     * {@link InferredMapping#populateFromMapping}, the ASSERTED chain-rule marker, and the
     * entity-index curie/label enrichment. Shared between the canonical leaf shown in the chain
     * and its corpus duplicates (ADR-0049), so every leaf is built one way.
     */
    private static void populateAssertedLeaf(InferredMapping leaf, String bareMappingId,
            DataloadSolr solrClient) {
        Mapping assertedMapping = solrClient.queryByMappingId(bareMappingId);
        if (assertedMapping != null) {
            leaf.populateFromMapping(assertedMapping);
        } else {
            logger.warn("No asserted mapping in Solr for mapping_id {} (<{}> <{}> <{}>)",
                    bareMappingId, leaf.getSubjectIRI(), leaf.getPredicateIRI(), leaf.getObjectIRI());
            leaf.setMappingId(bareMappingId);
        }
        InferredMapping.ChainRuleApplications chainRuleApplications =
                new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
        chainRuleApplications.setPremises(new ArrayList<>());
        leaf.setChainRuleApplications(Optional.of(chainRuleApplications));

        enrichEntityDetails(leaf, solrClient);
    }

    /**
     * Map a trace {@code ruleName} to its {@link ChainRulesEnum}. Returns {@code null} (with a
     * WARN) for an absent or unrecognised name rather than throwing, so one odd trace entry
     * doesn't abort the whole file.
     */
    private static ChainRulesEnum resolveChainRule(String ruleName, String conclusion) {
        if (ruleName == null || ruleName.isBlank()) {
            logger.warn("Inferred conclusion has no ruleName in trace: {}", conclusion);
            return null;
        }
        try {
            return ChainRulesEnum.fromJson(ruleName);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown ruleName '{}' in trace for conclusion {}", ruleName, conclusion);
            return null;
        }
    }

    private static void enrichEntityDetails(InferredMapping inferredMapping, DataloadSolr solrClient) {
        updateSubject(inferredMapping, solrClient);
        updatePredicate(inferredMapping, solrClient);
        updateObject(inferredMapping, solrClient);
    }

    private static void updateSubject(InferredMapping inferredMapping, DataloadSolr solrClient) {
        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                inferredMapping.getSubjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent()) inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent()) inferredMapping.setSubjectLabel(details.getLabel());
        }
    }

    private static void updatePredicate(InferredMapping inferredMapping, DataloadSolr solrClient) {
        String predicateIri = inferredMapping.getPredicateIRI().asStringIRI();
        Optional<String> predicateCurie = PrefixMap.toCurie(predicateIri);
        if (predicateCurie.isPresent()) {
            inferredMapping.setPredicateId(predicateCurie.get());
            return;
        }
        EntityDetails details = solrClient.queryEntityDetailsForIRI(predicateIri);
        if (details != null) {
            if (details.isCuriePresent()) inferredMapping.setPredicateId(details.getCurie());
            if (details.isLabelPresent()) inferredMapping.setPredicateLabel(details.getLabel());
        }
    }

    private static void updateObject(InferredMapping inferredMapping, DataloadSolr solrClient) {
        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                inferredMapping.getObjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent()) inferredMapping.setObjectId(details.getCurie());
            if (details.isLabelPresent()) inferredMapping.setObjectLabel(details.getLabel());
        }
    }

}
