package uk.ac.ebi.spot.oxo.inferences.nemo.model;

import java.util.Optional;

public enum NemoChainRulesEnum {
    ASSERTED(
            "Asserted", 
            "Asserted"),
    RCE1_1(
            "RCE1-1", 
            "mapping(?a, ?p, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), mapping(?b, ?p, ?c) ."),
    RCE1_2(
            "RCE1-2", 
            "mapping(?a, ?p, ?c) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), mapping(?b, ?p, ?c) ."),
    RCE2_1(
            "RCE2-1", 
            "mapping(?a, ?p, ?c) :- mapping(?a, ?p, ?b), mapping(?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c) ."),
    RCE2_2(
            "RCE2-2", 
            "mapping(?a, ?p, ?c) :- mapping(?a, ?p, ?b), mapping(?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) ."),
    T1(
            "T1", 
            "mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), mapping(?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c) ."),
    T2(
            "T2", 
            "mapping(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), mapping(?b, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c) ."),
    T3(
            "T3", 
            "mapping(?a, <http://www.w3.org/2002/07/owl#sameAs>, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#sameAs>, ?b), mapping(?b, <http://www.w3.org/2002/07/owl#sameAs>, ?c) ."),
    T4(
            "T4", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) :- mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), mapping(?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) ."),
    T5(
            "T5", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) :- mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?b), mapping(?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) ."),
    T6(
            "T6", 
            "mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b), mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c) ."),
    T7(
            "T7", 
            "mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b), mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c) ."),
    T8(
            "T8", 
            "mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b), mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c) ."),
    T9(
            "T9", 
            "mapping(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b), mapping(?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c) ."),
    T10(
            "T10", 
            "mapping(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), mapping(?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) ."),
    T11(
            "T11", 
            "mapping(?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b), mapping(?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c) ."),
    RI1(
            "RI1", 
            "mapping(?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?a) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b) ."),
    RI2(
            "RI2", 
            "mapping(?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?a) :- mapping(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b) ."),
    RI3(
            "RI3", 
            "mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?a) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b) ."),
    RI4(
            "RI4", 
            "mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?a) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b) ."),
    RI5(
            "RI5", 
            "mapping(?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?a) :- mapping(?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b) ."),
    RG1(
            "RG1", 
            "mapping(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b) ."),
    RG2(
            "RG2", 
            "mapping(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b) :- mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b) ."),
    RCE_N1(
            "RCE-N1", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), mapping(?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) ."),
    RCE_N2(
            "RCE-N2", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) :- mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), mapping(?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) ."),
    RCE_N3(
            "RCE-N3", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), mapping(?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) ."),
    RCE_N4(
            "RCE-N4", 
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) :- mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, b), mapping(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c) .");
    private final String name;
    private final String nemoRuleName; // Renamed field

    NemoChainRulesEnum(String name, String nemoRuleName) { // Updated constructor
        this.name = name;
        this.nemoRuleName = nemoRuleName;
    }

    public String getName() {
        return name;
    }


    public String getNemoRuleName() { // Updated getter
        return nemoRuleName;
    }

    public static Optional<NemoChainRulesEnum> getChainRuleFromNemoRuleName(String nemoRuleName) {
        for (NemoChainRulesEnum rule : NemoChainRulesEnum.values()) {
            if (rule.getNemoRuleName().equals(nemoRuleName)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty(); // Return null if no match is found
    }

}
