import { useNavigate } from "react-router-dom";
import { SearchInput, SearchMode, initialSearchState } from "../../model/Search";
import { LabelMatchMode, LABEL_MATCH_LABELS, LABEL_MATCH_ORDER, DEFAULT_LABEL_MATCH } from "../../model/LabelMatchMode";
import { CorpusMode, CORPUS_LABELS, DEFAULT_CORPUS, corpusToUrlParam } from "../../model/MappingSetCategory";
import { WeakPredicate, WEAK_PREDICATE_ORDER, WEAK_PREDICATE_LABELS, WEAK_PREDICATE_HINTS, WEAK_PREDICATE_SHORT_LABELS, DEFAULT_WEAK_PREDICATES } from "../../model/WeakPredicate";
import { useCallback, useEffect, useMemo, useState } from "react";
import React from "react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { useQuery } from "@tanstack/react-query";
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

const SINGLE_EXAMPLE = "cataract";
const MULTIPLE_EXAMPLE = "UBERON:0002107\nCataract\nhttp://purl.obolibrary.org/obo/MP_0001289";

/**
 * The entry point. A novice arrives wanting to map one term, or a list of terms, between ontologies.
 * Everything that is not that question is behind "More options", as three groups: where mappings
 * should come from (corpus, with a nested picker for specific mapping sets), the normally-hidden
 * predicates, and label-match semantics. The collapsed summary line names any non-default choice
 * hiding in there, so a search arriving via URL never applies an invisible filter.
 *
 * There is deliberately no result-order control here: ordering is a results decision, made on the
 * results table whose per-column sort popovers write `?sort`. No `?sort` means the backend ranks
 * by relevance (the provenance-led boost, ADR-0027).
 */
