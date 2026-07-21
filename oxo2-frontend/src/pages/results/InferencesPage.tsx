import { useLocation } from "react-router-dom";
import MappingSetDetails from "./MappingSetDetails";

const INFERENCES_BASE = "https://www.ebi.ac.uk/oxo2/inferences";

/**
 * Resolution surface for inferred mapping sets (ADR-0012). The route /inferences resolves the
 * single cross-set SSSOM set; /inferences/<encoded source id> resolves a per-source inferred set. The
 * full mapping_set_id is reconstructed from the raw path tail so a percent-encoded source id
 * survives as one path segment. Rendering is delegated to the shared MappingSetDetails, which is the
 * same detail surface the general /mapping-set route uses.
 */
function InferencesPage() {
    const location = useLocation();
    const marker = "/inferences";
    const markerIndex = location.pathname.indexOf(marker);
    const tail = markerIndex >= 0 ? location.pathname.slice(markerIndex + marker.length) : "";
    const mappingSetId = INFERENCES_BASE + tail;

    return <MappingSetDetails mappingSetId={mappingSetId} />;
}

export default InferencesPage;
