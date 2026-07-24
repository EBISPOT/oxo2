import {useMemo, useState} from "react";
import {useNavigate} from "react-router-dom";
import {DEFAULT_INFERENCE_TYPES} from "../../model/InferenceType";
import {InferenceTypeBadge} from "../../components/mapping/InferenceTypeBadge";
import {InferenceTypeFilterPopover} from "../../components/mapping/InferenceTypeFilterPopover";
import {AdvancedFieldQuery} from "../../model/Search";
import {emptyMappingPage, MappingPage, fetchMappings, fromJson} from "./MappingResultsSlice";
import {useQuery} from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_SortingState,
    useMaterialReactTable,
} from 'material-react-table';
import {Mapping} from "../../model/Mapping.ts";
import {formatMappingJustification} from "../../model/MappingJustification";
import {useUrlPagination, useUrlSorting, useUrlInferenceTypes, useUrlColumnFilters} from "../../util/tableUrlState";

// Default sort for the Advanced table. Module-level so its reference is stable across
// renders (useUrlSorting memoises against it).
const DEFAULT_SORTING: MRT_SortingState = [{ id: 'subject_id', desc: false }];

/**
 * Advanced ("Advanced" tab) results: the full wide multi-column table, kept exactly
 * as it was before the default-search redesign. Advanced search is the power-user
 * surface, so it shows every column (including ones the compact default view drops,
 * e.g. mapping tool and licence) and offers inline per-column filtering. The default
 * search results use the compact NormalResultsTable instead.
 */
