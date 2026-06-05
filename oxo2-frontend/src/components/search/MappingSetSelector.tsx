import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_RowSelectionState,
    useMaterialReactTable,
} from "material-react-table";
import { fetchMappingSets } from "../../pages/results/MappingSetsSlice";
import { MappingSet } from "../../model/MappingSet";

/**
 * Mapping-set picker for the search forms. There are hundreds of mapping sets, so this
 * table is deliberately split out from the Search component and wrapped in React.memo:
 * typing in the search box (or an advanced field) re-renders Search on every keystroke,
 * and without this isolation each keystroke would re-render every row here. Selection is
 * fully controlled by the parent (`selectedIds`), so as long as the parent keeps that
 * array and `onSelectionChange` referentially stable, memo skips this subtree entirely
 * while the user types. Rows are virtualised so even a genuine render mounts only the
 * visible handful rather than all hundreds.
 */
function idsToSelectionState(ids: string[]): MRT_RowSelectionState {
    return ids.reduce<MRT_RowSelectionState>((accumulator, id) => {
        accumulator[id] = true;
        return accumulator;
    }, {});
}

function MappingSetSelectorInner({
    selectedIds,
    onSelectionChange,
}: {
    selectedIds: string[];
    onSelectionChange: (ids: string[]) => void;
}) {
    const { data: mappingSets = [], isLoading: mappingSetsLoading } = useQuery({
        queryKey: ["fetchMappingSets"],
        queryFn: fetchMappingSets,
        staleTime: Infinity,
    });

    const rowSelection = useMemo<MRT_RowSelectionState>(
        () => idsToSelectionState(selectedIds),
        [selectedIds]
    );

    const columns = useMemo<MRT_ColumnDef<MappingSet>[]>(
        () => [
            { accessorKey: "mappingSetTitle", header: "Title", size: 200, filterFn: "contains" },
            {
                // Asserted vs inferred set, backed by the denormalised is_inferred flag (ADR-0008).
                // Client-side select filter over the already-fetched sets: default (no selection) =
                // all, matching the tri-state default in the results view.
                id: "isInferred",
                accessorFn: (row) => (row.isInferred ? "Inferred" : "Asserted"),
                header: "Type",
                size: 120,
                filterVariant: "select",
                filterSelectOptions: ["Asserted", "Inferred"],
                Cell: ({ row }) =>
                    row.original.isInferred ? (
                        <span className="inline-block rounded px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-800">
                            Inferred
                        </span>
                    ) : (
                        <span className="inline-block rounded px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-700">
                            Asserted
                        </span>
                    ),
            },
            {
                accessorKey: "mappingSetId",
                header: "Mapping Set Id",
                size: 250,
                filterFn: "contains",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "mappingSetDescription",
                header: "Description",
                size: 350,
                filterFn: "contains",
                Cell: ({ cell }) => (
                    <div style={{ whiteSpace: "normal", wordBreak: "break-word" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "creatorLabel",
                header: "Creator",
                size: 150,
                // creatorLabel is a string[]; filter against the same comma-joined text the
                // cell renders so a substring matches any one creator regardless of order.
                filterFn: (row, _columnId, filterValue) =>
                    (row.original.creatorLabel ?? [])
                        .join(", ")
                        .toLowerCase()
                        .includes(String(filterValue).toLowerCase()),
                Cell: ({ cell }) => <span>{(cell.getValue<string[]>() ?? []).join(", ")}</span>,
            },
            { accessorKey: "mappingProvider", header: "Provider", size: 150, filterFn: "contains" },
        ],
        []
    );

    const table = useMaterialReactTable<MappingSet>({
        columns,
        data: mappingSets,
        enableRowSelection: true,
        enableMultiRowSelection: true,
        enableSelectAll: true,
        enableRowVirtualization: true,
        getRowId: (row) => row.mappingSetId,
        state: { rowSelection, isLoading: mappingSetsLoading, density: "compact" },
        onRowSelectionChange: (updater) => {
            const next = typeof updater === "function" ? updater(rowSelection) : updater;
            const selected = Object.keys(next).filter((id) => next[id]);
            onSelectionChange(selected);
        },
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => row.toggleSelected(),
            sx: { cursor: "pointer" },
        }),
        muiTableContainerProps: { sx: { maxHeight: "20rem" } },
        enableTopToolbar: false,
        enableBottomToolbar: false,
        enableColumnActions: false,
        // Client-side per-column filtering over the already-fetched mapping sets. The
        // top toolbar is hidden, so there is no filter-toggle button; show the filter
        // input row by default (initialState.showColumnFilters) so every field is
        // filterable straight away. Filter state stays internal to MRT, so typing here
        // never re-renders the parent Search component (see the React.memo note above).
        enableColumnFilters: true,
        enableSorting: true,
        enablePagination: false,
        enableStickyHeader: true,
        muiTableHeadCellProps: {
            sx: { fontWeight: "bold", fontSize: "14px" },
        },
        initialState: { density: "compact", showColumnFilters: true },
    });

    return <MaterialReactTable table={table} />;
}

export const MappingSetSelector = React.memo(MappingSetSelectorInner);
