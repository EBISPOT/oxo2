package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

@Schema(description = "A single sort key.")
public class SortedField {
    @Schema(description = "Field to sort by.")
    private MappingEnum id;
    @Schema(description = "Sort descending when true, ascending when false.", defaultValue = "false")
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
