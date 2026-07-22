import React, { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_RowSelectionState,
    useMaterialReactTable,
} from "material-react-table";
import { fetchMappingSets } from "../../pages/results/MappingSetsSlice";
import { MappingSet } from "../../model/MappingSet";
import { CORPUS_LABELS } from "../../model/MappingSetCategory";

/**
 * Mapping-set picker for the search forms, split into two tables driven by the set's curation
 * category (ADR-0027, ADR-0038): a "Curated sets" table and an "Ontologies" table that leads with
 * the ontology's name and CURIE prefix. Selection is unified across both tables — each table controls
 * only its own rows and merges the result back into the single `selectedIds` list the parent owns.
 *
 * There are hundreds of mapping sets, so this is split out from the Search component and wrapped in
 * React.memo: typing in the search box re-renders Search on every keystroke, and without this
 * isolation each keystroke would re-render every row here. As long as the parent keeps `selectedIds`
 * and `onSelectionChange` referentially stable, memo skips this subtree entirely while the user
 * types. Rows are virtualised so even a genuine render mounts only the visible handful.
 */
function idsToSelectionState(ids: string[]): MRT_RowSelectionState {
    return ids.reduce<MRT_RowSelectionState>((accumulator, id) => {
        accumulator[id] = true;
        return accumulator;
    }, {});
}

// Curated-sets columns. The inference Type column is intentionally dropped: there is only ever one
// inferred set (folded in here, as it carries no category), so the column would be a near-constant.
const CURATED_COLUMNS: MRT_ColumnDef<MappingSet>[] = [
    {
        accessorKey: "mappingSetTitle",
        header: "Title",
        size: 220,
        filterFn: "contains",
        Cell: ({ cell }) => (
            <div style={{ whiteSpace: "normal", wordBreak: "break-word" }}>
                {cell.getValue<string>()}
            </div>
        ),
    },
    {
        accessorKey: "mappingSetId",
        header: "Mapping Set Id",
        size: 260,
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
        size: 340,
        filterFn: "contains",
        Cell: ({ cell }) => (
            <div style={{ whiteSpace: "normal", wordBreak: "break-word" }}>
                {cell.getValue<string>()}
            </div>
        ),
    },
    { accessorKey: "mappingProvider", header: "Provider", size: 150, filterFn: "contains" },
];

// Ontologies columns: lead with the two ontology-specific fields promoted from the OLS `other`
// block (ADR-0038). Deliberately lean — title/creator/provider are redundant or empty for OLS sets.
const ONTOLOGY_COLUMNS: MRT_ColumnDef<MappingSet>[] = [
    { accessorKey: "ontology", header: "Ontology", size: 340, filterFn: "contains" },
    { accessorKey: "prefix", header: "Prefix", size: 160, filterFn: "contains" },
];

/**
 * Build one selectable MRT over a category's sets. `rowSelection` is derived from the parent's
 * unified `selectedIds` narrowed to this table's rows; a change reports back only this table's
 * selected ids, which the parent merges with the other table's.
 */
function useMappingSetTable(
    data: MappingSet[],
    columns: MRT_ColumnDef<MappingSet>[],
    selectedIds: string[],
    isLoading: boolean,
    initialSortId: string,
    onScopedSelectionChange: (idsSelectedInThisTable: string[]) => void,
) {
    const rowIds = useMemo(() => new Set(data.map((set) => set.mappingSetId)), [data]);
    const rowSelection = useMemo<MRT_RowSelectionState>(
        () => idsToSelectionState(selectedIds.filter((id) => rowIds.has(id))),
        [selectedIds, rowIds]
    );

    return useMaterialReactTable<MappingSet>({
        columns,
        data,
        enableRowSelection: true,
        enableMultiRowSelection: true,
        enableSelectAll: true,
        enableRowVirtualization: true,
        getRowId: (row) => row.mappingSetId,
        state: { rowSelection, isLoading, density: "compact" },
        onRowSelectionChange: (updater) => {
            const next = typeof updater === "function" ? updater(rowSelection) : updater;
            onScopedSelectionChange(Object.keys(next).filter((id) => next[id]));
        },
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => row.toggleSelected(),
            sx: { cursor: "pointer" },
        }),
        muiTableContainerProps: { sx: { maxHeight: "18rem" } },
        enableTopToolbar: false,
        enableBottomToolbar: false,
        enableColumnActions: false,
        // Client-side per-column filtering over the already-fetched sets; the filter row shows by
        // default (no top toolbar to toggle it). Filter state stays internal to MRT, so typing here
        // never re-renders the parent Search (see the React.memo note above).
        enableColumnFilters: true,
        enableSorting: true,
        enablePagination: false,
        enableStickyHeader: true,
        muiTableHeadCellProps: { sx: { fontWeight: "bold", fontSize: "14px" } },
        initialState: {
            density: "compact",
            showColumnFilters: true,
            sorting: [{ id: initialSortId, desc: false }],
        },
    });
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
        // Wrap in a closure so React Query's QueryFunctionContext is not passed as the
        // `inferenceType` argument (it is not an array, so fetchMappingSets would throw on .forEach).
        queryFn: () => fetchMappingSets(),
        staleTime: Infinity,
    });

    // Ontology sets go to their own table; everything else (curated sets plus the uncategorised
    // synthetic inferences set) goes to the curated table.
    const { ontologySets, curatedSets } = useMemo(() => {
        const ontology: MappingSet[] = [];
        const curated: MappingSet[] = [];
        for (const set of mappingSets) {
            (set.mappingSetCategory === "ONTOLOGY" ? ontology : curated).push(set);
        }
        return { ontologySets: ontology, curatedSets: curated };
    }, [mappingSets]);

    const ontologyIds = useMemo(
        () => new Set(ontologySets.map((set) => set.mappingSetId)),
        [ontologySets]
    );
    const curatedIds = useMemo(
        () => new Set(curatedSets.map((set) => set.mappingSetId)),
        [curatedSets]
    );

    // Merge a table's scoped selection back with the ids owned by the other table, so the parent
    // keeps one unified list across both tables.
    const onCuratedSelection = useCallback(
        (idsInTable: string[]) =>
            onSelectionChange([
                ...selectedIds.filter((id) => !curatedIds.has(id)),
                ...idsInTable,
            ]),
        [onSelectionChange, selectedIds, curatedIds]
    );
    const onOntologySelection = useCallback(
        (idsInTable: string[]) =>
            onSelectionChange([
                ...selectedIds.filter((id) => !ontologyIds.has(id)),
                ...idsInTable,
            ]),
        [onSelectionChange, selectedIds, ontologyIds]
    );

    const curatedTable = useMappingSetTable(
        curatedSets, CURATED_COLUMNS, selectedIds, mappingSetsLoading, "mappingSetTitle", onCuratedSelection
    );
    const ontologyTable = useMappingSetTable(
        ontologySets, ONTOLOGY_COLUMNS, selectedIds, mappingSetsLoading, "ontology", onOntologySelection
    );

    return (
        <div className="space-y-4">
            <section>
                <h4 className="text-sm font-semibold text-secondary mb-1">{CORPUS_LABELS.CURATED}</h4>
                <MaterialReactTable table={curatedTable} />
            </section>
            <section>
                <h4 className="text-sm font-semibold text-secondary mb-1">{CORPUS_LABELS.ONTOLOGY}</h4>
                <MaterialReactTable table={ontologyTable} />
            </section>
        </div>
    );
}

export const MappingSetSelector = React.memo(MappingSetSelectorInner);
