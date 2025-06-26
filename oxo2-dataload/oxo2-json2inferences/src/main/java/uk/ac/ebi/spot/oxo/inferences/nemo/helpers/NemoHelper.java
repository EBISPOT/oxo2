package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.*;
import uk.ac.ebi.spot.oxo.model.sssom.*;

import java.util.*;

public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    public static Set<InferredMapping> fromNemoInferencesToInferredMappings(
            NemoInferences nemoInferences,
            Map<MinimalMapping, List<Mapping>> assertedMappings,
            Map<String, EntityDetails> iriToEntityDetails) {

        Set<InferredMapping> inferredMappings = new HashSet<>();

        nemoInferences.getFinalConclusion().forEach(finalConclusion -> {
            InferredMapping inferredMapping = determineInferencesLeadingToConclusion(nemoInferences,
                    finalConclusion, assertedMappings, iriToEntityDetails);
            inferredMappings.add(inferredMapping);
        });

        return inferredMappings;
    }

    private static InferredMapping determineInferencesLeadingToConclusion(
            NemoInferences nemoInferences,
            String conclusion,
            Map<MinimalMapping, List<Mapping>> assertedMappings,
            Map<String, EntityDetails> iriToEntityDetails) {

        InferredMapping inferredMapping = createInferredMapping(conclusion, assertedMappings, iriToEntityDetails);

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
                return inferredMapping;
            }

            InferredMapping.ChainRuleApplications chainRuleApplication =
                    new InferredMapping.ChainRuleApplications(chainRulesEnum);

            inferredMapping.setMappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL);
            inferredMapping.setMappingJustification(new EntityReference(
                    OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION));
            inferredMapping.setMappingSetId(OXOInferenceConstants.OXO_MAPPING_SET_ID);

            List<InferredMapping> premises = new ArrayList<>();

            nemoInference.getPremises().forEach(premise -> {
                InferredMapping premiseAsInferredMapping = determineInferencesLeadingToConclusion(
                        nemoInferences, premise, assertedMappings, iriToEntityDetails
                );
                premises.add(premiseAsInferredMapping);
            }
            );

            chainRuleApplication.setPremises(premises);
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
        }
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
            String mapping,
            Map<MinimalMapping, List<Mapping>> assertedMappings,
            Map<String, EntityDetails> iriToEntityDetails) {

        InferredMapping inferredMapping = new InferredMapping();
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

        MinimalMapping minimalMapping = new MinimalMapping(parts[0], parts[1], parts[2]);
        List<Mapping> assertedMappingsList = assertedMappings.get(minimalMapping);

        if (assertedMappingsList != null) {
            Mapping firstAssertedMapping = assertedMappingsList.get(0);
            inferredMapping = inferredMapping.populateFromMapping(firstAssertedMapping);
            InferredMapping.ChainRuleApplications chainRuleApplications = new InferredMapping.ChainRuleApplications(
                    Optional.of(ChainRulesEnum.ASSERTED));
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplications));
        }

        inferredMapping = updateSubject(inferredMapping, iriToEntityDetails);
        inferredMapping = updatePredicate(inferredMapping, iriToEntityDetails);
        inferredMapping = updateObject(inferredMapping, iriToEntityDetails);

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

    private static InferredMapping updatePredicate(InferredMapping inferredMapping,
                                          Map<String, EntityDetails> iriToEntityDetails) {

        EntityDetails details =  iriToEntityDetails.get(inferredMapping.getPredicateIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setPredicateId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setPredicateLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateObject(InferredMapping inferredMapping,
                                         Map<String, EntityDetails> iriToEntityDetails) {

        EntityDetails details =  iriToEntityDetails.get(inferredMapping.getObjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setObjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setObjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

}

