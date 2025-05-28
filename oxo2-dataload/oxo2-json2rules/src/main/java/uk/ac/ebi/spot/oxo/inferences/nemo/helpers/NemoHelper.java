package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoChainRulesEnum;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.*;

public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    /**
     * @Todo: The same conclusion can appear multiple times. Currently we will loose different explanations for the same
     * conclusion. See: https://github.com/knowsys/nemo/issues/677.
     *
     * @param nemoInferences
     * @return
     */
    public static Set<InferredMapping> fromNemoInferencesToInferredMappings(NemoInferences nemoInferences) {
        long timeBegin = System.currentTimeMillis();
        Set<InferredMapping> inferredMappings = new HashSet<>();
        Set<NemoInferences.NemoInference> conclusionsWithPremisesAndRules = nemoInferences.getInferencesSet();
        Map<String, InferredMapping> premiseToInferredMapping = new HashMap<>();
        Map<String, InferredMapping> conclusionToInferredMapping = new HashMap<>();
        for (NemoInferences.NemoInference nemoInference  : conclusionsWithPremisesAndRules) {

//            if (nemoInference.getPremises() != null && nemoInference.getPremises().size() > 0)  {
                InferredMapping inferredMapping;
                if (premiseToInferredMapping.containsKey(nemoInference.getConclusion())) {
                    inferredMapping = premiseToInferredMapping.get(nemoInference.getConclusion());
                } else {
                    inferredMapping = new InferredMapping();
                    inferredMapping = populateInferredMapping(inferredMapping, nemoInference.getConclusion());
                }
                if (inferredMapping.isMappingToSelf()) {
                    continue;
                }

                Optional<NemoChainRulesEnum> nemoChainRulesEnum =
                        NemoChainRulesEnum.getChainRuleFromNemoRuleName(nemoInference.getRuleName());
                Optional<ChainRulesEnum> chainRulesEnum = nemoChainRulesEnum.map(
                        nemoRule -> ChainRulesEnum.valueOf(nemoRule.name()));

                InferredMapping.ChainRuleApplications chainRuleApplication = new InferredMapping.ChainRuleApplications(
                            nemoInference.getConclusion(), chainRulesEnum);
                List<InferredMapping> premises = new ArrayList<>();
                for (String premise : nemoInference.getPremises()) {
                    InferredMapping inferredMappingForPremise;
                    if (conclusionToInferredMapping.containsKey(premise)) {
                        inferredMappingForPremise = conclusionToInferredMapping.get(premise);
                    } else {
                        inferredMappingForPremise = new InferredMapping();
                        inferredMappingForPremise =
                                populateInferredMapping(inferredMappingForPremise, premise);
                    }

                    premises.add(inferredMappingForPremise);
                    if (premiseToInferredMapping.containsKey(premise)) {
                        InferredMapping existingPremise = premiseToInferredMapping.get(premise);
                        if (!existingPremise.equals(inferredMappingForPremise)) {
                        logger.error("Premise {} already exists in premiseToInferredMapping. Existing related inferred mappping is {}" +
                                " and the new one is {}. ", premise, existingPremise, inferredMappingForPremise);
                        }
                    }
                    premiseToInferredMapping.put(premise, inferredMappingForPremise);
                }
                chainRuleApplication.setPremises(premises);

                inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
                conclusionToInferredMapping.put(nemoInference.getConclusion(), inferredMapping);
                inferredMappings.add(inferredMapping);
            }
//        }

        long timeEnd = System.currentTimeMillis();
        logger.info("Time taken: {} s", (timeEnd - timeBegin)/1000);
        return inferredMappings;
    }

    private static boolean isValidMappingString(String mappingString) {
        if (mappingString == null || mappingString.isEmpty()) {
            return false;
        }
        String regex = "^mapping\\(\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*\\)$";
        boolean result = mappingString.matches(regex);
        logger.trace("Result of isValidMappingString for {} is {} ", mappingString, result);
        return result;
    }

    private static InferredMapping populateInferredMapping(InferredMapping inferredMapping, String mapping) {
        logger.trace("populateInferredMapping where inferredMapping = {} and mapping = {}", inferredMapping, mapping);
        if (!isValidMappingString(mapping)) {
            throw new IllegalArgumentException("Invalid mapping string: " + mapping);
        }

        String[] parts = mapping.substring(mapping.indexOf('<') + 1, mapping.lastIndexOf('>')).split(">\\s*,\\s*<");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Conclusion string must contain exactly three URIs: " + mapping);
        }

        inferredMapping.setSubjectIRI(new Uri(parts[0]));
        inferredMapping.setPredicateIRI(new Uri(parts[1]));
        inferredMapping.setObjectIRI(new Uri(parts[2]));

        inferredMapping.setMappingTool("OxO2 inference");
        inferredMapping.setMappingJustification(new EntityReference("SEMAPV:MappingChaining"));

        return inferredMapping;
    }


}

