import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Link, useNavigate} from "react-router-dom";
import {mappingSetHref} from "../../util/mappingSetUrl";
import {emptyMappingPage, MappingPage, fetchMappings, exportMappings, fromJson, buildSearchRequest}
    from "./MappingResultsSlice";
import {useQuery} from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_SortingState,
    useMaterialReactTable,
} from 'material-react-table';
import {useUrlPagination, useUrlSorting, useUrlInferenceTypes, useUrlFieldFilters, useUrlExactFilters,
    useUrlWeakPredicates, fieldFiltersEqual} from "../../util/tableUrlState";
import {Mapping} from "../../model/Mapping.ts";
import {InferenceType, DEFAULT_INFERENCE_TYPES, INFERENCE_TYPE_ORDER, asInferenceType} from "../../model/InferenceType";
import {LabelMatchMode, DEFAULT_LABEL_MATCH} from "../../model/LabelMatchMode";
import {CorpusMode, DEFAULT_CORPUS, corpusToRequest} from "../../model/MappingSetCategory";
import {InferenceTypeBadge} from "../../components/mapping/InferenceTypeBadge";
import {InferenceTypeFilterPopover} from "../../components/mapping/InferenceTypeFilterPopover";
import {WeakPredicateFilterPopover} from "../../components/mapping/WeakPredicateFilterPopover";
import {IconButton, Tooltip} from "@mui/material";
import {EyeIcon, ArrowDownTrayIcon} from "@heroicons/react/24/solid";
import {EntityRefCell, CopyButton} from "../../components/mapping/EntityRefCell";
import {ColumnFilterPopover, type FilterFieldDef} from "../../components/mapping/ColumnFilterPopover";
import {ColumnSortPopover, type SortFieldDef} from "../../components/mapping/ColumnSortPopover";
import {SortMode, SORT_MODE_LABELS, SORT_MODE_ORDER, sortModeToSorting, sortModeFromSorting}
    from "../../model/SortMode";
import {SortingContext} from "../../components/mapping/sortingContext";

// Per-column filter inputs. Each `field` is a canonical Solr field name resolved by
// the backend MappingEnum; the values feed the columnFilters list (AND-combined).
//
// `suggest: "contextual"` gives a column a typeahead faceted over the LIVE search (ADR-0034), so it
// only ever offers values present in the rows on screen, each with the count it would leave. The
// SUBJECT column deliberately has none: a normal search is already subject-side (ADR-0030), so every
// row's subject is something the user just searched for — there is nothing to narrow.
const SUBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "subject_id", label: "Subject ID"},
    {field: "subject_label", label: "Subject label"},
    {field: "subject_iri", label: "Subject IRI"},
];
const PREDICATE_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "predicate_id", label: "Predicate ID", suggest: "contextual"},
    {field: "predicate_label", label: "Predicate label", suggest: "contextual"},
    {field: "predicate_iri", label: "Predicate IRI", suggest: "contextual"},
];
const OBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "object_id", label: "Object ID", suggest: "contextual"},
    {field: "object_label", label: "Object label", suggest: "contextual"},
    {field: "object_iri", label: "Object IRI", suggest: "contextual"},
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

// Set helpers for the exact-filter set (ADR-0034). New Sets, not mutations: the set is React state
// held in the URL, and mutating it in place would not re-render.
function withField(fields: Set<string>, field: string): Set<string> {
    const next = new Set(fields);
    next.add(field);
    return next;
}

function withoutField(fields: Set<string>, field: string): Set<string> {
    if (!fields.has(field)) {
        return fields;
    }
    const next = new Set(fields);
    next.delete(field);
    return next;
}

