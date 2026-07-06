import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { MRT_PaginationState } from "material-react-table";

const DEFAULT_PAGE_SIZE = 10;
// `page` is 1-based in the URL (page=1 is the first page) so shared links read naturally;
// internally MRT uses a 0-based pageIndex, so we convert on read/write.
const PAGE_PARAM = "page";
const SIZE_PARAM = "size";

function readPagination(params: URLSearchParams): MRT_PaginationState {
    const page = Number(params.get(PAGE_PARAM));
    const size = Number(params.get(SIZE_PARAM));
    return {
        pageIndex: Number.isFinite(page) && page >= 1 ? Math.floor(page) - 1 : 0,
        pageSize: Number.isFinite(size) && size >= 1 ? Math.floor(size) : DEFAULT_PAGE_SIZE,
    };
}

type PaginationUpdater = MRT_PaginationState | ((old: MRT_PaginationState) => MRT_PaginationState);

/**
 * Pagination state backed by the URL query string (`?page=` 1-based, `?size=`).
 *
 * The results table unmounts when the user opens a mapping's detail page; keeping the
 * page in the URL rather than component state means React Router restores it when they
 * hit Back — so they return to the page they left instead of page one. As a bonus the
 * page survives a refresh and can be shared. Other query params (mapping_set_id, from,
 * to, af, …) are preserved untouched.
 *
 * Returns the MRT-compatible `[pagination, setPagination]` pair. `setPagination` accepts
 * a value or an updater (matching MRT's `onPaginationChange`) and `replace`s the current
 * history entry, so paging within a result set doesn't pile up back-stack entries — Back
 * from the results leaves the search rather than stepping through every page visited.
 */
export function useUrlPagination(): [MRT_PaginationState, (updater: PaginationUpdater) => void] {
    const [searchParams, setSearchParams] = useSearchParams();

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

    const setPagination = useCallback(
        (updater: PaginationUpdater) => {
            setSearchParams(
                (previous) => {
                    const params = new URLSearchParams(previous);
                    const next = typeof updater === "function" ? updater(readPagination(params)) : updater;
                    // Keep the default page/size out of the URL so a first page of default size
                    // stays clean (…/search/disease rather than …/search/disease?page=1&size=10).
                    if (next.pageIndex > 0) {
                        params.set(PAGE_PARAM, String(next.pageIndex + 1));
                    } else {
                        params.delete(PAGE_PARAM);
                    }
                    if (next.pageSize !== DEFAULT_PAGE_SIZE) {
                        params.set(SIZE_PARAM, String(next.pageSize));
                    } else {
                        params.delete(SIZE_PARAM);
                    }
                    return params;
                },
                { replace: true }
            );
        },
        [setSearchParams]
    );

    return [pagination, setPagination];
}
