package uk.ac.ebi.spot.oxo.model.sssom;

public enum ChainRulesEnum {
    ASSERTED("Asserted", ""),
    RCE1_1("RCE1-1", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, ?p, ?c)"),
    RCE1_2("RCE1-2", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, ?p, ?c)"),
    RCE2_1("RCE2-1", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b), (?b, ?p, ?c)"),
    RCE2_2("RCE2-2", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b), (?b, ?p, ?c)"),
    T1("T1", "(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c)"),
    T2("T2", "(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c)"),
    T3("T3", "(?a, <http://www.w3.org/2002/07/owl#sameAs>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#sameAs>, ?b), (?b, <http://www.w3.org/2002/07/owl#sameAs>, ?c)"),
    T4("T4", "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)"),
    T5("T5", "(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c)"),
    T6("T6", "(?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c)"),
    T7("T7", "(?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c)"),
    T8("T8", "(?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c)"),
    T9("T9", "(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c)"),
    T10("T10", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c)"),
    T11("T11", "(?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c)"),
    RI1("RI1", "(?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?a) <- (?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b)"),
    RI2("RI2", "(?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?a) <- (?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b)"),
    RI3("RI3", "(?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b)"),
    RI4("RI4", "(?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b)"),
    RI5("RI5", "(?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b)"),
    RG1("RG1", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b) <- (?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c)"),
    RG2("RG2", "(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b) <- (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)"),
    RCE_N1("RCE-N1", "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)"),
    RCE_N2("RCE-N2", "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)"),
    RCE_N3("RCE-N3", "(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c)"),
    RCE_N4("RCE-N4", "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, b), (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c)");

    private final String name;
    private final String rule;

    ChainRulesEnum(String name, String rule) {
        this.name = name;
        this.rule = rule;
    }

    public String getName() {
        return name;
    }

    public String getRule() {
        return rule;
    }
}
