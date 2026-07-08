import {useParams, useSearchParams} from "react-router-dom";
import {Search} from "../../components/search/Search";
import {AdvancedFieldQuery, SearchInput} from "../../model/Search";
import {asLabelMatchMode} from "../../model/LabelMatchMode";
import {asCorpusMode} from "../../model/MappingSetCategory";
import {asSortMode} from "../../model/SortMode";
import {ADVANCED_FIELD_NAMES} from "../../model/AdvancedFields";
import {ThemeProvider, createTheme} from '@mui/material/styles';
import {NormalResultsTable} from "./NormalResultsTable";
import {AdvancedResultsTable} from "./AdvancedResultsTable";

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
 * picks the results table: the compact NormalResultsTable for the default "Search"
 * tab, or the full-width AdvancedResultsTable (unchanged legacy table) for the
 * "Advanced" tab. The two tables are deliberately separate so the Advanced surface
 * keeps every column and its inline per-column filtering.
 */
function MappingResults() {
    const { curies } = useParams<{ curies: string }>();
    const [searchParams] = useSearchParams();
    const mappingSetIds = searchParams.getAll("mapping_set_id");
    // Cross-ontology mapping (ADR-0024): from/to ontology prefixes; "_map" = whole-ontology (no terms).
    const subjectPrefixes = searchParams.getAll("from");
    const objectPrefixes = searchParams.getAll("to");
    // Label match mode (ADR-0026): how free-text label queries match; absent = case-insensitive exact.
    const labelMatch = asLabelMatchMode(searchParams.get("match"));
    // Which asserted corpora to search (ADR-0027); absent = both.
    const corpus = asCorpusMode(searchParams.get("corpus"));
    // The Sort-by control reads the same `sort` param the table's column popovers write, so the two
    // can never disagree. A per-column sort the control can't represent reads back as "Best match".
    const sortBy = asSortMode(searchParams.getAll("sort"));
    const isAdvanced = curies === "_advanced";
    const isMap = curies === "_map";

    const advancedFieldQueries: AdvancedFieldQuery[] = isAdvanced
        ? searchParams
              .getAll("af")
              .map((s) => {
                  const eq = s.indexOf("=");
                  if (eq < 0) return null;
                  const field = s.slice(0, eq);
                  const value = s.slice(eq + 1);
                  if (!ADVANCED_FIELD_NAMES.has(field) || value === "") return null;
                  return { field, value };
              })
              .filter((x): x is AdvancedFieldQuery => x !== null)
        : [];

    const queriesForBackend = (isAdvanced || isMap) ? [] : (curies
        ? curies.split(/[\n,]+/).filter((item) => item.trim() !== "")
        : []);

    const searchInput: SearchInput = {
        userSearchInput: (isAdvanced || isMap) ? "" : (curies || ""),
        sanitizedSearchInput: queriesForBackend,
        mappingSetIds: mappingSetIds.length > 0 ? mappingSetIds : undefined,
        advancedFieldQueries: isAdvanced && advancedFieldQueries.length > 0 ? advancedFieldQueries : undefined,
        activeTab: isAdvanced ? "advanced" : "search",
        subjectPrefixes: subjectPrefixes.length > 0 ? subjectPrefixes : undefined,
        objectPrefixes: objectPrefixes.length > 0 ? objectPrefixes : undefined,
        labelMatch,
        corpus,
        sortBy,
        // A batch of terms is what the multi-term entry produced; keep that mode on Back.
        searchMode: queriesForBackend.length > 1 ? "multiple" : "single",
    };

    return (
        <div>
            <Search searchInput={searchInput} />

            <ThemeProvider theme={tableTheme}>
                {isAdvanced ? (
                    <AdvancedResultsTable
                        advancedFieldQueries={advancedFieldQueries}
                        mappingSetIds={mappingSetIds}
                    />
                ) : (
                    <NormalResultsTable
                        queries={queriesForBackend}
                        mappingSetIds={mappingSetIds}
                        subjectPrefixes={subjectPrefixes}
                        objectPrefixes={objectPrefixes}
                        labelMatch={labelMatch}
                        corpus={corpus}
                    />
                )}
            </ThemeProvider>
        </div>
    );
}

export default MappingResults;
