package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

public class SortedField {
    private MappingEnum field;
    private SortOrderEnum order;


    public MappingEnum getField() {
        return field;
    }

    public void setField(MappingEnum field) {
        this.field = field;
    }

    public SortOrderEnum getOrder() {
        return order;
    }

    public void setOrder(SortOrderEnum order) {
        this.order = order;
    }
}
