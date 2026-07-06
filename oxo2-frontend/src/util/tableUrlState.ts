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
 * Two correctness constraints shape this module:
 *   1. react-router's setSearchParams reads the current params from the render closure,
 *      so two setSearchParams calls in one event handler would clobber each other. Every
 *      setter therefore performs a single atomic write, folding the page reset into that
 *      same write (`resetPageOnChange`) rather than issuing a second call.
 *   2. setSearchParams is a fresh function on every URL change (react-router keys it on
 *      the current params). If the setters inherited that instability they would land in
 *      the columns useMemo deps and remount the table headers — snapping open sort/filter
 *      popovers shut — on every page or filter change. So `update` is kept referentially
 *      stable via a ref, and the setters read the current value from refs; every returned
 *      setter is stable for the component's lifetime.
 */

const DEFAULT_PAGE_SIZE = 10;
const PAGE_PARAM = "page";
const SIZE_PARAM = "size";
const SORT_PARAM = "sort";
const TYPE_PARAM = "type";
const FILTER_PARAM = "filter";

type Updater<T> = T | ((old: T) => T);
// Separator for turning a getAll() array into a stable useMemo dependency key (so the memo
// doesn't recompute just because getAll returns a fresh array each render). A NUL character
// can't occur in a URL-decoded query value, so distinct arrays never collide to one key.
const NUL = "\u0000";

/** Shared reader + referentially-stable atomic writer over the URL query string. */
function useUrlState() {
    const [searchParams, setSearchParams] = useSearchParams();
    // react-router recreates setSearchParams on every URL change; keep the latest in a ref
    // so `update` (and thus every setter built on it) stays referentially stable.
    const setSearchParamsRef = useRef(setSearchParams);
    setSearchParamsRef.current = setSearchParams;

    const update = useCallback((mutate: (params: URLSearchParams) => void, resetPage = false) => {
        setSearchParamsRef.current(
            (previous) => {
                const params = new URLSearchParams(previous);
                mutate(params);
                // Resetting the page keeps a narrowed result set from stranding the user on an
                // out-of-range page; folding it into this write keeps the whole change atomic.
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

export function useUrlPagination(): [MRT_PaginationState, (updater: Updater<MRT_PaginationState>) => void] {
    const { searchParams, update } = useUrlState();
    const rawPage = searchParams.get(PAGE_PARAM);
    const rawSize = searchParams.get(SIZE_PARAM);
    const pagination = useMemo<MRT_PaginationState>(() => {
        const page = Number(rawPage);
        const size = Number(rawSize);
        return {
            pageIndex: Number.isFinite(page) && page >= 1 ? Math.floor(page) - 1 : 0,
            pageSize: Number.isFinite(size) && size >= 1 ? Math.floor(size) : DEFAULT_PAGE_SIZE,
        };
    }, [rawPage, rawSize]);

    const paginationRef = useRef(pagination);
    paginationRef.current = pagination;
    const setPagination = useCallback(
        (updater: Updater<MRT_PaginationState>) => {
            const next = typeof updater === "function" ? updater(paginationRef.current) : updater;
            update((params) => {
                if (next.pageIndex > 0) params.set(PAGE_PARAM, String(next.pageIndex + 1));
                else params.delete(PAGE_PARAM);
                if (next.pageSize !== DEFAULT_PAGE_SIZE) params.set(SIZE_PARAM, String(next.pageSize));
                else params.delete(SIZE_PARAM);
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

    const sortingRef = useRef(sorting);
    sortingRef.current = sorting;
    const setSorting = useCallback(
        (updater: Updater<MRT_SortingState>) => {
            const next = typeof updater === "function" ? updater(sortingRef.current) : updater;
            update((params) => writeSorting(params, next, defaultSorting), resetPageOnChange);
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

    const columnFiltersRef = useRef(columnFilters);
    columnFiltersRef.current = columnFilters;
    const setColumnFilters = useCallback(
        (updater: Updater<MRT_ColumnFiltersState>) => {
            const next = typeof updater === "function" ? updater(columnFiltersRef.current) : updater;
            update((params) => writeColumnFilters(params, next), resetPageOnChange);
        },
        [update, resetPageOnChange]
    );

    return [columnFilters, setColumnFilters];
}
