import { useNavigate } from "react-router-dom";
import { AdvancedFieldQuery, SearchInput, initialSearchState } from "../../model/Search";
import { ADVANCED_FIELD_NAMES } from "../../model/AdvancedFields";
import { useEffect, useMemo, useState } from "react";
import React from "react";
import { useQuery } from "@tanstack/react-query";
import {
    MaterialReactTable,
    type MRT_ColumnDef,
    type MRT_RowSelectionState,
    useMaterialReactTable,
} from "material-react-table";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { fetchMappingSets } from "../../pages/results/MappingSetsSlice";
import { MappingSet } from "../../model/MappingSet";
import { AdvancedSearch } from "./AdvancedSearch";

const tableTheme = createTheme({
    palette: {
        primary: { main: "#d4522c", light: "#b75c00", dark: "#461901", contrastText: "#fff" },
        secondary: { main: "#525252", light: "#99a1af", dark: "#373a36", contrastText: "#fff" },
    },
});

type ActiveTab = 'search' | 'advanced';

export function Search({ searchInput = initialSearchState, showWelcome = false }: {
    searchInput: SearchInput,
    showWelcome?: boolean
}) {
    const navigate = useNavigate();
    const [searchState, setSearchState] = useState<SearchInput>(searchInput);
    const [activeTab, setActiveTab] = useState<ActiveTab>(searchInput.activeTab ?? 'search');
    const [advancedValues, setAdvancedValues] = useState<Record<string, string>>(() =>
        Object.fromEntries(
            (searchInput.advancedFieldQueries ?? [])
                .filter((q) => ADVANCED_FIELD_NAMES.has(q.field))
                .map((q) => [q.field, q.value])
        )
    );

    const { data: mappingSets = [], isLoading: mappingSetsLoading } = useQuery({
        queryKey: ["fetchMappingSets"],
        queryFn: fetchMappingSets,
        staleTime: Infinity,
    });

    const idsToSelectionState = (ids?: string[]): MRT_RowSelectionState =>
        (ids ?? []).reduce<MRT_RowSelectionState>((acc, id) => {
            acc[id] = true;
            return acc;
        }, {});

    const [rowSelection, setRowSelection] = useState<MRT_RowSelectionState>(
        idsToSelectionState(searchInput.mappingSetIds)
    );

    // Keep row selection in sync when mapping_set_id query params arrive later
    // (e.g. via URL on the results page) after the table mounts.
    const incomingIdsKey = (searchInput.mappingSetIds ?? []).join(",");
    useEffect(() => {
        const ids = searchInput.mappingSetIds;
        setRowSelection(idsToSelectionState(ids));
        setSearchState((prev) => ({ ...prev, mappingSetIds: ids }));
    }, [incomingIdsKey]);

    // Pick up advanced values arriving via URL after mount.
    const incomingAdvancedKey = JSON.stringify(searchInput.advancedFieldQueries ?? []);
    useEffect(() => {
        setAdvancedValues(
            Object.fromEntries(
                (searchInput.advancedFieldQueries ?? [])
                    .filter((q) => ADVANCED_FIELD_NAMES.has(q.field))
                    .map((q) => [q.field, q.value])
            )
        );
        if (searchInput.activeTab) {
            setActiveTab(searchInput.activeTab);
        }
    }, [incomingAdvancedKey, searchInput.activeTab]);

    const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
        const userSearchInput = event.target.value;
        const sanitizedSearchInput = userSearchInput.split(/[\n,]+/).filter(item => item.trim() !== '');
        setSearchState((prev) => ({ ...prev, userSearchInput, sanitizedSearchInput }));
    };

    const handleSearch = () => {
        if (searchState.userSearchInput && searchState.userSearchInput.trim() !== "") {
            const curies = searchState.sanitizedSearchInput.join(",");
            const path = `/search/${encodeURIComponent(curies)}`;
            const ids = searchState.mappingSetIds ?? [];
            const query = ids.length > 0
                ? "?" + ids.map((id) => `mapping_set_id=${encodeURIComponent(id)}`).join("&")
                : "";
            navigate(`${path}${query}`);
        }
    };

    const handleClear = () => {
        setSearchState({
            userSearchInput: "",
            sanitizedSearchInput: [],
            mappingSetIds: undefined,
            activeTab,
        });
        setRowSelection({});
    };

    const handleAdvancedChange = (field: string, value: string) => {
        setAdvancedValues((prev) => ({ ...prev, [field]: value }));
    };

    const handleAdvancedSearch = () => {
        const filled: AdvancedFieldQuery[] = Object.entries(advancedValues)
            .filter(([, v]) => v && v.trim() !== "")
            .map(([field, value]) => ({ field, value: value.trim() }));
        if (filled.length === 0) return;

        const params = new URLSearchParams();
        for (const fq of filled) {
            params.append("af", `${fq.field}=${fq.value}`);
        }
        const ids = searchState.mappingSetIds ?? [];
        for (const id of ids) {
            params.append("mapping_set_id", id);
        }
        const query = params.toString();
        navigate(`/search/_advanced${query ? `?${query}` : ""}`);
    };

    const handleAdvancedClear = () => {
        setAdvancedValues({});
    };

    const columns = useMemo<MRT_ColumnDef<MappingSet>[]>(
        () => [
            { accessorKey: "mappingSetTitle", header: "Title", size: 200 },
            {
                accessorKey: "mappingSetId",
                header: "Mapping Set Id",
                size: 250,
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
                Cell: ({ cell }) => <span>{(cell.getValue<string[]>() ?? []).join(", ")}</span>,
            },
            { accessorKey: "mappingProvider", header: "Provider", size: 150 },
        ],
        []
    );

    const table = useMaterialReactTable<MappingSet>({
        columns,
        data: mappingSets,
        enableRowSelection: true,
        enableMultiRowSelection: true,
        enableSelectAll: true,
        getRowId: (row) => row.mappingSetId,
        state: { rowSelection, isLoading: mappingSetsLoading, density: "compact" },
        onRowSelectionChange: (updater) => {
            const next = typeof updater === "function" ? updater(rowSelection) : updater;
            setRowSelection(next);
            const selectedIds = Object.keys(next).filter((k) => next[k]);
            setSearchState((prev) => ({
                ...prev,
                mappingSetIds: selectedIds.length > 0 ? selectedIds : undefined,
            }));
        },
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => row.toggleSelected(),
            sx: { cursor: "pointer" },
        }),
        muiTableContainerProps: { sx: { maxHeight: "20rem" } },
        enableTopToolbar: false,
        enableBottomToolbar: false,
        enableColumnActions: false,
        enableColumnFilters: false,
        enableSorting: true,
        enablePagination: false,
        enableStickyHeader: true,
        muiTableHeadCellProps: {
            sx: { fontWeight: "bold", fontSize: "14px" },
        },
        initialState: { density: "compact" },
    });

    const tabButtonClass = (tab: ActiveTab) =>
        `px-4 py-1 text-base font-semibold border-b-2 cursor-pointer ${
            activeTab === tab
                ? "border-primary text-primary"
                : "border-transparent text-tertiary hover:text-primary"
        }`;

    return (
        <div className="search-container">
            {showWelcome && (
                <div className="text-primary">
                    Welcome to the EMBL-EBI OxO Mapping Service
                </div>
            )}
            <div className="flex border-b border-gray-200 mb-3">
                <button
                    type="button"
                    className={tabButtonClass('search')}
                    onClick={() => setActiveTab('search')}
                >
                    Search
                </button>
                <button
                    type="button"
                    className={tabButtonClass('advanced')}
                    onClick={() => setActiveTab('advanced')}
                >
                    Advanced
                </button>
            </div>

            {activeTab === 'search' ? (
                <div className="flex flex-col md:flex-row gap-4">
                    <div className="w-full">
                        <div className="flex flex-col md:flex-row justify-between mb-2">
                            <div className="text-tertiary">
                                Enter identifiers, IRIs, or labels separated by comma or newline:
                            </div>
                            <div
                                className="link-default md:mx-0.5"
                                onClick={() => {
                                    setSearchState((prev) => ({
                                        ...prev,
                                        userSearchInput: "UBERON:0002107\nCataract\nhttp://purl.obolibrary.org/obo/MP_0001289",
                                        sanitizedSearchInput: ["UBERON:0002107", "Cataract", "http://purl.obolibrary.org/obo/MP_0001289"],
                                    }));
                                }}
                            >
                                Examples...
                            </div>
                        </div>
                        <textarea
                            id="home-search"
                            rows={2}
                            className="input-default text-lg resize-y min-h-24"
                            placeholder={"Search OxO..."}
                            value={searchState.userSearchInput}
                            onChange={handleInputChange}
                        />
                    </div>
                    <div className="flex flex-col gap-2 md:mt-10">
                        <button
                            className="button-primary text-base font-bold px-4 py-1"
                            onClick={handleSearch}
                        >
                            Search
                        </button>
                        <button
                            className="button-primary text-base font-bold px-4 py-1"
                            onClick={handleClear}
                        >
                            Clear
                        </button>
                    </div>
                </div>
            ) : (
                <AdvancedSearch
                    values={advancedValues}
                    onChange={handleAdvancedChange}
                    onSubmit={handleAdvancedSearch}
                    onClear={handleAdvancedClear}
                />
            )}

            <div className="mt-4">
                <div className="text-tertiary mb-2">
                    Optionally restrict the search to one or more mapping sets
                    {(searchState.mappingSetIds ?? []).length > 0 && (
                        <span className="ml-2 text-sm">
                            ({(searchState.mappingSetIds ?? []).length} selected — click a row to toggle)
                        </span>
                    )}
                </div>
                <ThemeProvider theme={tableTheme}>
                    <MaterialReactTable table={table} />
                </ThemeProvider>
            </div>
        </div>
    );
}
