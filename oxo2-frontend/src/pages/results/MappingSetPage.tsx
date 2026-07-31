import { useSearchParams } from "react-router";
import MappingSetDetails from "./MappingSetDetails";

/**
 * Route component for /mapping-set?id=<full IRI>. The id is a query parameter, not a path segment,
 * because a mapping_set_id is a full IRI and the encoded slashes an IRI path variable would need are
 * rejected upstream (the same rationale as the backend by-id endpoint). useSearchParams decodes the
 * value, so it is the plain IRI here.
 */
function MappingSetPage() {
    const [searchParams] = useSearchParams();
    const mappingSetId = searchParams.get("id") ?? "";

    if (!mappingSetId) {
        return (
            <div className="container mx-auto px-4 py-8">
                <div className="alert-info">No mapping set id provided.</div>
            </div>
        );
    }

    return <MappingSetDetails mappingSetId={mappingSetId} />;
}

export default MappingSetPage;
