import {useCallback, useEffect, useMemo, useState} from "react";
import {useNavigate} from "react-router-dom";
import {emptyFacetedMapping, FacetedMapping, fetchMappings, fromJson} from "./MappingResultsSlice";
import {useQuery} from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_PaginationState,
    type MRT_SortingState,
    useMaterialReactTable,
} from 'material-react-table';
import {Mapping} from "../../model/Mapping.ts";
import {InferenceType, DEFAULT_INFERENCE_TYPES, INFERENCE_TYPE_ORDER, asInferenceType} from "../../model/InferenceType";
import {InferenceTypeBadge} from "../../components/mapping/InferenceTypeBadge";
import {InferenceTypeFilter} from "../../components/mapping/InferenceTypeFilter";
import {IconButton, Tooltip} from "@mui/material";
import {EyeIcon} from "@heroicons/react/24/solid";
import {EntityRefCell, CopyButton} from "../../components/mapping/EntityRefCell";
import {ColumnFilterPopover, type FilterFieldDef} from "../../components/mapping/ColumnFilterPopover";
import {ColumnSortPopover, type SortFieldDef} from "../../components/mapping/ColumnSortPopover";
import {SortingContext} from "../../components/mapping/sortingContext";

// Per-column filter inputs. Each `field` is a canonical Solr field name resolved by
// the backend MappingEnum; the values feed the columnFilters list (AND-combined).
const SUBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "subject_id", label: "Subject ID"},
    {field: "subject_label", label: "Subject label"},
    {field: "subject_iri", label: "Subject IRI"},
];
const PREDICATE_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "predicate_id", label: "Predicate ID"},
    {field: "predicate_label", label: "Predicate label"},
    {field: "predicate_iri", label: "Predicate IRI"},
];
const OBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "object_id", label: "Object ID"},
    {field: "object_label", label: "Object label"},
    {field: "object_iri", label: "Object IRI"},
];

// Per-column sort choices (id / label / iri). Same canonical Solr field names as the
// filters; label fields are routed to their `_str` docValue twin by the backend.
const SUBJECT_SORT_FIELDS: SortFieldDef[] = [
    {field: "subject_id", label: "ID"},
    {field: "subject_label", label: "Label"},
    {field: "subject_iri", label: "IRI"},
];
const PREDICATE_SORT_FIELDS: SortFieldDef[] = [
    {field: "predicate_id", label: "ID"},
    {field: "predicate_label", label: "Label"},
    {field: "predicate_iri", label: "IRI"},
];
const OBJECT_SORT_FIELDS: SortFieldDef[] = [
    {field: "object_id", label: "ID"},
    {field: "object_label", label: "Label"},
    {field: "object_iri", label: "IRI"},
];

// Same-SPO grouping helpers (ADR-0013). A grouped representative carries all its members (including
// itself) in groupMembers; an ungrouped row falls back to the single mapping.
function groupMembersOf(mapping: Mapping): Mapping[] {
    return mapping.groupMembers && mapping.groupMembers.length > 0 ? mapping.groupMembers : [mapping];
}

// Distinct inference types present in the group, in display order — for the stacked Type badges.
function groupInferenceTypes(mapping: Mapping): InferenceType[] {
    const members = groupMembersOf(mapping);
    return INFERENCE_TYPE_ORDER.filter(type => members.some(member => asInferenceType(member.inferenceType) === type));
}

// The shared value of a per-mapping field across the group, or null when members differ ("Multiple").
function sharedValue(mapping: Mapping, pick: (member: Mapping) => string | undefined): string | null {
    const members = groupMembersOf(mapping);
    const distinct = new Set(members.map(member => (pick(member) || '').trim()));
    return distinct.size <= 1 ? (pick(mapping) || '') : null;
}

// Deep-link to the flat Advanced view filtered to this exact triple (the "+N more" overflow target).
function advancedHrefForTriple(mapping: Mapping): string {
    const params = new URLSearchParams();
    params.append('af', `subject_id=${mapping.subjectId}`);
    params.append('af', `predicate_id=${mapping.predicateId}`);
    params.append('af', `object_id=${mapping.objectId}`);
    return `/search/_advanced?${params.toString()}`;
}

