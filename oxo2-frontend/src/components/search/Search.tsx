import { useNavigate } from "react-router-dom";
import { AdvancedFieldQuery, SearchInput, initialSearchState } from "../../model/Search";
import { ADVANCED_FIELD_NAMES } from "../../model/AdvancedFields";
import { useCallback, useEffect, useMemo, useState } from "react";
import React from "react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { useQuery } from "@tanstack/react-query";
import { AdvancedSearch } from "./AdvancedSearch";
import { MappingSetSelector } from "./MappingSetSelector";
import { OntologySelector } from "./OntologySelector";
import { fetchOntologies, fetchTargetsForSubject } from "../../pages/results/OntologiesSlice";

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

    // Mapping-set selection is held in searchState.mappingSetIds and passed down to the
    // (memoised) MappingSetSelector as controlled state. selectedIds / onSelectionChange
    // are kept referentially stable so the selector — a table of hundreds of rows — is
    // skipped by React.memo while the user types in the search box or advanced fields.
    const selectedIds = useMemo(
        () => searchState.mappingSetIds ?? [],
        [searchState.mappingSetIds]
    );

    const handleSelectionChange = useCallback((ids: string[]) => {
        setSearchState((prev) => ({
            ...prev,
            mappingSetIds: ids.length > 0 ? ids : undefined,
        }));
    }, []);

    // Cross-ontology mapping (ADR-0024): source/target ontology prefix selectors on the Search tab.
    const [subjectPrefixes, setSubjectPrefixes] = useState<string[]>(searchInput.subjectPrefixes ?? []);
    const [objectPrefixes, setObjectPrefixes] = useState<string[]>(searchInput.objectPrefixes ?? []);

    const { data: ontologies } = useQuery({
        queryKey: ["ontologies"], queryFn: fetchOntologies, staleTime: Infinity,
    });
    const allPrefixes = useMemo(() => (ontologies ?? []).map((o) => o.prefix), [ontologies]);
    const subjectCounts = useMemo(
        () => Object.fromEntries((ontologies ?? []).map((o) => [o.prefix, o.asSubject])), [ontologies]);
    const objectCounts = useMemo(
        () => Object.fromEntries((ontologies ?? []).map((o) => [o.prefix, o.asObject])), [ontologies]);

    // With exactly one source chosen, narrow the target options to the reachable ones (with counts).
    const singleSource = subjectPrefixes.length === 1 ? subjectPrefixes[0] : null;
    const { data: targets } = useQuery({
        queryKey: ["targetsForSubject", singleSource],
        queryFn: () => fetchTargetsForSubject(singleSource as string),
        enabled: singleSource != null,
        staleTime: Infinity,
    });
    const targetOptions = singleSource && targets ? targets.map((t) => t.prefix) : allPrefixes;
    const targetCounts = singleSource && targets
        ? Object.fromEntries(targets.map((t) => [t.prefix, t.count])) : objectCounts;

    const handleSwap = () => {
        const previousSource = subjectPrefixes;
        setSubjectPrefixes(objectPrefixes);
        setObjectPrefixes(previousSource);
    };

    // Keep selection in sync when mapping_set_id query params arrive later
    // (e.g. via URL on the results page) after this component mounts.
    const incomingIdsKey = (searchInput.mappingSetIds ?? []).join(",");
    useEffect(() => {
        setSearchState((prev) => ({ ...prev, mappingSetIds: searchInput.mappingSetIds }));
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

    // Pick up from/to prefixes arriving via URL after mount.
    const incomingPrefixKey = (searchInput.subjectPrefixes ?? []).join(",")
        + "|" + (searchInput.objectPrefixes ?? []).join(",");
    useEffect(() => {
        setSubjectPrefixes(searchInput.subjectPrefixes ?? []);
        setObjectPrefixes(searchInput.objectPrefixes ?? []);
    }, [incomingPrefixKey]);

    const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
        const userSearchInput = event.target.value;
        const sanitizedSearchInput = userSearchInput.split(/[\n,]+/).filter(item => item.trim() !== '');
        setSearchState((prev) => ({ ...prev, userSearchInput, sanitizedSearchInput }));
    };

    const handleSearch = () => {
        const hasTerms = !!searchState.userSearchInput && searchState.userSearchInput.trim() !== "";
        const hasPrefixes = subjectPrefixes.length > 0 || objectPrefixes.length > 0;
        if (!hasTerms && !hasPrefixes) {
            return;
        }
        // Terms present → /search/<curies>; whole-ontology (prefixes only) → the _map sentinel.
        const curies = hasTerms ? searchState.sanitizedSearchInput.join(",") : "_map";
        const params = new URLSearchParams();
        for (const id of searchState.mappingSetIds ?? []) {
            params.append("mapping_set_id", id);
        }
        for (const prefix of subjectPrefixes) {
            params.append("from", prefix);
        }
        for (const prefix of objectPrefixes) {
            params.append("to", prefix);
        }
        const query = params.toString();
        navigate(`/search/${encodeURIComponent(curies)}${query ? `?${query}` : ""}`);
    };

    const handleClear = () => {
        setSearchState({
            userSearchInput: "",
            sanitizedSearchInput: [],
            mappingSetIds: undefined,
            activeTab,
        });
        setSubjectPrefixes([]);
        setObjectPrefixes([]);
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
                <>
                <div className="mb-3">
                    <div className="text-tertiary mb-2">
                        Optionally map from / to whole ontologies (leave the box empty to map an entire
                        source ontology):
                    </div>
                    <div className="flex flex-col md:flex-row items-stretch md:items-center gap-2">
                        <div className="flex-1">
                            <OntologySelector
                                label="From ontologies"
                                placeholder="source, e.g. DOID"
                                options={allPrefixes}
                                counts={subjectCounts}
                                value={subjectPrefixes}
                                onChange={setSubjectPrefixes}
                            />
                        </div>
                        <button
                            type="button"
                            title="Swap source and target"
                            className="link-default px-2 py-1 text-xl"
                            onClick={handleSwap}
                        >
                            ⇄
                        </button>
                        <div className="flex-1">
                            <OntologySelector
                                label="To ontologies"
                                placeholder="target, e.g. EFO, MONDO"
                                options={targetOptions}
                                counts={targetCounts}
                                value={objectPrefixes}
                                onChange={setObjectPrefixes}
                            />
                        </div>
                    </div>
                </div>
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
                </>
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
                    <MappingSetSelector
                        selectedIds={selectedIds}
                        onSelectionChange={handleSelectionChange}
                    />
                </ThemeProvider>
            </div>
        </div>
    );
}
