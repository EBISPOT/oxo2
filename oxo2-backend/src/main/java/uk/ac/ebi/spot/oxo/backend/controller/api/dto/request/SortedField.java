package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

public class SortedField {
    private MappingEnum id;
    private boolean desc;

    public boolean isDesc() {
        return desc;
    }

    public void setDesc(boolean desc) {
        this.desc = desc;
    }

    public MappingEnum getId() {
        return id;
    }

    public void setId(MappingEnum id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "SortedField{" +
                "desc=" + desc +
                ", id=" + id +
                '}';
    }
}
