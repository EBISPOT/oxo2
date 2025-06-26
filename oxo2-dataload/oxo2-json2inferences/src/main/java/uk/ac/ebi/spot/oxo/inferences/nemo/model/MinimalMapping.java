package uk.ac.ebi.spot.oxo.inferences.nemo.model;

public class MinimalMapping implements  Comparable<MinimalMapping>{

    private String subjectIRI;
    private String predicateIRI;
    private String objectIRI;

    public MinimalMapping(String subjectIRI, String predicateIRI, String objectIRI) {
        this.objectIRI = objectIRI;
        this.predicateIRI = predicateIRI;
        this.subjectIRI = subjectIRI;
    }

    public String getObjectIRI() {
        return objectIRI;
    }

    public String getPredicateIRI() {
        return predicateIRI;
    }

    public String getSubjectIRI() {
        return subjectIRI;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinimalMapping)) return false;
        MinimalMapping that = (MinimalMapping) o;
        return subjectIRI.equals(that.subjectIRI)
                && predicateIRI.equals(that.predicateIRI)
                && objectIRI.equals(that.objectIRI);
    }

    @Override
    public int hashCode() {
        int result = subjectIRI.hashCode();
        result = 31 * result + predicateIRI.hashCode();
        result = 31 * result + objectIRI.hashCode();
        return result;
    }

    @Override
    public int compareTo(MinimalMapping o) {
        int compare = this.subjectIRI.compareTo(o.subjectIRI);
        if (compare != 0) return compare;
        compare = this.predicateIRI.compareTo(o.predicateIRI);
        if (compare != 0) return compare;
        return this.objectIRI.compareTo(o.objectIRI);
    }
}