export function AdvancedResultsTable({
    advancedFieldQueries,
    mappingSetIds,
}: {
    advancedFieldQueries: AdvancedFieldQuery[];
    mappingSetIds: string[];
}) {
    const navigate = useNavigate();

    // View state (page, size, sort, column filters, inference-type filter) is held in the
    // URL so returning from a mapping's detail page restores what the user was looking at,
    // rather than resetting to the first page with default sort and filters. Sorting and
    // column-filter changes keep the current page (matching this table's prior behaviour);
    // only the inference-type filter resets to the first page — the URL hooks fold that
    // reset into the same write.
    const [pagination, setPagination] = useUrlPagination();
    const [sorting, setSorting] = useUrlSorting(DEFAULT_SORTING, false);
    const [columnFilters, setColumnFilters] = useUrlColumnFilters(false);

    // Multi-select inference-type filter for the result rows (ADR-0011); defaults to
    // {Asserted, SSSOM inference}. Mirrors the control in NormalResultsTable.
    const [inferenceTypes, setInferenceTypes] = useUrlInferenceTypes(DEFAULT_INFERENCE_TYPES, true);

    const mappingSetIdsKey = mappingSetIds.join(",");
    const advancedKey = JSON.stringify(advancedFieldQueries);
    const { data, isLoading, isError } = useQuery({
        queryKey: [
            "fetchMappings",
            [],
            pagination.pageIndex,
            pagination.pageSize,
            columnFilters,
            sorting,
            mappingSetIdsKey,
            advancedKey,
            inferenceTypes.join(","),
        ],
        queryFn: () =>
            fetchMappings(
                [],
                pagination.pageIndex,
                pagination.pageSize,
                columnFilters,
                sorting,
                mappingSetIds,
                advancedFieldQueries.length > 0 ? advancedFieldQueries : undefined,
                inferenceTypes
            ),
        staleTime: Infinity,
    });

    const mappingResults: MappingPage = data ? fromJson(data) : emptyMappingPage;

    const columns = useMemo<MRT_ColumnDef<Mapping>[]>(
        () => [
            {
                accessorKey: "subjectId",
                header: "Subject Id",
            },
            {
                accessorKey: "subjectIri",
                header: "Subject IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "subjectLabel",
                header: "Subject Label",
            },
            {
                accessorKey: "predicateId",
                header: "Predicate Id",
            },
            {
                accessorKey: "predicateIri",
                header: "Predicate IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "objectId",
                header: "Object Id",
            },
            {
                accessorKey: "objectIri",
                header: "Object IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "objectLabel",
                header: "Object Label",
            },
            {
                accessorKey: "mappingJustification",
                header: "Mapping Justification",
                Cell: ({ row }) => (
                    <span title={row.original.mappingJustification}>
                        {formatMappingJustification(row.original.mappingJustification)}
                    </span>
                ),
            },
            {
                // Inference type (ADR-0011). Filtering of this field is driven by the single-select
                // popover in the column header, not the per-column filter input, so the inline
                // filter is disabled.
                accessorKey: "inferenceType",
                header: "Type",
                enableColumnFilter: false,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Type</span>
                        <InferenceTypeFilterPopover value={inferenceTypes} onChange={setInferenceTypes} />
                    </span>
                ),
                Cell: ({ row }) => <InferenceTypeBadge value={row.original.inferenceType} />,
            },
            {
                accessorKey: "mappingProvider",
                header: "Mapping Provider"
            },
            {
                accessorKey: "mappingSetId",
                header: "Mapping Set Id"
            },
            {
                accessorKey: "mappingTool",
                header: "Mapping Tool",
            },
            {
                accessorKey: "license",
                header: "License",
            }
        ],
        [inferenceTypes, setInferenceTypes]
    );

    const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>({
        license: false,
        mappingJustification: false,
        mappingProvider: false,
        mappingSetId: false,
        mappingTool: false,
        objectIri: true,
        predicateIri: true,
        subjectIri: true,
    })

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        enableColumnOrdering: true,
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: true,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        manualPagination: true, //turn off built-in client-side pagination
        manualFiltering: true, // Enable manual filtering
        manualSorting: true,
        muiTablePaperProps: {
            sx: {
                '& .MuiBox-root:has(.MuiTablePagination-root)': {
                    justifyContent: 'center',
                    width: '100%',
                    display: 'flex'
                }
            },
        },
        //give loading spinner somewhere to go while loading
        muiTableBodyProps: {
            children: isLoading ? (
                <tr style={{ height: '200px' }}>
                    <td />
                </tr>
            ) : undefined,
            sx: {
                //stripe the rows, make odd rows a darker color
                '& tr:nth-of-type(even) > td': {
                    backgroundColor: '#F3F4F6',
                },
            },
        },
        muiTableHeadCellProps: {
            //simple styling with the `sx` prop, works just like a style prop in this example
            sx: {
                fontWeight: 'bold',
                fontSize: '16px',
            },
        },
        muiToolbarAlertBannerProps: isError
            ? {
                color: 'error',
                children: 'Error loading data',
            }
            : undefined,
        paginationDisplayMode: 'pages',
        muiPaginationProps: {
            rowsPerPageOptions: [5, 10, 20, 30, 50],
            boundaryCount: 1,
            color: 'primary',
            size: 'medium',
            siblingCount: 1,
        },
        onPaginationChange: setPagination,
        onColumnFiltersChange: setColumnFilters,
        onSortingChange: setSorting,
        rowCount: (mappingResults?.totalElements) ?? 0,
        state: {
            isLoading,
            pagination,
            columnFilters, // Pass filter state to table
            showAlertBanner: isError,
            columnVisibility,
            sorting
        },
        initialState: {
            showColumnFilters: true
        },
        onColumnVisibilityChange: setColumnVisibility,
        enableHiding: true,
        enableTopToolbar: true, // Show toolbar so user can access column visibility menu
        renderTopToolbarCustomActions: () => (
            <div className="flex items-center">
                {!isLoading && !isError && (
                    <span className="text-sm text-tertiary">
                        {/* Advanced search never groups (groupBySpo stays off), so totalElements
                            is always a plain mapping count. */}
                        {(mappingResults?.totalElements ?? 0).toLocaleString()}
                        {' '}
                        {mappingResults?.totalElements === 1 ? "mapping" : "mappings"}
                    </span>
                )}
            </div>
        ),
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => {
                const mapping = row.original;
                navigate(`/mapping/${mapping.mappingId}`, { state: { mapping } });
            },
            style: { cursor: 'pointer' }
        })
    });

    return (
        <>
            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-default w-10 h-10 animate-spin" role="status">
                        <span className="sr-only">Loading...</span>
                    </div>
                </div>
            )}
            <MaterialReactTable table={table}/>
        </>
    );
}
