package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Prefix {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("url")
    private final String url;

    public Prefix(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}
