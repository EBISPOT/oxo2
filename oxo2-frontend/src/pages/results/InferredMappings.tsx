import {useCallback, useMemo, useState} from "react";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_SortingState,
    useMaterialReactTable,
} from "material-react-table";
import {useQuery} from "@tanstack/react-query";
import {Link} from "react-router-dom";
import {mappingSetHref} from "../../util/mappingSetUrl";
import {fetchConfidenceByMappingIds} from "./MappingResultsSlice";
import {InferredMapping, Mapping} from "../../model/Mapping.ts";
import {EntityRefCell} from "../../components/mapping/EntityRefCell";
import {ColumnSortPopover, type SortFieldDef} from "../../components/mapping/ColumnSortPopover";
import {SortingContext} from "../../components/mapping/sortingContext";

// Per-column sort choices (id / label / iri). Unlike the Normal search results these
// fields are the InferredMapping property names rather than Solr field names, because
// asserted mappings are local data sorted client-side (see sortedAssertedMappings).
const SUBJECT_SORT_FIELDS: SortFieldDef[] = [
    {field: "subjectId", label: "ID"},
    {field: "subjectLabel", label: "Label"},
    {field: "subjectIri", label: "IRI"},
];
// InferredMapping predicates carry no label, only id + IRI.
const PREDICATE_SORT_FIELDS: SortFieldDef[] = [
    {field: "predicateId", label: "ID"},
    {field: "predicateIri", label: "IRI"},
];
const OBJECT_SORT_FIELDS: SortFieldDef[] = [
    {field: "objectId", label: "ID"},
    {field: "objectLabel", label: "Label"},
    {field: "objectIri", label: "IRI"},
];

// Locale-aware comparison; numeric:true keeps embedded numbers (e.g. ids) in natural order.
const compareValues = (left: unknown, right: unknown, descending?: boolean): number => {
    const comparison = String(left ?? "").localeCompare(String(right ?? ""), undefined, {
        numeric: true,
        sensitivity: "base",
    });
    return descending ? -comparison : comparison;
};

/**
 * The "Asserted Mappings" table on the Mapping Details page. Mirrors the Normal search
 * results layout — Subject / Predicate / Object each rendered as a stacked id › label ›
 * IRI cell with per-column sort popovers — but operates on the local
 * mapping.assertedMappings array (so sorting is client-side) and shows every row without
 * paging.
 */
