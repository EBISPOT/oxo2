package uk.ac.ebi.spot.oxo.dataload.solr;

public class EntityDetails {
    private String curie;
    private String iri;
    private String label;

    public boolean isCuriePresent() {
        if (curie == null || curie.isBlank())
            return false;
        else return true;
    }

    public boolean isIRIPresent() {
        if (iri == null || iri.isBlank())
            return false;
        else return true;
    }

    public boolean isLabelPresent() {
        if (label == null || label.isBlank())
            return false;
        else return true;
    }

    public boolean areAllFieldsPresent() {
        return isCuriePresent() && isIRIPresent() && isLabelPresent();
    }

    public String getCurie() {
        return curie;
    }

    public void setCurie(String curie) {
        this.curie = curie;
    }

    public String getIri() {
        return iri;
    }

    public void setIri(String iri) {
        this.iri = iri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
