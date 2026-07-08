import type { MRT_SortingState } from "material-react-table";

// The user-facing "Sort by" choice. It writes the existing `?sort` param (the same one the results
// table's per-column sort popovers write), so the URL stays the single source of truth and the two
// controls can never disagree.
//
// "Best match" sends no sort at all, which is what lets the backend rank by relevance — i.e. by the
// provenance-led boost (ADR-0027). Any explicit Solr sort replaces `score` and would discard it.

export type SortMode = 'RELEVANCE' | 'CONFIDENCE' | 'NEWEST';

export const DEFAULT_SORT_MODE: SortMode = 'RELEVANCE';

export const SORT_MODE_LABELS: Record<SortMode, string> = {
    RELEVANCE: 'Best match',
    CONFIDENCE: 'Highest confidence',
    NEWEST: 'Most recent',
};

export const SORT_MODE_ORDER: SortMode[] = ['RELEVANCE', 'CONFIDENCE', 'NEWEST'];

/** The `?sort` tokens each mode writes. Best match writes none — relevance is the absence of a sort. */
const SORT_MODE_FIELDS: Record<SortMode, MRT_SortingState> = {
    RELEVANCE: [],
    CONFIDENCE: [{ id: 'confidence', desc: true }],
    NEWEST: [{ id: 'mapping_date', desc: true }],
};

export function sortModeToSorting(mode: SortMode): MRT_SortingState {
    return SORT_MODE_FIELDS[mode];
}

/**
 * Recover the mode from the URL `sort` tokens. A sort we don't offer — a per-column sort the user set
 * on the results table — reads back as "Best match", because the control has no way to show it. It
 * only takes effect if the user submits a new search, which resets the table view anyway.
 */
export function asSortMode(sortTokens: string[]): SortMode {
    if (sortTokens.length !== 1) {
        return DEFAULT_SORT_MODE;
    }
    const match = SORT_MODE_ORDER.find((mode) => {
        const fields = SORT_MODE_FIELDS[mode];
        if (fields.length !== 1) return false;
        const [field] = fields;
        return sortTokens[0] === (field.desc ? `-${field.id}` : field.id);
    });
    return match ?? DEFAULT_SORT_MODE;
}

/** The `?sort` params a mode contributes, in URL order. Empty for the default. */
export function sortModeToUrlParams(mode: SortMode): string[] {
    return sortModeToSorting(mode).map((field) => (field.desc ? `-${field.id}` : field.id));
}
