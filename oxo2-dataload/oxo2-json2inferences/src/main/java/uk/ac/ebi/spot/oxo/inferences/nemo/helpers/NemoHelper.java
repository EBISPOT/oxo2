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
     */
    public static InferredMapping buildInferredMapping(InferenceLookup lookup, String conclusion,
            DataloadSolr solrClient, Map<String, InferredMapping> memo, String inferredMappingSetId) {
        return determineInferencesLeadingToConclusion(lookup, conclusion, solrClient, memo,
                inferredMappingSetId);
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
            String inferredMappingSetId) {

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
            // Asserted leaf: the id term is the real mapping_id and the provenance key.
            String bareMappingId = OXOInferenceConstants.toBareMappingId(mappingIdTerm);
            Mapping assertedMapping = solrClient.queryByMappingId(bareMappingId);
            if (assertedMapping != null) {
                inferredMapping.populateFromMapping(assertedMapping);
            } else {
                logger.warn("No asserted mapping in Solr for mapping_id {} (<{}> <{}> <{}>)",
                        bareMappingId, subjectIRI, predicateIRI, objectIRI);
                inferredMapping.setMappingId(bareMappingId);
            }
            InferredMapping.ChainRuleApplications chainRuleApplications =
                    new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
            chainRuleApplications.setPremises(new ArrayList<>());
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplications));

            enrichEntityDetails(inferredMapping, solrClient);
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
                            lookup, premise, solrClient, memo, inferredMappingSetId);
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
