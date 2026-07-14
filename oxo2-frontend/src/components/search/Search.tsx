import { useNavigate } from "react-router-dom";
import { AdvancedFieldQuery, SearchInput, SearchMode, initialSearchState } from "../../model/Search";
import { LabelMatchMode, LABEL_MATCH_LABELS, LABEL_MATCH_ORDER, DEFAULT_LABEL_MATCH } from "../../model/LabelMatchMode";
import { CorpusMode, DEFAULT_CORPUS, corpusToUrlParam } from "../../model/MappingSetCategory";
import { SortMode, SORT_MODE_LABELS, SORT_MODE_ORDER, DEFAULT_SORT_MODE, sortModeToUrlParams } from "../../model/SortMode";
import { WeakPredicate, WEAK_PREDICATE_ORDER, WEAK_PREDICATE_LABELS, WEAK_PREDICATE_HINTS, DEFAULT_WEAK_PREDICATES } from "../../model/WeakPredicate";
import { ADVANCED_FIELD_NAMES } from "../../model/AdvancedFields";
import { useCallback, useEffect, useMemo, useState } from "react";
import React from "react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { useQuery } from "@tanstack/react-query";
import { AdvancedSearch } from "./AdvancedSearch";
import { CorpusSelector } from "./CorpusSelector";
import { EntitySuggest } from "./EntitySuggest";
import { MappingSetSelector } from "./MappingSetSelector";
import { OntologySelector } from "./OntologySelector";
import { TermFileDrop } from "./TermFileDrop";
import { parseTerms } from "../../util/terms";
import { fetchOntologies, fetchTargetsForSubject } from "../../pages/results/OntologiesSlice";

const tableTheme = createTheme({
    palette: {
        primary: { main: "#d4522c", light: "#b75c00", dark: "#461901", contrastText: "#fff" },
        secondary: { main: "#525252", light: "#99a1af", dark: "#373a36", contrastText: "#fff" },
    },
});

type ActiveTab = 'search' | 'advanced';

const SINGLE_EXAMPLE = "cataract";
const MULTIPLE_EXAMPLE = "UBERON:0002107\nCataract\nhttp://purl.obolibrary.org/obo/MP_0001289";

/**
 * The entry point. A novice arrives wanting to map one term, or a list of terms, between ontologies.
 * Everything that is not that question is behind "More options": label-match semantics and the
 * restrict-to-mapping-sets table, both of which used to greet the user before they had typed anything.
 *
 * Two things stay on the surface because they change what the answer *means*, not merely how it is
 * fetched: which corpora the mappings come from (ADR-0027) and how the results are ordered.
 */
