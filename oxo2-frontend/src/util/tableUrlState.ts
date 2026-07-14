import { useCallback, useMemo, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import type { MRT_ColumnFiltersState, MRT_PaginationState, MRT_SortingState } from "material-react-table";
import { INFERENCE_TYPE_LABELS, InferenceType } from "../model/InferenceType";

/**
 * Results-table view state (page, size, sort, inference-type filter, field filters) held
 * in the URL query string rather than component state.
 *
 * The results table unmounts when the user opens a mapping's detail page; keeping the
 * view state in the URL means React Router restores all of it on Back — the user returns
 * to the page, sort, and filters they left, not a reset first page. As a bonus the view
 * survives a refresh and can be shared.
 *
 * Encoding (all defaults are omitted so an untouched search stays a clean URL):
 *   ?page=  1-based page number   (internally MRT uses a 0-based pageIndex)
 *   ?size=  page size
 *   ?sort=  repeated; `field` ascending, `-field` descending, order = sort precedence;
 *           a lone `sort=` means "explicitly no sort" (distinct from the default sort)
 *   ?type=  repeated inference-type code, lowercased (omitted when all types selected)
 *   ?filter= repeated `field=value` (value may contain `=`; split on the first one)
 *
 * Two properties are load-bearing, not incidental:
 *   - One write per action. A change to a facet (sort/filter/type) also resets the page,
 *     so a narrowed result set can't strand the user on an out-of-range page. Both happen
 *     in a single write (`resetPageOnChange`): react-router derives an updater's `previous`
 *     from the render closure rather than chaining synchronous calls, so a second write in
 *     the same handler would silently drop the first — one atomic write is the only correct
 *     shape regardless.
 *   - Stable setters. react-router returns a fresh setSearchParams on every URL change. The
 *     setters must not inherit that instability: they feed the table's memoised column defs,
 *     and a changing identity would remount the headers (closing any open popover) on every
 *     page or filter change. We stabilise the writer with one latest-value ref — the standard
 *     idiom for a stable callback that reads current state — and derive each functional
 *     updater's previous value from the live params, so no per-facet refs are needed.
 */

const DEFAULT_PAGE_SIZE = 10;
const PAGE_PARAM = "page";
const SIZE_PARAM = "size";
const SORT_PARAM = "sort";
const TYPE_PARAM = "type";
const FILTER_PARAM = "filter";
/** Fields whose filter value was picked from a suggestion, so matches EXACTly (ADR-0034). */
const EXACT_PARAM = "fx";

type Updater<T> = T | ((old: T) => T);
const applyUpdater = <T,>(updater: Updater<T>, current: T): T =>
    typeof updater === "function" ? (updater as (old: T) => T)(current) : updater;

// Separator for turning a getAll() array into a stable useMemo dependency key (so the memo
// doesn't recompute just because getAll returns a fresh array each render). A NUL character
// can't occur in a URL-decoded query value, so distinct arrays never collide to one key.
const NUL = "\u0000";

/** Shared reader + a referentially-stable atomic writer over the URL query string. */
function useUrlState() {
    const [searchParams, setSearchParams] = useSearchParams();
    // react-router recreates setSearchParams on every URL change; hold the latest in a ref so
    // `update` (and every setter built on it) stays referentially stable for the component's life.
    const setSearchParamsRef = useRef(setSearchParams);
    setSearchParamsRef.current = setSearchParams;

    const update = useCallback((mutate: (params: URLSearchParams) => void, resetPage = false) => {
        setSearchParamsRef.current(
            (previous) => {
                const params = new URLSearchParams(previous);
                mutate(params); // sees the live params, so a functional updater can read its own "old" value
                if (resetPage) {
                    params.delete(PAGE_PARAM);
                }
                return params;
            },
            { replace: true }
        );
    }, []);

    return { searchParams, update };
}

// ---------- pagination ----------

function readPagination(pageValue: string | null, sizeValue: string | null): MRT_PaginationState {
    const page = Number(pageValue);
    const size = Number(sizeValue);
    return {
        pageIndex: Number.isFinite(page) && page >= 1 ? Math.floor(page) - 1 : 0,
        pageSize: Number.isFinite(size) && size >= 1 ? Math.floor(size) : DEFAULT_PAGE_SIZE,
    };
}

function writePagination(params: URLSearchParams, next: MRT_PaginationState): void {
    if (next.pageIndex > 0) params.set(PAGE_PARAM, String(next.pageIndex + 1));
    else params.delete(PAGE_PARAM);
    if (next.pageSize !== DEFAULT_PAGE_SIZE) params.set(SIZE_PARAM, String(next.pageSize));
    else params.delete(SIZE_PARAM);
}

export function useUrlPagination(): [MRT_PaginationState, (updater: Updater<MRT_PaginationState>) => void] {
    const { searchParams, update } = useUrlState();
    const rawPage = searchParams.get(PAGE_PARAM);
    const rawSize = searchParams.get(SIZE_PARAM);
    const pagination = useMemo(() => readPagination(rawPage, rawSize), [rawPage, rawSize]);

    const setPagination = useCallback(
        (updater: Updater<MRT_PaginationState>) => {
            update((params) => {
                const next = applyUpdater(updater, readPagination(params.get(PAGE_PARAM), params.get(SIZE_PARAM)));
                writePagination(params, next);
            });
        },
        [update]
    );

    return [pagination, setPagination];
}

// ---------- sorting ----------

function sortingEquals(a: MRT_SortingState, b: MRT_SortingState): boolean {
    return a.length === b.length && a.every((sort, index) => sort.id === b[index].id && sort.desc === b[index].desc);
}

function readSorting(raw: string[], defaultSorting: MRT_SortingState): MRT_SortingState {
    if (raw.length === 0) return defaultSorting;
    if (raw.length === 1 && raw[0] === "") return []; // explicit "no sort" sentinel
    return raw
        .filter((token) => token !== "")
        .map((token) => (token.startsWith("-") ? { id: token.slice(1), desc: true } : { id: token, desc: false }));
}

function writeSorting(params: URLSearchParams, next: MRT_SortingState, defaultSorting: MRT_SortingState): void {
    params.delete(SORT_PARAM);
    if (sortingEquals(next, defaultSorting)) return; // default: omit
    if (next.length === 0) {
        params.append(SORT_PARAM, ""); // explicitly cleared, ≠ default
        return;
    }
    next.forEach((sort) => params.append(SORT_PARAM, sort.desc ? `-${sort.id}` : sort.id));
}

/**
 * @param defaultSorting must be a stable (module-level) reference.
 * @param resetPageOnChange whether changing the sort resets to the first page (mirrors
 *        the table's existing behaviour: the compact table resets, the Advanced table does not).
 */
export function useUrlSorting(
    defaultSorting: MRT_SortingState,
    resetPageOnChange: boolean
): [MRT_SortingState, (updater: Updater<MRT_SortingState>) => void] {
    const { searchParams, update } = useUrlState();
    const rawSort = searchParams.getAll(SORT_PARAM);
    const sortKey = rawSort.join(NUL);
    // Keyed on sortKey (not the fresh getAll array) so it only recomputes when the URL changes.
    const sorting = useMemo(() => readSorting(rawSort, defaultSorting), [sortKey, defaultSorting]);

    const setSorting = useCallback(
        (updater: Updater<MRT_SortingState>) => {
            update((params) => {
                const next = applyUpdater(updater, readSorting(params.getAll(SORT_PARAM), defaultSorting));
                writeSorting(params, next, defaultSorting);
            }, resetPageOnChange);
        },
        [update, defaultSorting, resetPageOnChange]
    );

    return [sorting, setSorting];
}

// ---------- inference-type filter ----------

function sameTypeSet(a: InferenceType[], b: InferenceType[]): boolean {
    if (a.length !== b.length) return false;
    const setB = new Set(b);
    return a.every((type) => setB.has(type));
}

function readInferenceTypes(raw: string[], defaultTypes: InferenceType[]): InferenceType[] {
    const parsed = raw
        .map((value) => value.toUpperCase())
        .filter((value): value is InferenceType => value in INFERENCE_TYPE_LABELS);
    return parsed.length > 0 ? parsed : defaultTypes;
}

function writeInferenceTypes(params: URLSearchParams, next: InferenceType[], defaultTypes: InferenceType[]): void {
    params.delete(TYPE_PARAM);
    // An empty selection also means "all"; both collapse to the default and stay out of the URL.
    if (next.length === 0 || sameTypeSet(next, defaultTypes)) return;
    next.forEach((type) => params.append(TYPE_PARAM, type.toLowerCase()));
}

/**
 * @param defaultTypes must be a stable (module-level) reference.
 * @param resetPageOnChange whether changing the filter resets to the first page.
 */
export function useUrlInferenceTypes(
    defaultTypes: InferenceType[],
    resetPageOnChange: boolean
): [InferenceType[], (next: InferenceType[]) => void] {
    const { searchParams, update } = useUrlState();
    const rawTypes = searchParams.getAll(TYPE_PARAM);
    const typeKey = rawTypes.join(NUL);
    const inferenceTypes = useMemo(() => readInferenceTypes(rawTypes, defaultTypes), [typeKey, defaultTypes]);

    const setInferenceTypes = useCallback(
        (next: InferenceType[]) => update((params) => writeInferenceTypes(params, next, defaultTypes), resetPageOnChange),
        [update, defaultTypes, resetPageOnChange]
    );

    return [inferenceTypes, setInferenceTypes];
}

// ---------- field filters ----------

function nonEmptyEntries(filters: Record<string, string>): Array<[string, string]> {
    return Object.entries(filters)
        .filter(([, value]) => value != null && value.trim() !== "")
        .sort(([a], [b]) => a.localeCompare(b));
}

/** True when two filter maps are equal ignoring empty values and key order. */
export function fieldFiltersEqual(a: Record<string, string>, b: Record<string, string>): boolean {
    const left = nonEmptyEntries(a);
    const right = nonEmptyEntries(b);
    return (
        left.length === right.length &&
        left.every(([key, value], index) => right[index][0] === key && right[index][1].trim() === value.trim())
    );
}

function readFieldFilters(raw: string[]): Record<string, string> {
    const filters: Record<string, string> = {};
    raw.forEach((entry) => {
        const separator = entry.indexOf("=");
        if (separator <= 0) return;
        const field = entry.slice(0, separator);
        const value = entry.slice(separator + 1);
        if (value.trim() !== "") filters[field] = value;
    });
    return filters;
}

function writeFieldFilters(params: URLSearchParams, filters: Record<string, string>): void {
    params.delete(FILTER_PARAM);
    nonEmptyEntries(filters).forEach(([field, value]) => params.append(FILTER_PARAM, `${field}=${value}`));
}

/** Field filters as a `field -> value` map (the compact table's inline filters). Always resets the page. */
export function useUrlFieldFilters(): [Record<string, string>, (filters: Record<string, string>) => void] {
    const { searchParams, update } = useUrlState();
    const rawFilters = searchParams.getAll(FILTER_PARAM);
    const filterKey = rawFilters.join(NUL);
    const filters = useMemo(() => readFieldFilters(rawFilters), [filterKey]);

    const setFilters = useCallback(
        (nextFilters: Record<string, string>) => update((params) => writeFieldFilters(params, nextFilters), true),
        [update]
    );

    return [filters, setFilters];
}

// ---------- exact-match filters (ADR-0034) ----------

/**
 * Which field filters were PICKED from a suggestion rather than typed.
 *
 * A picked value came out of the index and is unambiguous, so it filters EXACTly; a typed value is a
 * fragment, so it keeps the existing "contains" behaviour. Carried as its own repeatable `fx=<field>`
 * param rather than by extending the `filter=<field>=<value>` encoding — that encoding is shared with
 * the Advanced table's column filters, and widening it would ripple somewhere this feature has no
 * business touching. Bookmarkable like every other bit of table state here.
 */
export function useUrlExactFilters(): [Set<string>, (fields: Set<string>) => void] {
    const { searchParams, update } = useUrlState();
    const rawExact = searchParams.getAll(EXACT_PARAM);
    const exactKey = rawExact.join(NUL);
    const exact = useMemo(() => new Set(rawExact.filter((field) => field.trim() !== "")), [exactKey]);

    const setExact = useCallback(
        (fields: Set<string>) => update((params) => {
            params.delete(EXACT_PARAM);
            [...fields].filter((field) => field.trim() !== "")
                .forEach((field) => params.append(EXACT_PARAM, field));
        }, true),
        [update]
    );

    return [exact, setExact];
}

// ---------- column filters (MRT-shaped, for the Advanced table) ----------

function readColumnFilters(raw: string[]): MRT_ColumnFiltersState {
    const filters: MRT_ColumnFiltersState = [];
    raw.forEach((entry) => {
        const separator = entry.indexOf("=");
        if (separator <= 0) return;
        const value = entry.slice(separator + 1);
        if (value.trim() !== "") filters.push({ id: entry.slice(0, separator), value });
    });
    return filters;
}

function writeColumnFilters(params: URLSearchParams, next: MRT_ColumnFiltersState): void {
    params.delete(FILTER_PARAM);
    next
        .filter((filter) => filter.value != null && String(filter.value).trim() !== "")
        .forEach((filter) => params.append(FILTER_PARAM, `${filter.id}=${String(filter.value)}`));
}

/**
 * @param resetPageOnChange whether changing a filter resets to the first page (the
 *        Advanced table preserves its page, matching its existing behaviour).
 */
export function useUrlColumnFilters(
    resetPageOnChange: boolean
): [MRT_ColumnFiltersState, (updater: Updater<MRT_ColumnFiltersState>) => void] {
    const { searchParams, update } = useUrlState();
    const rawFilters = searchParams.getAll(FILTER_PARAM);
    const filterKey = rawFilters.join(NUL);
    const columnFilters = useMemo(() => readColumnFilters(rawFilters), [filterKey]);

    const setColumnFilters = useCallback(
        (updater: Updater<MRT_ColumnFiltersState>) => {
            update((params) => {
                const next = applyUpdater(updater, readColumnFilters(params.getAll(FILTER_PARAM)));
                writeColumnFilters(params, next);
            }, resetPageOnChange);
        },
        [update, resetPageOnChange]
    );

    return [columnFilters, setColumnFilters];
}