/**
 * Default ("Search" tab) results: a compact, readable table of Subject / Predicate /
 * Object (each an id › label › IRI cell) plus mapping justification, provider, and
 * set. Same-SPO mappings are collapsed into one expandable row (ADR-0013): the parent shows the
 * representative triple with the distinct inference types and a member count, and the row expands to
 * the underlying mappings. Field-level filtering is offered via per-column popovers; the Advanced tab
 * remains the home for exhaustive per-field filtering (see AdvancedResultsTable).
 */
export function NormalResultsTable({ queries, mappingSetIds, initialInferenceTypes = DEFAULT_INFERENCE_TYPES }:
    { queries: string[]; mappingSetIds: string[]; initialInferenceTypes?: InferenceType[] }) {
    const navigate = useNavigate();

    const [pagination, setPagination] = useState<MRT_PaginationState>({
        pageIndex: 0,
        pageSize: 10,
    });

    // Inline field filters live in their own state (Solr field name -> value). They are
    // ephemeral session state (not URL-synced); the Advanced tab owns persistent
    // field filtering. A short debounce keeps typing from hammering the backend.
    const [fieldFilters, setFieldFilters] = useState<Record<string, string>>({});
    const [debouncedFilters, setDebouncedFilters] = useState<Record<string, string>>({});
    const [sorting, setSorting] = useState<MRT_SortingState>([
        { id: 'subject_label', desc: false }
    ]);

    // Multi-select inference-type filter for the result rows (ADR-0011); defaults to
    // {Asserted, SSSOM inference}.
    const [inferenceTypes, setInferenceTypes] = useState<InferenceType[]>(initialInferenceTypes);

    const handleInferenceTypesChange = useCallback((next: InferenceType[]) => {
        setInferenceTypes(next);
        setPagination((previous) => ({ ...previous, pageIndex: 0 }));
    }, []);

    const handleFilterChange = useCallback((field: string, value: string) => {
        setFieldFilters((previous) => ({ ...previous, [field]: value }));
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedFilters(fieldFilters), 400);
        return () => clearTimeout(timer);
    }, [fieldFilters]);

    // A filter change resets to the first page so results aren't stranded on an
    // out-of-range page.
    useEffect(() => {
        setPagination((previous) => ({ ...previous, pageIndex: 0 }));
    }, [debouncedFilters]);

    const columnFiltersForBackend = useMemo(
        () => Object.entries(debouncedFilters)
            .filter(([, value]) => value && value.trim() !== "")
            .map(([id, value]) => ({ id, value: value.trim() })),
        [debouncedFilters]
    );

    // The sort popovers compute the full next sort list themselves (single key per
    // column group, multi-column across groups); we just store it and reset to the
    // first page so results aren't stranded on an out-of-range page.
    const handleSortChange = useCallback((next: MRT_SortingState) => {
        setSorting(next);
        setPagination((previous) => ({ ...previous, pageIndex: 0 }));
    }, []);

    const mappingSetIdsKey = mappingSetIds.join(",");
    const { data, isLoading, isError } = useQuery({
        queryKey: [
            "fetchMappings",
            queries,
            pagination.pageIndex,
            pagination.pageSize,
            columnFiltersForBackend,
            sorting,
            mappingSetIdsKey,
            inferenceTypes.join(","),
        ],
        queryFn: () =>
            fetchMappings(
                queries,
                pagination.pageIndex,
                pagination.pageSize,
                columnFiltersForBackend,
                sorting,
                mappingSetIds,
                undefined,
                inferenceTypes,
                true // group same-SPO mappings into one row (ADR-0013)
            ),
        staleTime: Infinity,
    });

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    const columns = useMemo<MRT_ColumnDef<Mapping>[]>(
        () => [
            {
                id: "subject_label",
                accessorFn: (row) => row.subjectLabel,
                header: "Subject",
                size: 320,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Subject</span>
                        <ColumnFilterPopover title="Subject" fields={SUBJECT_FILTER_FIELDS} onChange={handleFilterChange} />
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
                id: "predicate_label",
                accessorFn: (row) => row.predicateLabel,
                header: "Predicate",
                size: 240,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Predicate</span>
                        <ColumnFilterPopover title="Predicate" fields={PREDICATE_FILTER_FIELDS} onChange={handleFilterChange} />
                        <ColumnSortPopover title="Predicate" fields={PREDICATE_SORT_FIELDS} onApply={handleSortChange} />
                    </span>
                ),
                Cell: ({ row }) => (
                    <EntityRefCell
                        id={row.original.predicateId}
                        iri={row.original.predicateIri}
                        label={row.original.predicateLabel}
                        modifier={row.original.predicateModifier}
                    />
                ),
            },
            {
                id: "object_label",
                accessorFn: (row) => row.objectLabel,
                header: "Object",
                size: 320,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Object</span>
                        <ColumnFilterPopover title="Object" fields={OBJECT_FILTER_FIELDS} onChange={handleFilterChange} />
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
            {
                id: "mapping_justification",
                accessorFn: (row) => row.mappingJustification,
                header: "Mapping justification",
                enableSorting: false,
                size: 200,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Mapping justification</span>
                        <ColumnFilterPopover
                            title="Mapping justification"
                            fields={[{ field: "mapping_justification", label: "Mapping justification" }]}
                            onChange={handleFilterChange}
                        />
                    </span>
                ),
                Cell: ({ row }) => {
                    const shared = sharedValue(row.original, (member) => member.mappingJustification);
                    return shared === null
                        ? <span className="italic text-gray-500">Multiple</span>
                        : <span className="break-all">{shared}</span>;
                },
            },
            {
                id: "inference_type",
                accessorFn: (row) => row.inferenceType,
                header: "Type",
                enableSorting: false,
                size: 130,
                Cell: ({ row }) => {
                    const groupSize = row.original.groupSize ?? 1;
                    return (
                        <div className="flex flex-col gap-0.5">
                            <div className="flex flex-wrap gap-1">
                                {groupInferenceTypes(row.original).map((type) => (
                                    <InferenceTypeBadge key={type} value={type} />
                                ))}
                            </div>
                            {groupSize > 1 && (
                                <span className="text-xs text-gray-500">{groupSize} mappings</span>
                            )}
                        </div>
                    );
                },
            },
            {
                id: "mapping_provider",
                accessorFn: (row) => row.mappingProvider,
                header: "Mapping provider",
                enableSorting: false,
                size: 170,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Mapping provider</span>
                        <ColumnFilterPopover
                            title="Mapping provider"
                            fields={[{ field: "mapping_provider", label: "Mapping provider" }]}
                            onChange={handleFilterChange}
                        />
                    </span>
                ),
                Cell: ({ row }) => {
                    const shared = sharedValue(row.original, (member) => member.mappingProvider);
                    return shared === null
                        ? <span className="italic text-gray-500">Multiple</span>
                        : <span className="break-all">{shared}</span>;
                },
            },
            {
                id: "mapping_set",
                accessorFn: (row) => row.mappingSetTitle || row.mappingSetId,
                header: "Mapping set",
                enableSorting: false,
                size: 220,
                Cell: ({ row }) => {
                    // One row can span several sets; show "Multiple sets" rather than a misleading single set.
                    if (sharedValue(row.original, (member) => member.mappingSetId) === null) {
                        return <span className="italic text-gray-500">Multiple sets</span>;
                    }
                    return (
                        <div className="flex flex-col gap-0.5 min-w-0">
                            <span className="font-semibold break-words">{row.original.mappingSetTitle || "—"}</span>
                            {row.original.mappingSetId && (
                                <div className="flex items-start text-xs text-gray-500">
                                    <span className="break-all">{row.original.mappingSetId}</span>
                                    <CopyButton value={row.original.mappingSetId} title="Copy mapping set id" />
                                </div>
                            )}
                        </div>
                    );
                },
            },
        ],
        [handleFilterChange, handleSortChange]
    );

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        enableColumnOrdering: true,
        enableColumnFilters: false, // field filtering is driven by the per-column popovers
        enableSorting: false, // sorting is driven by the per-column sort popovers
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        manualPagination: true,
        manualSorting: true,
        // Same-SPO grouping (ADR-0013): only rows backing more than one mapping can expand; the detail
        // panel lists the underlying members.
        enableExpanding: true,
        getRowCanExpand: (row) => (row.original.groupSize ?? 1) > 1,
        renderDetailPanel: ({ row }) => {
            const members = groupMembersOf(row.original);
            const total = row.original.groupSize ?? members.length;
            const overflow = total - members.length;
            return (
                <div className="px-4 py-2">
                    <div className="text-sm font-medium mb-2">{total} mappings for this triple</div>
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-left text-gray-500">
                                <th className="py-1 pr-4 font-medium">Type</th>
                                <th className="py-1 pr-4 font-medium">Mapping justification</th>
                                <th className="py-1 pr-4 font-medium">Mapping provider</th>
                                <th className="py-1 pr-4 font-medium">Mapping set</th>
                                <th className="py-1" />
                            </tr>
                        </thead>
                        <tbody>
                            {members.map((member, index) => (
                                <tr key={member.mappingId || index} className="border-t border-gray-200 align-top">
                                    <td className="py-1 pr-4"><InferenceTypeBadge value={member.inferenceType} /></td>
                                    <td className="py-1 pr-4 break-all">{member.mappingJustification}</td>
                                    <td className="py-1 pr-4 break-all">{member.mappingProvider}</td>
                                    <td className="py-1 pr-4">
                                        <div className="font-semibold break-words">{member.mappingSetTitle || "—"}</div>
                                        {member.mappingSetId && (
                                            <div className="text-xs text-gray-500 break-all">{member.mappingSetId}</div>
                                        )}
                                    </td>
                                    <td className="py-1">
                                        <Tooltip title="View mapping details">
                                            <IconButton
                                                size="small"
                                                onClick={() => navigate(`/mapping/${encodeURIComponent(member.mappingId)}`, { state: { mapping: member } })}
                                            >
                                                <EyeIcon className="h-4 w-4" />
                                            </IconButton>
                                        </Tooltip>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {overflow > 0 && (
                        <div className="mt-2 text-sm">
                            <a className="text-[#d4522c] hover:underline" href={advancedHrefForTriple(row.original)}>
                                +{overflow} more — view all in Advanced search
                            </a>
                        </div>
                    )}
                </div>
            );
        },
        enableRowActions: true,
        positionActionsColumn: 'last',
        renderRowActions: ({ row }) => (
            <Tooltip title="View mapping details">
                <IconButton
                    size="small"
                    onClick={() => {
                        const mapping = row.original;
                        navigate(`/mapping/${encodeURIComponent(mapping.mappingId)}`, { state: { mapping } });
                    }}
                >
                    <EyeIcon className="h-5 w-5" />
                </IconButton>
            </Tooltip>
        ),
        displayColumnDefOptions: {
            'mrt-row-actions': { header: '', size: 60 },
        },
        muiTablePaperProps: {
            sx: {
                '& .MuiBox-root:has(.MuiTablePagination-root)': {
                    justifyContent: 'center',
                    width: '100%',
                    display: 'flex'
                }
            },
        },
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
        rowCount: (mappingResults?.totalElements) ?? 0,
        state: {
            isLoading,
            pagination,
            showAlertBanner: isError,
            sorting
        },
        enableHiding: true,
        enableTopToolbar: true,
        renderTopToolbarCustomActions: () => (
            <InferenceTypeFilter value={inferenceTypes} onChange={handleInferenceTypesChange} />
        ),
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
            <SortingContext.Provider value={sorting}>
                <MaterialReactTable table={table} />
            </SortingContext.Provider>
        </>
    );
}
