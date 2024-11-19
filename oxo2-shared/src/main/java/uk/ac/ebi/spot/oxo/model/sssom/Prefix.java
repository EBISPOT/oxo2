package uk.ac.ebi.spot.oxo.model.sssom;

public class Prefix {
    private String name;
    private String url;

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
