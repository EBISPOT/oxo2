import {useCallback, useEffect, useState} from "react";
import {useParams, useSearchParams} from "react-router-dom";
import {Search} from "../../components/search/Search";
import {SearchInput} from "../../model/Search";
import {asLabelMatchMode} from "../../model/LabelMatchMode";
import {asCorpusMode} from "../../model/MappingSetCategory";
import {asWeakPredicate, WeakPredicate} from "../../model/WeakPredicate";
import {ThemeProvider, createTheme} from '@mui/material/styles';
import {NormalResultsTable} from "./NormalResultsTable";

const tableTheme = createTheme({
    palette: {
        primary: {
            main: '#d4522c',
            light: '#b75c00',
            dark: '#461901',
            contrastText: '#fff'
        },
        secondary: {
            main: '#525252',
            light: '#99a1af',
            dark: '#373a36',
            contrastText: '#fff'
        },
    },
});

/**
 * Results page. Parses the query from the route, renders the shared search bar, and
 * the compact NormalResultsTable beneath it.
 */
function MappingResults() {
    const { curies } = useParams<{ curies: string }>();
    const [searchParams, setSearchParams] = useSearchParams();
    const mappingSetIds = searchParams.getAll("mapping_set_id");
    // Cross-ontology mapping (ADR-0024): from/to ontology prefixes; "_map" = whole-ontology (no terms).
    const subjectPrefixes = searchParams.getAll("from");
    const objectPrefixes = searchParams.getAll("to");
    // Label match mode (ADR-0026): how free-text label queries match; absent = case-insensitive exact.
    const labelMatch = asLabelMatchMode(searchParams.get("match"));
    // Which asserted corpora to search (ADR-0027); absent = both.
    const corpus = asCorpusMode(searchParams.get("corpus"));
    // The predicate checkboxes (ADR-0035). The results table reads the same `wp` param through
    // useUrlWeakPredicates, so the search box above the table and the rows below it stay in step —
    // and re-submitting the search does not silently drop the ticks.
    const includeWeakPredicates = searchParams
        .getAll("wp")
        .map(asWeakPredicate)
        .filter((predicate): predicate is WeakPredicate => predicate !== null);
    // Show obsolete terms (ADR-0041): the same `obs` param the results table reads, fed back so the form
    // checkbox stays ticked on Back and re-submitting the search keeps it.
    const includeObsolete = searchParams.get("obs") === "1";
    const isMap = curies === "_map";

    const queriesForBackend = isMap ? [] : (curies
        ? curies.split(/[\n,]+/).filter((item) => item.trim() !== "")
        : []);

    const searchInput: SearchInput = {
        userSearchInput: isMap ? "" : (curies || ""),
        sanitizedSearchInput: queriesForBackend,
        mappingSetIds: mappingSetIds.length > 0 ? mappingSetIds : undefined,
        subjectPrefixes: subjectPrefixes.length > 0 ? subjectPrefixes : undefined,
        objectPrefixes: objectPrefixes.length > 0 ? objectPrefixes : undefined,
        labelMatch,
        corpus,
        includeWeakPredicates,
        includeObsolete,
        // A batch of terms is what the multi-term entry produced; keep that mode on Back.
        searchMode: queriesForBackend.length > 1 ? "multiple" : "single",
    };

    // The form's Clear button resets its own fields, then delegates the results side to us. Every
    // results-table filter, sort and search option lives in the query string, so dropping it (keeping
    // the path) resets them to their defaults while leaving the user on the results they're viewing.
    //
    // The table also keeps a *local* copy of its filter inputs (NormalResultsTable's fieldFilters and
    // each ColumnFilterPopover's own state, both seeded once at mount), which a debounce otherwise
    // writes straight back into the URL — so clearing the query alone is undone ~400 ms later. We
    // remount the table (bumping clearNonce → a new React key) so those local copies re-seed empty.
    // The remount must observe the *already-stripped* URL, or it re-seeds the filter and writes it
    // back; react-router commits the query change as an external-store update that does not batch with
    // a plain setState, so stripping and bumping in one handler races (the remount can run first, on
    // the still-filtered URL). Instead we record the request and bump the nonce from an effect, once
    // the query string is observably empty — so the fresh table always mounts against a clean URL.
    const [clearNonce, setClearNonce] = useState(0);
    const [clearRequested, setClearRequested] = useState(false);
    const handleClear = useCallback(() => {
        setSearchParams(new URLSearchParams(), { replace: true });
        setClearRequested(true);
    }, [setSearchParams]);

    const queryString = searchParams.toString();
    useEffect(() => {
        if (clearRequested && queryString === "") {
            setClearNonce((nonce) => nonce + 1);
            setClearRequested(false);
        }
    }, [clearRequested, queryString]);

    return (
        <div>
            <Search searchInput={searchInput} onClear={handleClear} />

            <ThemeProvider theme={tableTheme}>
                <NormalResultsTable
                    key={clearNonce}
                    queries={queriesForBackend}
                    mappingSetIds={mappingSetIds}
                    subjectPrefixes={subjectPrefixes}
                    objectPrefixes={objectPrefixes}
                    labelMatch={labelMatch}
                    corpus={corpus}
                />
            </ThemeProvider>
        </div>
    );
}

export default MappingResults;