export function Search({ searchInput = initialSearchState, showWelcome = false }: {
    searchInput: SearchInput,
    showWelcome?: boolean
}) {
    const navigate = useNavigate();
    const [searchState, setSearchState] = useState<SearchInput>(searchInput);
    const [activeTab, setActiveTab] = useState<ActiveTab>(searchInput.activeTab ?? 'search');
    const [searchMode, setSearchMode] = useState<SearchMode>(searchInput.searchMode ?? 'single');
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

    // Label match mode (ADR-0026): how free-text label queries match. Defaults to case-insensitive
    // exact; carried in the URL `match` param on submit.
    const [labelMatch, setLabelMatch] = useState<LabelMatchMode>(searchInput.labelMatch ?? DEFAULT_LABEL_MATCH);
    // Which asserted corpora to search (ADR-0027); carried in the URL `corpus` param.
    const [corpus, setCorpus] = useState<CorpusMode>(searchInput.corpus ?? DEFAULT_CORPUS);
    // Result order. "Best match" writes no `sort` param, which is what lets the backend rank by
    // relevance — i.e. by the provenance-led boost.
    const [sortBy, setSortBy] = useState<SortMode>(searchInput.sortBy ?? DEFAULT_SORT_MODE);
    // The normally-hidden predicates the user wants shown (ADR-0035). Local state here, carried into
    // the results URL on submit as `wp`, where the table's useUrlWeakPredicates picks it up — so the
    // suggestions offered here and the rows shown there are filtered by the same selection.
    const [weakPredicates, setWeakPredicates] =
        useState<WeakPredicate[]>(searchInput.includeWeakPredicates ?? DEFAULT_WEAK_PREDICATES);

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

    // Pick up the label match mode, corpus and sort arriving via URL after mount.
    useEffect(() => {
        setLabelMatch(searchInput.labelMatch ?? DEFAULT_LABEL_MATCH);
    }, [searchInput.labelMatch]);
    useEffect(() => {
        setCorpus(searchInput.corpus ?? DEFAULT_CORPUS);
    }, [searchInput.corpus]);
    useEffect(() => {
        setSortBy(searchInput.sortBy ?? DEFAULT_SORT_MODE);
    }, [searchInput.sortBy]);
    // Keyed on the joined codes, not the array: it is rebuilt from the URL on every render, so
    // depending on its identity would loop.
    const incomingWeakPredicateKey = (searchInput.includeWeakPredicates ?? []).join(",");
    useEffect(() => {
        setWeakPredicates(searchInput.includeWeakPredicates ?? DEFAULT_WEAK_PREDICATES);
    }, [incomingWeakPredicateKey]);
    useEffect(() => {
        if (searchInput.searchMode) setSearchMode(searchInput.searchMode);
    }, [searchInput.searchMode]);

    const setTermText = (userSearchInput: string) => {
        setSearchState((prev) => ({
            ...prev,
            userSearchInput,
            sanitizedSearchInput: parseTerms(userSearchInput),
        }));
    };

    const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement>) => {
        setTermText(event.target.value);
    };

    // A dropped file appends to whatever is already in the box, so the textarea stays the one
    // authoritative list the user can read back before searching.
    const handleFileTerms = useCallback((terms: string[]) => {
        setSearchState((prev) => {
            const existing = parseTerms(prev.userSearchInput);
            const merged = [...existing, ...terms.filter((term) => !existing.includes(term))];
            return { ...prev, userSearchInput: merged.join("\n"), sanitizedSearchInput: merged };
        });
    }, []);

    /**
     * @param overrideTerm search this instead of whatever is in searchState. Picking an autocomplete
     * suggestion needs it: setTermText is a setState, so it has not landed yet when the pick handler
     * calls through, and reading searchState here would search the half-typed fragment the user was
     * replacing rather than the entity they chose.
     */
    const handleSearch = (overrideTerm?: string) => {
        const userSearchInput = overrideTerm ?? searchState.userSearchInput;
        const sanitizedSearchInput = overrideTerm
            ? parseTerms(overrideTerm)
            : searchState.sanitizedSearchInput;

        const hasTerms = !!userSearchInput && userSearchInput.trim() !== "";
        const hasPrefixes = subjectPrefixes.length > 0 || objectPrefixes.length > 0;
        if (!hasTerms && !hasPrefixes) {
            return;
        }
        // Terms present → /search/<curies>; whole-ontology (prefixes only) → the _map sentinel.
        const curies = hasTerms ? sanitizedSearchInput.join(",") : "_map";
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
        // Only carry non-default choices; the defaults keep URLs clean and match the backend defaults.
        if (labelMatch !== DEFAULT_LABEL_MATCH) {
            params.append("match", labelMatch);
        }
        const corpusParam = corpusToUrlParam(corpus);
        if (corpusParam) {
            params.append("corpus", corpusParam);
        }
        for (const sortToken of sortModeToUrlParams(sortBy)) {
            params.append("sort", sortToken);
        }
        // Carry the ticked predicates through to the result table (ADR-0035). Both-unticked is the
        // default and stays out of the URL.
        for (const predicate of weakPredicates) {
            params.append("wp", predicate);
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
            searchMode,
        });
        setSubjectPrefixes([]);
        setObjectPrefixes([]);
        setLabelMatch(DEFAULT_LABEL_MATCH);
        setCorpus(DEFAULT_CORPUS);
        setSortBy(DEFAULT_SORT_MODE);
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

    const modeButtonClass = (mode: SearchMode) =>
        `px-3 py-1 rounded-md text-base cursor-pointer transition-colors ${
            searchMode === mode
                ? "bg-link-default text-white"
                : "text-neutral-dark hover:text-link-default"
        }`;

    const termCount = searchState.sanitizedSearchInput.length;

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
                <div
                    role="radiogroup"
                    aria-label="How many terms are you mapping?"
                    className="inline-flex gap-1 mb-3 p-1 rounded-md bg-white"
                >
                    {(['single', 'multiple'] as SearchMode[]).map((mode) => (
                        <button
                            key={mode}
                            type="button"
                            role="radio"
                            aria-checked={searchMode === mode}
                            className={modeButtonClass(mode)}
                            onClick={() => setSearchMode(mode)}
                        >
                            {mode === 'single' ? 'Single term' : 'Multiple terms'}
                        </button>
                    ))}
                </div>

                <div className="flex flex-col md:flex-row gap-4">
                    <div className="w-full">
                        <div className="flex flex-col md:flex-row justify-between mb-2">
                            <label htmlFor="home-search" className="text-tertiary">
                                {searchMode === 'single'
                                    ? 'Which term do you want to map?'
                                    : 'Which terms do you want to map? One per line.'}
                            </label>
                            <button
                                type="button"
                                className="link-default md:mx-0.5"
                                onClick={() => setTermText(searchMode === 'single' ? SINGLE_EXAMPLE : MULTIPLE_EXAMPLE)}
                            >
                                {searchMode === 'single' ? 'Example...' : 'Examples...'}
                            </button>
                        </div>
                        {searchMode === 'single' ? (
                            // Typeahead over entities (ADR-0034). Subject-side, because the default
                            // search matches the subject side only (ADR-0030) — an object-only entity
                            // would complete to zero rows. Restricted to the chosen source ontologies,
                            // so the "From" selector narrows the suggestions for free.
                            <EntitySuggest
                                inputId="home-search"
                                value={searchState.userSearchInput}
                                onTyped={setTermText}
                                onPick={(entity) => {
                                    setTermText(entity.id);
                                    // Pass the CURIE explicitly: setTermText has not landed yet.
                                    handleSearch(entity.id);
                                }}
                                onSubmit={() => handleSearch()}
                                side="subject"
                                prefixes={subjectPrefixes}
                                includeWeakPredicates={weakPredicates}
                                placeholder="A label, CURIE or IRI — e.g. cataract, MP:0001289"
                            />
                        ) : (
                            <>
                                <textarea
                                    id="home-search"
                                    rows={4}
                                    className="input-default text-lg resize-y min-h-24"
                                    placeholder={"cataract\nMP:0001289\nhttp://purl.obolibrary.org/obo/UBERON_0002107"}
                                    value={searchState.userSearchInput}
                                    onChange={handleInputChange}
                                />
                                <div className="mt-2">
                                    <TermFileDrop onTerms={handleFileTerms} />
                                </div>
                                {termCount > 0 && (
                                    <div className="text-tertiary text-sm mt-1">
                                        {termCount} {termCount === 1 ? 'term' : 'terms'} to map
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                    <div className="flex flex-col gap-2 md:mt-10">
                        <button
                            className="button-primary text-base font-bold px-4 py-1"
                            onClick={() => handleSearch()}
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

                <div className="mt-4">
                    <div className="text-tertiary mb-2">
                        Between which ontologies? Leave either side empty for "any".
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

                <div className="mt-4 flex flex-col md:flex-row md:items-start gap-6">
                    <CorpusSelector value={corpus} onChange={setCorpus} />
                    <div>
                        <label htmlFor="sort-by" className="text-tertiary mb-2 block">Order results by</label>
                        <select
                            id="sort-by"
                            className="input-default text-base py-1 w-auto"
                            value={sortBy}
                            onChange={(event) => setSortBy(event.target.value as SortMode)}
                        >
                            {SORT_MODE_ORDER.map((mode) => (
                                <option key={mode} value={mode}>{SORT_MODE_LABELS[mode]}</option>
                            ))}
                        </select>
                        <div className="text-tertiary text-sm mt-1">
                            Best match ranks by who asserts the mapping, then how strong it is.
                        </div>
                    </div>
                    {/* The normally-hidden predicates (ADR-0035). Here rather than only on the result
                        table because they also decide what the search box above will SUGGEST: with
                        both unticked, an entity whose every mapping is an xref is not offered, since
                        picking it would land on an empty table. */}
                    <div>
                        <span className="text-tertiary mb-2 block">Also show</span>
                        {WEAK_PREDICATE_ORDER.map((predicate) => (
                            <label key={predicate} className="flex items-start gap-2 mb-1">
                                <input
                                    type="checkbox"
                                    className="mt-1"
                                    checked={weakPredicates.includes(predicate)}
                                    onChange={(event) =>
                                        setWeakPredicates(
                                            WEAK_PREDICATE_ORDER.filter((code) =>
                                                code === predicate
                                                    ? event.target.checked
                                                    : weakPredicates.includes(code)
                                            )
                                        )
                                    }
                                />
                                <span>
                                    <span className="text-base">{WEAK_PREDICATE_LABELS[predicate]}</span>
                                    <span className="text-tertiary text-sm block">
                                        {WEAK_PREDICATE_HINTS[predicate]}
                                    </span>
                                </span>
                            </label>
                        ))}
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

            <details className="mt-6 group">
                <summary className="link-default text-base list-none cursor-pointer select-none">
                    <span className="group-open:hidden">▸ More options</span>
                    <span className="hidden group-open:inline">▾ Fewer options</span>
                    {(searchState.mappingSetIds ?? []).length > 0 && (
                        <span className="text-tertiary text-sm ml-2">
                            ({(searchState.mappingSetIds ?? []).length} mapping sets selected)
                        </span>
                    )}
                </summary>

                <div className="mt-3">
                    {activeTab === 'search' && (
                        <div className="mb-4 flex items-center gap-2 text-tertiary text-sm">
                            <label htmlFor="label-match" className="whitespace-nowrap">
                                Label matching:
                            </label>
                            <select
                                id="label-match"
                                className="input-default text-sm py-1 w-auto"
                                value={labelMatch}
                                onChange={(event) => setLabelMatch(event.target.value as LabelMatchMode)}
                            >
                                {LABEL_MATCH_ORDER.map((mode) => (
                                    <option key={mode} value={mode}>{LABEL_MATCH_LABELS[mode]}</option>
                                ))}
                            </select>
                        </div>
                    )}

                    <div className="text-tertiary mb-2">
                        Restrict the search to one or more mapping sets
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
            </details>
        </div>
    );
}