// Default sort for the compact table: none, i.e. relevance — the provenance-led ranking (ADR-0027).
// This is deliberately empty rather than a field: an explicit Solr sort *replaces* `score`, so the
// previous `subject_label asc` default silently discarded the ranking and ordered alphabetically.
// The backend names `score desc` when no sort is sent. Module-level so its reference is stable across
// renders (useUrlSorting memoises against it).
const DEFAULT_SORTING: MRT_SortingState = [];

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
export function NormalResultsTable({ queries, mappingSetIds, subjectPrefixes = [], objectPrefixes = [],
    initialInferenceTypes = DEFAULT_INFERENCE_TYPES, labelMatch = DEFAULT_LABEL_MATCH,
    corpus = DEFAULT_CORPUS, groupBySpo = true, variant = "search" }:
    { queries: string[]; mappingSetIds: string[]; subjectPrefixes?: string[]; objectPrefixes?: string[];
      initialInferenceTypes?: InferenceType[]; labelMatch?: LabelMatchMode; corpus?: CorpusMode;
      // Collapse same-SPO mappings into one expandable row (ADR-0013/0023). Default on for a normal
      // search; a set-detail view passes false — scoped to one set the collapse merges almost
      // nothing yet runs an unbounded CollapsingQParser allocation that OOMs Solr on large sets.
      groupBySpo?: boolean;
      // Which column set to render. "search" is the default full set. "set-detail" is the
      // single-set page: Type and Mapping set are dropped (every row shares the set — and, since the
      // set-detail view is ungrouped, a single inference type — so both columns are constant noise)
      // and Mapping confidence is shown in their place. (Mapping provider is hidden by default in
      // both views — see initialState — but stays revealable via the columns menu.)
      variant?: "search" | "set-detail" }) {
    const navigate = useNavigate();
    const isSetDetail = variant === "set-detail";
    const [isExporting, setIsExporting] = useState(false);

    // View state (page, size, sort, inference-type filter, field filters) is held in the
    // URL so returning from a mapping's detail page restores exactly what the user was
    // looking at, rather than resetting to the first page with default sort and filters.
    // Changing the sort or a filter resets to the first page (as before) — the URL hooks
    // fold that reset into the same write.
    const [pagination, setPagination] = useUrlPagination();
    const [sorting, setSorting] = useUrlSorting(DEFAULT_SORTING, true);
    const [inferenceTypes, setInferenceTypes] = useUrlInferenceTypes(initialInferenceTypes, true);
    const [urlFilters, setUrlFilters] = useUrlFieldFilters();
    // The two "also show" predicate checkboxes (ADR-0035). Hidden by default; ticking one changes
    // which rows exist at all, so it resets to page 1 like any other filter.
    const [weakPredicates, setWeakPredicates] = useUrlWeakPredicates(true);

    // The filter inputs keep their own local state for responsive typing (seeded once from
    // the URL so a restored filter shows in the inputs); a short debounce copies them to
    // the URL, which is the applied layer that drives the backend query. The debounced
    // write only fires when the inputs actually differ from what the URL already holds, so
    // mounting with a restored filter neither refetches nor clobbers the restored page.
    const [fieldFilters, setFieldFilters] = useState<Record<string, string>>(() => urlFilters);
    const initialFieldFilters = useRef(urlFilters).current;

    // Which fields were PICKED from a suggestion rather than typed (ADR-0034). A picked value came
    // out of the index and is unambiguous, so it filters EXACTly; a typed fragment stays "contains".
    const [exactFilters, setExactFilters] = useUrlExactFilters();

    const handleFilterChange = useCallback((field: string, value: string) => {
        setFieldFilters((previous) => ({ ...previous, [field]: value }));
        // Typing over a picked value makes it a fragment again, so it must go back to "contains" —
        // otherwise the box would keep matching exactly while the user edits it, and match nothing.
        setExactFilters(withoutField(exactFilters, field));
    }, [exactFilters, setExactFilters]);

    const handleFilterPick = useCallback((field: string, value: string) => {
        setFieldFilters((previous) => ({ ...previous, [field]: value }));
        setExactFilters(withField(exactFilters, field));
    }, [exactFilters, setExactFilters]);

    useEffect(() => {
        if (fieldFiltersEqual(fieldFilters, urlFilters)) {
            return;
        }
        const timer = setTimeout(() => setUrlFilters(fieldFilters), 400);
        return () => clearTimeout(timer);
    }, [fieldFilters, urlFilters, setUrlFilters]);

    const exactFiltersKey = [...exactFilters].sort().join(",");
    const columnFiltersForBackend = useMemo(
        () => Object.entries(urlFilters)
            .filter(([, value]) => value && value.trim() !== "")
            .map(([id, value]) => ({
                id,
                value: value.trim(),
                match: exactFilters.has(id) ? "EXACT" : "CONTAINS",
            })),
        [urlFilters, exactFiltersKey]
    );

    const mappingSetIdsKey = mappingSetIds.join(",");
    const subjectPrefixesKey = subjectPrefixes.join(",");
    const objectPrefixesKey = objectPrefixes.join(",");
    // Which asserted corpora to search (ADR-0027); undefined for "both", which sends no filter.
    const mappingSetCategory = useMemo(() => corpusToRequest(corpus), [corpus]);

    // The live search, for the column filters' contextual suggestions (ADR-0034). Built with the SAME
    // buildSearchRequest that fetchMappings uses, so the values offered are faceted over exactly the
    // rows on screen and can never yield zero. Memoised on the same primitive keys as the useQuery
    // key below — without that, a fresh object every render would re-memoise the column definitions
    // on every render, which is the thing ColumnFilterPopover's local input state exists to avoid.
    const weakPredicatesKey = weakPredicates.join(",");
    const suggestContext = useMemo(
        () => buildSearchRequest(
            queries, pagination.pageIndex, pagination.pageSize, columnFiltersForBackend, sorting,
            mappingSetIds, undefined, inferenceTypes, false,
            subjectPrefixes, objectPrefixes, labelMatch, mappingSetCategory, weakPredicates),
        [queries, pagination.pageIndex, pagination.pageSize, columnFiltersForBackend, sorting,
            mappingSetIdsKey, inferenceTypes.join(","), subjectPrefixesKey, objectPrefixesKey,
            labelMatch, mappingSetCategory, weakPredicatesKey]
    );
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
            subjectPrefixesKey,
            objectPrefixesKey,
            labelMatch,
            corpus,
            weakPredicatesKey,
            groupBySpo,
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
                groupBySpo, // group same-SPO mappings into one row (ADR-0013); off for set-detail views
                subjectPrefixes,
                objectPrefixes,
                labelMatch, // label match mode (ADR-0026)
                mappingSetCategory, // asserted corpora (ADR-0027)
                weakPredicates // show the normally-hidden predicates the user ticked (ADR-0035)
            ),
        staleTime: Infinity,
    });

    // Cross-ontology export (ADR-0024): download the full result as SSSOM-compliant TSV.
    const handleExport = useCallback(() => {
        setIsExporting(true);
        exportMappings(queries, columnFiltersForBackend, mappingSetIds, inferenceTypes,
            subjectPrefixes, objectPrefixes, labelMatch, mappingSetCategory, weakPredicates)
            .catch((error) => console.error("Export failed", error))
            .finally(() => setIsExporting(false));
    }, [queries, columnFiltersForBackend, mappingSetIds, inferenceTypes,
        subjectPrefixes, objectPrefixes, labelMatch, mappingSetCategory, weakPredicates]);

    const mappingResults: MappingPage = data ? fromJson(data) : emptyMappingPage;

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
                        <ColumnFilterPopover title="Subject" fields={SUBJECT_FILTER_FIELDS} onChange={handleFilterChange} initialValues={initialFieldFilters} />
                        <ColumnSortPopover title="Subject" fields={SUBJECT_SORT_FIELDS} onApply={setSorting} />
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
                        <WeakPredicateFilterPopover value={weakPredicates} onChange={setWeakPredicates} />
                        <ColumnFilterPopover title="Predicate" fields={PREDICATE_FILTER_FIELDS} onChange={handleFilterChange} onPick={handleFilterPick} suggestContext={suggestContext} initialValues={initialFieldFilters} />
                        <ColumnSortPopover title="Predicate" fields={PREDICATE_SORT_FIELDS} onApply={setSorting} />
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
                        <ColumnFilterPopover title="Object" fields={OBJECT_FILTER_FIELDS} onChange={handleFilterChange} onPick={handleFilterPick} suggestContext={suggestContext} initialValues={initialFieldFilters} />
                        <ColumnSortPopover title="Object" fields={OBJECT_SORT_FIELDS} onApply={setSorting} />
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
                            initialValues={initialFieldFilters}
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
            // Mapping confidence, shown in every view. A same-SPO group (search view) can span sets
            // with different confidences, so it shares the Justification/Provider "Multiple" handling;
            // the set-detail view is ungrouped, so it collapses to the single mapping's value. No
            // value (many mappings carry no confidence) renders as an em dash, not a fabricated number.
            {
                id: "confidence",
                accessorFn: (row) => row.confidence,
                header: "Mapping confidence",
                enableSorting: false,
                size: 150,
                Cell: ({ row }) => {
                    const shared = sharedValue(row.original, (member) =>
                        typeof member.confidence === "number" ? String(member.confidence) : "");
                    if (shared === null) {
                        return <span className="italic text-gray-500">Multiple</span>;
                    }
                    return shared === ""
                        ? <span className="text-gray-400">—</span>
                        : <span>{shared}</span>;
                },
            },
            // Type: dropped on a set-detail page (a single set has a single inference type).
            ...(isSetDetail ? [] : [{
                id: "inference_type",
                accessorFn: (row: Mapping) => row.inferenceType,
                header: "Type",
                enableSorting: false,
                size: 130,
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Type</span>
                        <InferenceTypeFilterPopover value={inferenceTypes} onChange={setInferenceTypes} />
                    </span>
                ),
                Cell: ({ row }: { row: { original: Mapping } }) => {
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
            } as MRT_ColumnDef<Mapping>]),
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
                            initialValues={initialFieldFilters}
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
            // Mapping set: dropped on a set-detail page (every row belongs to the same set).
            ...(isSetDetail ? [] : [{
                id: "mapping_set",
                accessorFn: (row: Mapping) => row.mappingSetTitle || row.mappingSetId,
                header: "Mapping set",
                enableSorting: false,
                size: 220,
                Cell: ({ row }: { row: { original: Mapping } }) => {
                    // One row can span several sets; show "Multiple sets" rather than a misleading single set.
                    if (sharedValue(row.original, (member) => member.mappingSetId) === null) {
                        return <span className="italic text-gray-500">Multiple sets</span>;
                    }
                    const setId = row.original.mappingSetId;
                    const title = row.original.mappingSetTitle;
                    if (!setId) {
                        return <span className="font-semibold break-words">{title || "—"}</span>;
                    }
                    // The set links to its detail page (/mapping-set). When there is a title, it is
                    // the link and the id sits below as muted text; with no title the id itself is the
                    // link. The copy button is always available and never navigates (it is a sibling
                    // of the link, not nested in it).
                    return (
                        <div className="flex flex-col gap-0.5 min-w-0">
                            {title && (
                                <Link to={mappingSetHref(setId)} className="font-semibold break-words text-link-default hover:underline"
                                      title="View mapping set details">
                                    {title}
                                </Link>
                            )}
                            <div className="flex items-start text-xs text-gray-500">
                                {title
                                    ? <span className="break-all">{setId}</span>
                                    : <Link to={mappingSetHref(setId)} className="break-all text-link-default hover:underline"
                                            title="View mapping set details">{setId}</Link>}
                                <CopyButton value={setId} title="Copy mapping set id" />
                            </div>
                        </div>
                    );
                },
            } as MRT_ColumnDef<Mapping>]),
        ],
        [handleFilterChange, setSorting, inferenceTypes, setInferenceTypes, initialFieldFilters,
            weakPredicates, setWeakPredicates, isSetDetail]
    );

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        // Mapping provider is hidden by default in every view (often empty, or the same for every row);
        // enableHiding keeps it revealable via the columns button. Uncontrolled, so the user's toggle
        // sticks.
        initialState: { columnVisibility: { mapping_provider: false } },
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
                                <th className="py-1 pr-4 font-medium">Mapping confidence</th>
                                <th className="py-1 pr-4 font-medium">Mapping set</th>
                                <th className="py-1" />
                            </tr>
                        </thead>
                        <tbody>
                            {members.map((member, index) => (
                                <tr key={member.mappingId || index} className="border-t border-gray-200 align-top">
                                    <td className="py-1 pr-4"><InferenceTypeBadge value={member.inferenceType} /></td>
                                    <td className="py-1 pr-4 break-all">{member.mappingJustification}</td>
                                    <td className="py-1 pr-4">
                                        {typeof member.confidence === "number"
                                            ? member.confidence
                                            : <span className="text-gray-400">—</span>}
                                    </td>
                                    <td className="py-1 pr-4">
                                        {member.mappingSetId ? (
                                            <>
                                                <Link to={mappingSetHref(member.mappingSetId)}
                                                      className="font-semibold break-words text-link-default hover:underline"
                                                      title="View mapping set details">
                                                    {member.mappingSetTitle || member.mappingSetId}
                                                </Link>
                                                {member.mappingSetTitle && (
                                                    <div className="text-xs text-gray-500 break-all">{member.mappingSetId}</div>
                                                )}
                                            </>
                                        ) : (
                                            <div className="font-semibold break-words">{member.mappingSetTitle || "—"}</div>
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
            <div className="flex items-center gap-3">
                {/* The ranking choice (ADR-0036): Best match / confidence / recency. It replaces
                    the whole sorting state — a ranking is exclusive, unlike the per-column sort
                    popovers it shares the `?sort` param with — which also makes "Best match" the
                    one-click way back to relevance from any column sort. */}
                <label className="flex items-center gap-2 text-sm text-tertiary">
                    Order by
                    <select
                        className="input-default text-sm py-1 w-auto"
                        value={sortModeFromSorting(sorting) ?? 'CUSTOM'}
                        onChange={(event) => setSorting(sortModeToSorting(event.target.value as SortMode))}
                    >
                        {SORT_MODE_ORDER.map((mode) => (
                            <option key={mode} value={mode}>{SORT_MODE_LABELS[mode]}</option>
                        ))}
                        {sortModeFromSorting(sorting) === null && (
                            <option value="CUSTOM" disabled>Column sort</option>
                        )}
                    </select>
                </label>
                {(subjectPrefixes.length > 0 || objectPrefixes.length > 0) && (
                    <span className="text-sm text-tertiary">
                        {(mappingResults?.totalElements ?? 0).toLocaleString()} mappings
                        {subjectPrefixes.length > 0 ? ` · from ${subjectPrefixes.join(", ")}` : ""}
                        {objectPrefixes.length > 0 ? ` → ${objectPrefixes.join(", ")}` : ""}
                    </span>
                )}
                <Tooltip title="Download all results as SSSOM-compliant TSV">
                    <span>
                        <IconButton onClick={handleExport} disabled={isExporting} size="small">
                            <ArrowDownTrayIcon className="w-5 h-5" />
                        </IconButton>
                    </span>
                </Tooltip>
            </div>
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
