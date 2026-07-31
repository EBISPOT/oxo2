import {useCallback, useMemo, useState} from "react";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_SortingState,
    useMaterialReactTable,
} from "material-react-table";
import {useQuery} from "@tanstack/react-query";
import {Link} from "react-router";
import {mappingSetHref} from "../../util/mappingSetUrl";
import {fetchConfidenceByMappingIds} from "./MappingResultsSlice";
import {InferredMapping, Mapping} from "../../model/Mapping.ts";
import {mappingJustificationShortName, mappingJustificationLabel} from "../../model/MappingJustification";
import {EntityRefCell} from "../../components/mapping/EntityRefCell";
import {ColumnFilterPopover, type FilterFieldDef} from "../../components/mapping/ColumnFilterPopover";
import {ColumnSortPopover, type SortFieldDef} from "../../components/mapping/ColumnSortPopover";
import {SortingContext} from "../../components/mapping/sortingContext";
import type {ValueSuggestion} from "./SuggestSlice";

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

// Per-column filter inputs, mirroring the Normal search results table. As with the sort fields
// above, `field` is the InferredMapping property name rather than a Solr field name: the rows are
// local data, so the filters are applied client-side (see rowsMatchingTextFilters).
//
// Each carries `suggest: "contextual"`, so it is a typeahead rather than a plain box. The results
// table's typeahead facets a backend search this table does not have; here the component counts the
// distinct values over the on-screen rows itself and passes them as `localOptions` (see
// textFilterOptions), which ValueSuggest narrows client-side. Note this DOES give Subject a typeahead,
// where the results table deliberately does not: there, subject-side search (ADR-0030) means every
// row's subject is what the user just searched, so there is nothing to narrow — but an inferred
// mapping's asserted premises carry genuinely varied subjects, so the suggestion earns its place.
const SUBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "subjectId", label: "Subject ID", suggest: "contextual"},
    {field: "subjectLabel", label: "Subject label", suggest: "contextual"},
    {field: "subjectIri", label: "Subject IRI", suggest: "contextual"},
];
const PREDICATE_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "predicateId", label: "Predicate ID", suggest: "contextual"},
    {field: "predicateIri", label: "Predicate IRI", suggest: "contextual"},
];
const OBJECT_FILTER_FIELDS: FilterFieldDef[] = [
    {field: "objectId", label: "Object ID", suggest: "contextual"},
    {field: "objectLabel", label: "Object label", suggest: "contextual"},
    {field: "objectIri", label: "Object IRI", suggest: "contextual"},
];
// The property names the text filters (and their typeaheads) act on — everything except the pick-only
// justification. Drives the per-field option counts in textFilterOptions.
const TEXT_FILTER_PROPERTIES = [
    "subjectId", "subjectLabel", "subjectIri",
    "predicateId", "predicateIri",
    "objectId", "objectLabel", "objectIri",
] as const;

