import { Navigate, useParams } from "react-router";
import { legacyTermToCurie } from "../../util/legacyTerm";

/**
 * OxO v1 term URL (ADR-0004): `/terms/<id>` → the subject-side results page for that term.
 *
 * External sites still link to v1's per-term page (the GWAS Catalog's "Trait in OXO" button), and
 * without a route here the SPA shell rendered an empty page for them. The id is accepted as v1 took
 * it — CURIE or OBO short form — and normalised by `legacyTermToCurie`. The redirect lands on the
 * ordinary `/search/<curie>` page rather than a both-sides term view: a term's page shows what the
 * term maps to, the same question the Search tab poses (ADR-0030); fanning out to the object side
 * would pile every text-mapping label that merely points at the term on top of its own mappings.
 *
 * `replace` keeps the dead v1 URL out of the history, so Back returns to the referring site.
 */
function LegacyTermRedirect() {
    const { id } = useParams<{ id: string }>();
    const curie = legacyTermToCurie(id);
    if (curie === null) {
        return <Navigate to="/" replace />;
    }
    return <Navigate to={`/search/${encodeURIComponent(curie)}`} replace />;
}

export default LegacyTermRedirect;
