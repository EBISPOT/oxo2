package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/MappingSet/">MappingSet</a>
 */
public class MappingSet {
    String comment;
    PrefixMap curieMap;


    SortedSet<EntityReference> subjectMatchField = new TreeSet<>();
    SortedSet<EntityReference> objectMatchField = new TreeSet<>();
}
