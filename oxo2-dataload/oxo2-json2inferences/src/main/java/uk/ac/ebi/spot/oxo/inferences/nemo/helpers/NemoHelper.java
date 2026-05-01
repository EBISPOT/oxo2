package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.dataload.solr.DataloadSolr;
import uk.ac.ebi.spot.oxo.dataload.solr.EntityDetails;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.*;
import uk.ac.ebi.spot.oxo.model.sssom.*;

import java.util.*;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    public static Set<InferredMapping> fromNemoInferencesToInferredMappings(
            NemoInferences nemoInferences, DataloadSolr solrClient, String sourceMappingSetId) {

        // Walk every conclusion/premise string in the chains file once to collect
        // distinct subject/predicate/object IRIs, then bulk-populate the Solr
        // entity-details cache in batched queries. Without this, each
        // createInferredMapping() call would issue 3 sequential Solr round-trips
        // for IRI -> curie/label lookups (cached after the first hit but still
        // single-keyed), which dominates wall-clock for large chains files.
        prefetchEntityDetailsForChains(nemoInferences, solrClient, sourceMappingSetId);

        Set<InferredMapping> inferredMappings = new HashSet<>();
        // Memoize InferredMapping per conclusion string so that shared sub-chains
        // in the inference DAG share one object instead of being re-expanded as a
        // fresh subtree per parent. Without this, deep DAGs with shared premises
        // blow up super-linearly. The memo lives only for this call (per file).
        Map<String, InferredMapping> memo = new HashMap<>();

        List<String> finalConclusions = nemoInferences.getFinalConclusion();
        int totalConclusions = finalConclusions != null ? finalConclusions.size() : 0;
        logger.info("Building InferredMappings for {} final conclusions", totalConclusions);
        long buildStart = System.currentTimeMillis();
        int progressInterval = Math.max(1000, totalConclusions / 20);
        int processed = 0;

        String inferredMappingSetId = OXOInferenceConstants.inferredMappingSetIdFor(sourceMappingSetId);

        for (String finalConclusion : finalConclusions) {
            InferredMapping inferredMapping = determineInferencesLeadingToConclusion(nemoInferences,
                    finalConclusion, solrClient, memo, sourceMappingSetId, inferredMappingSetId);
            inferredMappings.add(inferredMapping);
            processed++;
            if (processed % progressInterval == 0 || processed == totalConclusions) {
                long elapsedMs = System.currentTimeMillis() - buildStart;
                double rate = elapsedMs > 0 ? (processed * 1000.0 / elapsedMs) : 0.0;
                logger.info("InferredMapping build progress: {}/{} ({}%) — memo size {}, elapsed {} ms, ~{} conclusions/s",
                        processed, totalConclusions,
                        totalConclusions > 0 ? (processed * 100 / totalConclusions) : 100,
                        memo.size(), elapsedMs, String.format("%.0f", rate));
            }
        }

        logger.info("Finished InferredMapping build: {} top-level mappings, {} memoized conclusions, {} ms total",
                inferredMappings.size(), memo.size(), System.currentTimeMillis() - buildStart);
        return inferredMappings;
    }

    private static void prefetchEntityDetailsForChains(NemoInferences nemoInferences,
                                                        DataloadSolr solrClient,
                                                        String sourceMappingSetId) {
        Set<String> subjectIris = new HashSet<>();
        Set<String> predicateIris = new HashSet<>();
        Set<String> objectIris = new HashSet<>();
        // Use a Set keyed on the spoKey for cheap dedup; carry the parsed parts
        // alongside so we can hand String[3] arrays to the SPO prefetch.
        Map<String, String[]> distinctTriples = new HashMap<>();

        if (nemoInferences.getFinalConclusion() != null) {
            nemoInferences.getFinalConclusion().forEach(c ->
                    collectFromConclusion(c, subjectIris, predicateIris, objectIris, distinctTriples));
        }
        if (nemoInferences.getInferences() != null) {
            for (NemoInferences.NemoInference inference : nemoInferences.getInferences()) {
                collectFromConclusion(inference.getConclusion(), subjectIris, predicateIris, objectIris, distinctTriples);
                if (inference.getPremises() != null) {
                    for (String premise : inference.getPremises()) {
                        collectFromConclusion(premise, subjectIris, predicateIris, objectIris, distinctTriples);
                    }
                }
            }
        }

        long start = System.currentTimeMillis();
        solrClient.prefetchEntityDetails(SUBJECT_IRI, subjectIris, SUBJECT_ID, SUBJECT_LABEL);
        solrClient.prefetchEntityDetails(PREDICATE_IRI, predicateIris, PREDICATE_ID, PREDICATE_LABEL);
        solrClient.prefetchEntityDetails(OBJECT_IRI, objectIris, OBJECT_ID, OBJECT_LABEL);
        logger.info("Prefetched entity details for {} subject / {} predicate / {} object IRIs in {} ms",
                subjectIris.size(), predicateIris.size(), objectIris.size(),
                System.currentTimeMillis() - start);

        solrClient.prefetchMappingsForTriples(distinctTriples.values(), sourceMappingSetId);
    }

    private static void collectFromConclusion(String conclusion,
                                               Set<String> subjectIris,
                                               Set<String> predicateIris,
                                               Set<String> objectIris,
                                               Map<String, String[]> distinctTriples) {
        if (conclusion == null || conclusion.isEmpty()) return;
        int firstAngle = conclusion.indexOf('<');
        int lastAngle = conclusion.lastIndexOf('>');
        if (firstAngle < 0 || lastAngle <= firstAngle) return;
        String[] parts = conclusion.substring(firstAngle + 1, lastAngle).split(">\\s*,\\s*<");
        if (parts.length != 3) return;
        subjectIris.add(parts[0]);
        predicateIris.add(parts[1]);
        objectIris.add(parts[2]);
        String key = parts[0] + '\0' + parts[1] + '\0' + parts[2];
        distinctTriples.putIfAbsent(key, parts);
    }

    private static InferredMapping determineInferencesLeadingToConclusion(
            NemoInferences nemoInferences,
            String conclusion, DataloadSolr solrClient,
            Map<String, InferredMapping> memo,
            String sourceMappingSetId,
            String inferredMappingSetId) {

        InferredMapping cached = memo.get(conclusion);
        if (cached != null) return cached;

        InferredMapping inferredMapping = createInferredMapping(conclusion, solrClient, sourceMappingSetId);

        Optional<NemoInferences.NemoInference> optionalNemoInference =
                nemoInferences.findNemoInferenceForConclusion(conclusion);
        if (optionalNemoInference.isPresent()) {
            NemoInferences.NemoInference nemoInference = optionalNemoInference.get();
            Optional<NemoChainRulesEnum> nemoChainRulesEnum = NemoChainRulesEnum.getChainRuleFromNemoRuleName(
                    nemoInference.getRule());
            Optional<ChainRulesEnum> chainRulesEnum = nemoChainRulesEnum.map(
                    nemoRule -> ChainRulesEnum.valueOf(nemoRule.name()));

            if (chainRulesEnum.isEmpty()) {
                InferredMapping.ChainRuleApplications chainRuleApplication =
                        new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
                chainRuleApplication.setPremises(new ArrayList<>());
                inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
            } else {
                InferredMapping.ChainRuleApplications chainRuleApplication =
                        new InferredMapping.ChainRuleApplications(chainRulesEnum);

                inferredMapping.setMappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL);
                inferredMapping.setMappingJustification(new EntityReference(
                        OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION));
                inferredMapping.setMappingSetId(inferredMappingSetId);

                List<InferredMapping> premises = new ArrayList<>();

                nemoInference.getPremises().forEach(premise -> {
                    InferredMapping premiseAsInferredMapping = determineInferencesLeadingToConclusion(
                            nemoInferences, premise, solrClient, memo, sourceMappingSetId, inferredMappingSetId
                    );
                    premises.add(premiseAsInferredMapping);
                });

                chainRuleApplication.setPremises(premises);
                inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
            }
        }
        memo.put(conclusion, inferredMapping);
        return inferredMapping;
    }

    private static boolean isValidMappingString(String mappingString) {
        if (mappingString == null || mappingString.isEmpty()) {
            return false;
        }
        String mappingRegex = "^mapping\\(\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*\\)$";
        boolean result = mappingString.matches(mappingRegex);
        logger.trace("Result of isValidMappingString for {} is {} ", mappingString, result);
        return result;
    }


    private static InferredMapping createInferredMapping(
            String mapping, DataloadSolr solrClient, String sourceMappingSetId) {

        InferredMapping inferredMapping = new InferredMapping();
        if (!isValidMappingString(mapping)) {
            throw new IllegalArgumentException("Invalid mapping string: " + mapping);
        }

        String[] parts = mapping.substring(mapping.indexOf('<') + 1, mapping.lastIndexOf('>')).split(">\\s*,\\s*<");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Conclusion string must contain exactly three URIs: " + mapping);
        }

        String subjectIRI = parts[0];
        String predicateIRI = parts[1];
        String objectIRI = parts[2];
        inferredMapping.setSubjectIRI(new Uri(subjectIRI));
        inferredMapping.setPredicateIRI(new Uri(predicateIRI));
        inferredMapping.setObjectIRI(new Uri(objectIRI));

        // Scope the asserted-mapping lookup to the source set whose TTL was fed
        // into nmo for this run. Premises in the chain came from that set's TTL,
        // so the in-set hit is the authoritative provenance label. The Solr
        // overload falls back to an unscoped lookup with a WARN if the in-set
        // query is empty (data drift between TTL and Solr).
        List<Mapping> assertedMappingsList =
                solrClient.querySubjectPredicateObjectIRI(subjectIRI, predicateIRI, objectIRI, sourceMappingSetId);

        if (assertedMappingsList != null && assertedMappingsList.size() > 0) {
            Mapping firstAssertedMapping = assertedMappingsList.get(0);
            inferredMapping = inferredMapping.populateFromMapping(firstAssertedMapping);
            InferredMapping.ChainRuleApplications chainRuleApplications = new InferredMapping.ChainRuleApplications(
                    Optional.of(ChainRulesEnum.ASSERTED));
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplications));
        }

        inferredMapping = updateSubject(inferredMapping, solrClient);
        inferredMapping = updatePredicate(inferredMapping, solrClient);
        inferredMapping = updateObject(inferredMapping, solrClient);

        return inferredMapping;
    }


    private static InferredMapping updateSubject(InferredMapping inferredMapping,
                                          Map<String, EntityDetails> iriToEntityDetails) {

        EntityDetails details =  iriToEntityDetails.get(inferredMapping.getSubjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setSubjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateSubject(InferredMapping inferredMapping, DataloadSolr solrClient) {

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                SUBJECT_IRI,
                inferredMapping.getSubjectIRI().asStringIRI(),
                SUBJECT_ID,
                SUBJECT_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setSubjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updatePredicate(InferredMapping inferredMapping,
                                          DataloadSolr solrClient) {

        String predicateIri = inferredMapping.getPredicateIRI().asStringIRI();
        Optional<String> predicateCurie = PrefixMap.toCurie(predicateIri);
        if (predicateCurie.isPresent()) {
            inferredMapping.setPredicateId(predicateCurie.get());
            return inferredMapping;
        }

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                PREDICATE_IRI,
                predicateIri,
                PREDICATE_ID,
                PREDICATE_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setPredicateId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setPredicateLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateObject(InferredMapping inferredMapping,
                                                DataloadSolr solrClient) {

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                OBJECT_IRI,
                inferredMapping.getObjectIRI().asStringIRI(),
                OBJECT_ID,
                OBJECT_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setObjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setObjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

}