export function Search({ searchInput = initialSearchState, showWelcome = false, onClear }: {
    searchInput: SearchInput,
    showWelcome?: boolean,
    // Called after the form's own state is reset, on the results page only. The results-table filters,
    // sort and search options all live in the URL query string, and the table keeps a local copy of
    // its filter inputs; clearing both (query string + a table remount) is the results page's job, not
    // the form's — see MappingResults. Absent on the home page, where the form reset is the whole job.
    onClear?: () => void,
}) {
    const navigate = useNavigate();
    const [searchState, setSearchState] = useState<SearchInput>(searchInput);
    const [searchMode, setSearchMode] = useState<SearchMode>(searchInput.searchMode ?? 'single');

    // Mapping-set selection is held in searchState.mappingSetIds and passed down to the
    // (memoised) MappingSetSelector as controlled state. selectedIds / onSelectionChange
    // are kept referentially stable so the selector — a table of hundreds of rows — is
    // skipped by React.memo while the user types in the search box.
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
    // The normally-hidden predicates the user wants shown (ADR-0035). Local state here, carried into
    // the results URL on submit as `wp`, where the table's useUrlWeakPredicates picks it up — so the
    // suggestions offered here and the rows shown there are filtered by the same selection.
    const [weakPredicates, setWeakPredicates] =
        useState<WeakPredicate[]>(searchInput.includeWeakPredicates ?? DEFAULT_WEAK_PREDICATES);
    // Show obsolete terms (ADR-0041). Local state here, carried into the results URL on submit as `obs`.
    // The one switch governs the results table, the mapping-set picker below and the typeahead, so all
    // three hide or reveal obsolete terms together. Off by default: an ordinary lookup does not want a
    // live term's mappings to dead terms.
    const [includeObsolete, setIncludeObsolete] = useState<boolean>(searchInput.includeObsolete ?? false);

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

    // Pick up from/to prefixes arriving via URL after mount.
    const incomingPrefixKey = (searchInput.subjectPrefixes ?? []).join(",")
        + "|" + (searchInput.objectPrefixes ?? []).join(",");
    useEffect(() => {
        setSubjectPrefixes(searchInput.subjectPrefixes ?? []);
        setObjectPrefixes(searchInput.objectPrefixes ?? []);
    }, [incomingPrefixKey]);

    // Pick up the label match mode and corpus arriving via URL after mount.
    useEffect(() => {
        setLabelMatch(searchInput.labelMatch ?? DEFAULT_LABEL_MATCH);
    }, [searchInput.labelMatch]);
    useEffect(() => {
        setCorpus(searchInput.corpus ?? DEFAULT_CORPUS);
    }, [searchInput.corpus]);
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
        // Carry the ticked predicates through to the result table (ADR-0035). Both-unticked is the
        // default and stays out of the URL.
        for (const predicate of weakPredicates) {
            params.append("wp", predicate);
        }
        // Show obsolete terms (ADR-0041). Off is the default and stays out of the URL.
        if (includeObsolete) {
            params.append("obs", "1");
        }
        const query = params.toString();
        navigate(`/search/${encodeURIComponent(curies)}${query ? `?${query}` : ""}`);
    };

    const handleClear = () => {
        setSearchState({
            userSearchInput: "",
            sanitizedSearchInput: [],
            mappingSetIds: undefined,
            searchMode,
        });
        setSubjectPrefixes([]);
        setObjectPrefixes([]);
        setLabelMatch(DEFAULT_LABEL_MATCH);
        setCorpus(DEFAULT_CORPUS);
        setWeakPredicates(DEFAULT_WEAK_PREDICATES);
        setIncludeObsolete(false);
        // On the results page, hand off to MappingResults to drop the query string (every
        // results-table filter, sort and search option lives there) and remount the table so its local
        // filter inputs re-seed empty instead of debouncing themselves back in — all while keeping the
        // user on the page they're on. On the home page there is no query string and no table, so the
        // form reset above is the whole job and onClear is absent.
        onClear?.();
    };

    const modeButtonClass = (mode: SearchMode) =>
        `px-3 py-1 rounded-md text-base cursor-pointer transition-colors ${
            searchMode === mode
                ? "bg-link-default text-white"
                : "text-neutral-dark hover:text-link-default"
        }`;

    const termCount = searchState.sanitizedSearchInput.length;

    // Names every non-default choice hiding behind the collapsed "More options", so a search
    // arriving via URL never applies an invisible filter.
    const moreOptionsHints: string[] = [];
    if (selectedIds.length > 0) {
        moreOptionsHints.push(
            `${selectedIds.length} mapping ${selectedIds.length === 1 ? 'set' : 'sets'} selected`);
    }
    if (corpus !== DEFAULT_CORPUS) {
        moreOptionsHints.push(`${CORPUS_LABELS[corpus].toLowerCase()} only`);
    }
    if (weakPredicates.length > 0) {
        moreOptionsHints.push(
            `also showing ${weakPredicates.map((code) => WEAK_PREDICATE_SHORT_LABELS[code]).join(', ')}`);
    }
    if (includeObsolete) {
        moreOptionsHints.push("including obsolete terms");
    }
    if (labelMatch !== DEFAULT_LABEL_MATCH) {
        moreOptionsHints.push(`label matching: ${LABEL_MATCH_LABELS[labelMatch].toLowerCase()}`);
    }

    return (
        <div className="search-container">
            {showWelcome && (
                <div className="text-primary">
                    Welcome to the EMBL-EBI OxO Mapping Service
                </div>
            )}
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

            <div>
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
                    // so the "From" selector narrows the suggestions for free. Every option that
                    // narrows the search has to be passed down, not just the ontologies: the
                    // predicate checkboxes, the obsolete switch and the mapping-set selection all
                    // decide what a suggestion can complete to.
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
                        includeObsolete={includeObsolete}
                        mappingSetIds={selectedIds}
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

            <div className="mt-4">
                <div className="text-tertiary mb-2">
                    Between which ontologies? Leave either side empty for "all".
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

            {/* Actions come after both questions they submit, and before the More options
                disclosure so they don't jump when it opens. */}
            <div className="flex gap-2 mt-4">
                <button
                    className="button-primary text-base font-bold px-4 py-1"
                    onClick={() => handleSearch()}
                >
                    Search
                </button>
                <button
                    className="button-secondary text-base px-4 py-1"
                    onClick={handleClear}
                >
                    Clear
                </button>
            </div>

            <details className="mt-6 group">
                <summary className="link-default text-base list-none cursor-pointer select-none">
                    <span className="group-open:hidden">▸ More options</span>
                    <span className="hidden group-open:inline">▾ Fewer options</span>
                    {moreOptionsHints.length > 0 && (
                        <span className="text-tertiary text-sm ml-2">
                            ({moreOptionsHints.join('; ')})
                        </span>
                    )}
                </summary>

                <div className="mt-3 flex flex-col gap-5">
                    {/* Group 1: which mappings should be searched — the corpus category, and
                        beneath it the same question at a finer granularity: specific sets. The
                        full mapping-set table is hundreds of rows, so it sits behind its own
                        nested disclosure: opening "More options" should show compact groups,
                        not a wall of table. */}
                    <div>
                        <CorpusSelector value={corpus} onChange={setCorpus} />
                        <div className="mt-2">
                            <details>
                                <summary className="link-default cursor-pointer select-none">
                                    Choose specific mapping sets…
                                    {selectedIds.length > 0 && (
                                        <span className="text-tertiary text-sm ml-2">
                                            ({selectedIds.length} selected — click a row to toggle)
                                        </span>
                                    )}
                                </summary>
                                <div className="mt-2">
                                    <ThemeProvider theme={tableTheme}>
                                        <MappingSetSelector
                                            selectedIds={selectedIds}
                                            onSelectionChange={handleSelectionChange}
                                            includeObsolete={includeObsolete}
                                        />
                                    </ThemeProvider>
                                </div>
                            </details>
                        </div>
                    </div>
                    {/* Group 2: the normally-hidden predicates (ADR-0035). Here rather than
                        only on the result table because they also decide what the search box
                        above will SUGGEST: with both unticked, an entity whose every mapping is
                        an xref is not offered, since picking it would land on an empty table. */}
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
                        {/* Show obsolete terms (ADR-0041). One switch drives this picker's rows, the
                            results table and the typeahead, so all three hide or reveal them together. */}
                        <label className="flex items-start gap-2 mb-1 mt-2">
                            <input
                                type="checkbox"
                                className="mt-1"
                                checked={includeObsolete}
                                onChange={(event) => setIncludeObsolete(event.target.checked)}
                            />
                            <span>
                                <span className="text-base">Obsolete terms</span>
                                <span className="text-tertiary text-sm block">
                                    Terms an ontology has deprecated. Hidden by default; a live term's
                                    mappings to a dead one are hidden too.
                                </span>
                            </span>
                        </label>
                    </div>
                    {/* Group 3: how free-text terms match labels (ADR-0026). */}
                    <div>
                        <label htmlFor="label-match" className="text-tertiary mb-2 block">
                            Label matching
                        </label>
                        <select
                            id="label-match"
                            className="input-default text-base py-1 w-auto"
                            value={labelMatch}
                            onChange={(event) => setLabelMatch(event.target.value as LabelMatchMode)}
                        >
                            {LABEL_MATCH_ORDER.map((mode) => (
                                <option key={mode} value={mode}>{LABEL_MATCH_LABELS[mode]}</option>
                            ))}
                        </select>
                        <div className="text-tertiary text-sm mt-1">
                            Applies to label searches; a CURIE or IRI always matches exactly.
                        </div>
                    </div>
                </div>
            </details>
        </div>
    );
}