function InferredMappings({ mapping }: { mapping: Mapping }) {
    const [sorting, setSorting] = useState<MRT_SortingState>([]);

    // Confidence is not in the precomputed premise blob (ADR-0028), but each premise carries its
    // mapping_id, and the asserted mapping's own doc carries confidence. Resolve them all in ONE
    // batched search (fetchConfidenceByMappingIds) and index by mapping_id for the column below.
    const premiseIds = useMemo(
        () => (mapping.assertedMappings ?? [])
            .map((premise) => premise.mappingId)
            .filter((id): id is string => !!id),
        [mapping.assertedMappings]
    );
    const { data: confidenceById } = useQuery({
        queryKey: ["assertedConfidence", premiseIds],
        queryFn: () => fetchConfidenceByMappingIds(premiseIds),
        enabled: premiseIds.length > 0,
        staleTime: 5 * 60 * 1000,
    });

    // The sort popovers compute the full next sort list themselves (single key per column
    // group, multi-column across groups); we just store it and re-sort below.
    const handleSortChange = useCallback((next: MRT_SortingState) => {
        setSorting(next);
    }, []);

    const sortedAssertedMappings = useMemo<InferredMapping[]>(() => {
        const rows = mapping.assertedMappings ?? [];
        if (sorting.length === 0) {
            return rows;
        }
        return [...rows].sort((left, right) => {
            for (const sort of sorting) {
                const field = sort.id as keyof InferredMapping;
                const comparison = compareValues(left[field], right[field], sort.desc);
                if (comparison !== 0) {
                    return comparison;
                }
            }
            return 0;
        });
    }, [mapping.assertedMappings, sorting]);

    const assertedMappingColumns = useMemo<MRT_ColumnDef<InferredMapping>[]>(
        () => [
            {
                id: "subject",
                accessorFn: (row) => row.subjectLabel,
                header: "Subject",
                size: 320,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Subject</span>
                        <ColumnSortPopover title="Subject" fields={SUBJECT_SORT_FIELDS} onApply={handleSortChange} />
                    </span>
                ),
                Cell: ({ row }) => (
                    <EntityRefCell
                        id={row.original.subjectId}
                        iri={row.original.subjectIri}
                        label={row.original.subjectLabel}
                        showOlsLink
                    />
                ),
            },
            {
                id: "predicate",
                accessorFn: (row) => row.predicateId,
                header: "Predicate",
                size: 240,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Predicate</span>
                        <ColumnSortPopover title="Predicate" fields={PREDICATE_SORT_FIELDS} onApply={handleSortChange} />
                    </span>
                ),
                Cell: ({ row }) => (
                    <EntityRefCell
                        id={row.original.predicateId}
                        iri={row.original.predicateIri}
                    />
                ),
            },
            {
                id: "object",
                accessorFn: (row) => row.objectLabel,
                header: "Object",
                size: 320,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Object</span>
                        <ColumnSortPopover title="Object" fields={OBJECT_SORT_FIELDS} onApply={handleSortChange} />
                    </span>
                ),
                Cell: ({ row }) => (
                    <EntityRefCell
                        id={row.original.objectId}
                        iri={row.original.objectIri}
                        label={row.original.objectLabel}
                        showOlsLink
                    />
                ),
            },
            // Confidence, resolved by the premise's mapping_id via the batched lookup. No value (many
            // asserted sets carry none) renders as an em dash, never a fabricated number.
            {
                id: "confidence",
                accessorFn: (row) => row.mappingId,
                header: "Mapping confidence",
                size: 150,
                Cell: ({ row }) => {
                    const mappingId = row.original.mappingId;
                    const confidence = mappingId ? confidenceById?.get(mappingId) : undefined;
                    return typeof confidence === "number"
                        ? <span>{confidence}</span>
                        : <span className="text-gray-400">—</span>;
                },
            },
            // The premise's mapping justification, carried directly in the precomputed blob (no lookup).
            {
                id: "mappingJustification",
                accessorFn: (row) => row.mappingJustification,
                header: "Mapping justification",
                size: 200,
                Cell: ({ row }) => (
                    <span className="break-all">{row.original.mappingJustification}</span>
                ),
            },
            // The premise's source set, linking to that set's detail page. An asserted premise only
            // carries its mapping_set_id (no title in the precomputed blob), so the id itself is the
            // link text — same id-only rendering the results table falls back to when a set has no title.
            {
                id: "mappingSet",
                accessorFn: (row) => row.mappingSetId,
                header: "Mapping set",
                size: 260,
                Cell: ({ row }) => {
                    const setId = row.original.mappingSetId;
                    if (!setId) {
                        return <span className="text-gray-400">—</span>;
                    }
                    return (
                        <Link
                            to={mappingSetHref(setId)}
                            className="break-all text-link-default hover:underline"
                            title="View mapping set details"
                        >
                            {setId}
                        </Link>
                    );
                },
            },
        ],
        [handleSortChange, confidenceById]
    );

    const assertedMappingsTable = useMaterialReactTable({
        columns: assertedMappingColumns,
        data: sortedAssertedMappings,
        enableColumnFilters: false,
        enableSorting: false, // sorting is driven by the per-column sort popovers
        manualSorting: true,
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        enableHiding: false,
        enableTopToolbar: false,
        enablePagination: false,
        muiTableBodyProps: {
            sx: {
                // stripe the rows, make even rows a darker color
                '& tr:nth-of-type(even) > td': {
                    backgroundColor: '#F3F4F6',
                },
            },
        },
        muiTableBodyCellProps: {
            sx: {
                verticalAlign: 'top',
            },
        },
        muiTableHeadCellProps: {
            sx: {
                fontWeight: 'bold',
                fontSize: '16px',
            },
        },
        state: {
            sorting,
        },
    });

    return (
        <div>
            <h1 className="section-subheading">Asserted Mappings</h1>
            <SortingContext.Provider value={sorting}>
                <MaterialReactTable table={assertedMappingsTable}/>
            </SortingContext.Provider>
        </div>
    );
}
export default InferredMappings;