// One active filter's predicate against one row. The justification is an exact match on the raw SEMAPV
// CURIE (pick-only); every other field is a case-insensitive "contains" on the string value. A blank
// value matches everything (the filter is inactive).
const rowMatchesFilter = (row: InferredMapping, field: string, value: string): boolean => {
    const needle = value.trim();
    if (needle === "") {
        return true;
    }
    if (field === "mappingJustification") {
        return row.mappingJustification === needle;
    }
    return String(row[field as keyof InferredMapping] ?? "").toLowerCase().includes(needle.toLowerCase());
};

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
 * IRI cell with per-column filter and sort popovers — but operates on the local
 * mapping.assertedMappings array (so filtering and sorting are client-side) and shows
 * every row without paging.
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

    // The applied column filters, keyed by InferredMapping property name. Text fields are
    // AND-combined case-insensitive "contains" — the same semantics the results table's typed
    // fragments get from the backend; the justification is a pick-only vocab value (a raw SEMAPV
    // CURIE) and matches exactly. No debounce layer: there is no backend round-trip to spare, so
    // each keystroke filters the local rows directly.
    const [filters, setFilters] = useState<Record<string, string>>({});

    const handleFilterChange = useCallback((field: string, value: string) => {
        setFilters((previous) => ({ ...previous, [field]: value }));
    }, []);

    // Rows surviving the text filters (subject/predicate/object), before the justification pick is
    // applied. The justification dropdown's options are counted over THIS set — the client-side
    // equivalent of the backend stripping the field's own filter before faceting (see VocabSelect) —
    // so the other values stay on offer after one is picked, and a pick can never empty the table.
    const rowsMatchingTextFilters = useMemo<InferredMapping[]>(() => {
        const rows = mapping.assertedMappings ?? [];
        const activeTextFilters = Object.entries(filters).filter(([field, value]) =>
            field !== "mappingJustification" && value.trim() !== "");
        if (activeTextFilters.length === 0) {
            return rows;
        }
        return rows.filter((row) =>
            activeTextFilters.every(([field, value]) => rowMatchesFilter(row, field, value)));
    }, [mapping.assertedMappings, filters]);

    // The typeahead options for each text column: the distinct values present in the rows, counted,
    // most common first. Each column is scoped to the rows matching every OTHER active filter (not its
    // own) — the local analog of the backend stripping a field's own filter before faceting — so the
    // suggestions reflect what a pick would leave and a pick can never empty the table.
    const textFilterOptions = useMemo<Record<string, ValueSuggestion[]>>(() => {
        const rows = mapping.assertedMappings ?? [];
        const activeFilters = Object.entries(filters).filter(([, value]) => value.trim() !== "");
        const optionsByField: Record<string, ValueSuggestion[]> = {};
        for (const field of TEXT_FILTER_PROPERTIES) {
            const scoped = rows.filter((row) => activeFilters.every(([otherField, value]) =>
                otherField === field || rowMatchesFilter(row, otherField, value)));
            const counts = new Map<string, number>();
            for (const row of scoped) {
                const value = String(row[field as keyof InferredMapping] ?? "");
                if (value !== "") {
                    counts.set(value, (counts.get(value) ?? 0) + 1);
                }
            }
            optionsByField[field] = [...counts.entries()]
                .sort((left, right) => right[1] - left[1])
                .map(([value, count]) => ({ value, count }));
        }
        return optionsByField;
    }, [mapping.assertedMappings, filters]);

    const subjectFilterFields = useMemo<FilterFieldDef[]>(() =>
        SUBJECT_FILTER_FIELDS.map((fieldDef) =>
            ({ ...fieldDef, localOptions: textFilterOptions[fieldDef.field] })), [textFilterOptions]);
    const predicateFilterFields = useMemo<FilterFieldDef[]>(() =>
        PREDICATE_FILTER_FIELDS.map((fieldDef) =>
            ({ ...fieldDef, localOptions: textFilterOptions[fieldDef.field] })), [textFilterOptions]);
    const objectFilterFields = useMemo<FilterFieldDef[]>(() =>
        OBJECT_FILTER_FIELDS.map((fieldDef) =>
            ({ ...fieldDef, localOptions: textFilterOptions[fieldDef.field] })), [textFilterOptions]);

    // Distinct justification values present in the (text-filtered) rows, most common first — the
    // local stand-in for the results table's live-search facet.
    const justificationOptions = useMemo<ValueSuggestion[]>(() => {
        const counts = new Map<string, number>();
        for (const row of rowsMatchingTextFilters) {
            if (row.mappingJustification) {
                counts.set(row.mappingJustification, (counts.get(row.mappingJustification) ?? 0) + 1);
            }
        }
        return [...counts.entries()]
            .sort((left, right) => right[1] - left[1])
            .map(([value, count]) => ({ value, count }));
    }, [rowsMatchingTextFilters]);

    const justificationFilterFields = useMemo<FilterFieldDef[]>(() => [
        {field: "mappingJustification", label: "Mapping justification", suggest: "vocab",
            formatOption: mappingJustificationShortName, formatOptionTitle: mappingJustificationLabel,
            localOptions: justificationOptions},
    ], [justificationOptions]);

    const filteredAssertedMappings = useMemo<InferredMapping[]>(() => {
        const justification = (filters.mappingJustification ?? "").trim();
        if (justification === "") {
            return rowsMatchingTextFilters;
        }
        return rowsMatchingTextFilters.filter((row) => row.mappingJustification === justification);
    }, [rowsMatchingTextFilters, filters.mappingJustification]);

    const sortedAssertedMappings = useMemo<InferredMapping[]>(() => {
        const rows = filteredAssertedMappings;
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
    }, [filteredAssertedMappings, sorting]);

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
                        <ColumnFilterPopover title="Subject" fields={subjectFilterFields} onChange={handleFilterChange} />
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
                        <ColumnFilterPopover title="Predicate" fields={predicateFilterFields} onChange={handleFilterChange} />
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
                        <ColumnFilterPopover title="Object" fields={objectFilterFields} onChange={handleFilterChange} />
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
                Header: () => (
                    <span className="flex items-center gap-1">
                        <span>Mapping justification</span>
                        <ColumnFilterPopover
                            title="Mapping justification"
                            fields={justificationFilterFields}
                            onChange={handleFilterChange}
                        />
                    </span>
                ),
                Cell: ({ row }) => (
                    <span className="break-all" title={mappingJustificationLabel(row.original.mappingJustification)}>
                        {mappingJustificationShortName(row.original.mappingJustification)}
                    </span>
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
        [handleSortChange, handleFilterChange, subjectFilterFields, predicateFilterFields,
            objectFilterFields, justificationFilterFields, confidenceById]
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
