import { Close, KeyboardArrowDown } from "@mui/icons-material";
import { Slider, ThemeProvider } from "@mui/material";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { theme } from "../../app/mui";
import LoadingOverlay from "../../common/LoadingOverlay";
import { Pagination } from "../../common/Pagination";
import Mapping from "../../model/Mapping";
import { getMappings } from "../mapping/slice";
import { getMappingsByEntityIds } from "./slice";

export default function Search() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const results = useAppSelector((state) => state.search.mappings);
  const otherMappings = useAppSelector((state) => state.mapping.otherMappings);
  const paging = useAppSelector((state) => state.search.pagination);
  const facets = useAppSelector((state) => state.search.facets);
  const facetKeys = Object.keys(facets);
  const loadingSearch = useAppSelector((state) => state.search.loadingSearch);
  const loadingWidget = useAppSelector(
    (state) => state.mapping.loadingMappings
  );

  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState<string>(
    searchParams.get("ids")?.replaceAll(",", "\n") || ""
  );
  const [facetFields, setFacetFields] = useState<any>({});
  var facetFieldsCopy: any = {};
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);

  const [minValue, setMinValue] = useState<number>(0);
  const [maxValue, setMaxValue] = useState<number>(1);
  const handleChange = (event: Event, newValue: number | number[]) => {
    const newVal = newValue as number[];
    setMinValue(newVal[0]);
    setMaxValue(newVal[1]);
  };

  const [widgetParams, setWidgetParams] = useState<URLSearchParams>(
    new URLSearchParams()
  );
  const [allJustifs, setAllJustifs] = useState<{
    mapping: {
      subject: string;
      predicate: string;
      object: string;
    };
    justif: {
      uri: string;
      confidence: number;
      provider: string;
      subjectMatches: string;
      objectMatches: string;
      matchStrings: string;
    };
    otherJustifs: {
      uri: string;
      confidence: number;
      provider: string;
      subjectMatches: string;
      objectMatches: string;
      matchStrings: string;
    }[];
  }>({
    mapping: {
      subject: "",
      predicate: "",
      object: "",
    },
    justif: {
      uri: "",
      confidence: -1,
      provider: "",
      subjectMatches: "",
      objectMatches: "",
      matchStrings: "",
    },
    otherJustifs: [],
  });

  useEffect(() => {
    if (!widgetParams.entries().next().done) {
      dispatch(
        getMappings({
          subjectId: widgetParams.get("subject_id"),
          predicateId: widgetParams.get("predicate_id"),
          objectId: widgetParams.get("object_id"),
          limit: rowsPerPage,
          page: page + 1,
        })
      );
    }
  }, [widgetParams, dispatch, page, rowsPerPage]);

  useEffect(() => {
    const justifs: any[] = [];
    otherMappings.forEach((mapping: Mapping) => {
      justifs.push({
        uri: mapping.getJustification(),
        confidence: mapping.getConfidence(),
        provider: mapping.getProvider(),
        subjectMatches: mapping.getSubjectMatchFields()
          ? mapping.getSubjectMatchFields().join(", ")
          : "",
        objectMatches: mapping.getObjectMatchFields()
          ? mapping.getObjectMatchFields().join(", ")
          : "",
        matchStrings: mapping.getMatchStrings()
          ? mapping.getMatchStrings().join(", ")
          : "",
      });
    });
    setAllJustifs({
      mapping: {
        subject: widgetParams.get("subject_id") || "",
        predicate: widgetParams.get("predicate_id") || "",
        object: widgetParams.get("object_id") || "",
      },
      justif: {
        uri: widgetParams.get("justif_uri") || "",
        confidence: parseFloat(widgetParams.get("justif_confidence") || "0"),
        provider: widgetParams.get("justif_provider") || "",
        subjectMatches: widgetParams.get("justif_subject_matches") || "",
        objectMatches: widgetParams.get("justif_object_matches") || "",
        matchStrings: widgetParams.get("justif_match_strings") || "",
      },
      otherJustifs: justifs,
    });
  }, [otherMappings, widgetParams]);

  useEffect(() => {
    dispatch(
      getMappingsByEntityIds({
        entityIds: searchParams.get("ids")?.split(",") || [],
        facetIds: facetFields,
        limit: rowsPerPage,
        page: page + 1,
      })
    );
  }, [dispatch, searchParams, facetFields, page, rowsPerPage]);

  return (
    <div>
      <main className="container mx-auto">
        <div className="flex flex-row gap-4 mt-8 mb-4">
          <div className="w-full">
            <textarea
              id="home-search"
              rows={4}
              style={{ resize: "vertical", minHeight: "5rem" }}
              placeholder={"Search OxO..."}
              className="input-default text-lg"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
              }}
            />
          </div>
          <button
            className="button-primary text-lg font-bold"
            onClick={() => {
              if (query) {
                setSearchParams({
                  ids: query
                    .split(/[\n,]+/)
                    .filter((id) => id !== "")
                    .map((id) => id.trim())
                    .join(","),
                });
              }
            }}
          >
            Search
          </button>
        </div>
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-1 mb-4">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 text-neutral-black overflow-x-auto">
              <div className="font-bold text-neutral-dark text-sm mb-4">
                {`Showing ${
                  paging.total_items > rowsPerPage
                    ? rowsPerPage
                    : paging.total_items
                } `}
                from a total&nbsp;of&nbsp;{paging.total_items}
              </div>
              {facetKeys.length > 0 ? (
                <div className="text-neutral-black">
                  {facetKeys.map((facetKey) => {
                    const facetValue = facets[facetKey];
                    return (
                      <div key={facetKey}>
                        <div className="font-semibold text-lg mb-2 capitalize">
                          {facetKey.replaceAll("_", " ")}
                        </div>
                        <fieldset className="mb-4">
                          {facetKey && Object.keys(facetValue).length > 0
                            ? Object.keys(facetValue).map((facetSubKey) => {
                                facetFieldsCopy = { ...facetFields };
                                return (
                                  <label
                                    key={facetSubKey}
                                    htmlFor={facetSubKey}
                                    className="block p-1 w-fit whitespace-nowrap"
                                  >
                                    <input
                                      type="checkbox"
                                      id={facetSubKey}
                                      className="invisible hidden peer"
                                      checked={
                                        !!facetFields[facetKey] &&
                                        !!Array.isArray(
                                          facetFields[facetKey]
                                        ) &&
                                        !!facetFields[facetKey].find(
                                          (key: string) => key === facetSubKey
                                        )
                                      }
                                      onChange={(e) => {
                                        if (
                                          facetFieldsCopy[facetKey] &&
                                          Array.isArray(
                                            facetFieldsCopy[facetKey]
                                          )
                                        ) {
                                          if (
                                            facetFieldsCopy[facetKey].find(
                                              (key: string) =>
                                                key === facetSubKey
                                            )
                                          ) {
                                            const facetCopy = facetFieldsCopy[
                                              facetKey
                                            ].filter(
                                              (key: string) =>
                                                key !== facetSubKey
                                            );
                                            if (facetCopy.length === 0) {
                                              facetFieldsCopy[facetKey] =
                                                undefined;
                                            } else {
                                              facetFieldsCopy[facetKey] =
                                                facetCopy;
                                            }
                                          }
                                        } else {
                                          facetFieldsCopy[facetKey] = [
                                            facetSubKey,
                                          ];
                                        }
                                        setFacetFields(facetFieldsCopy);
                                      }}
                                    />
                                    <span className="input-checkbox mr-4" />
                                    <span className="mr-4">
                                      {facetSubKey.substring(
                                        facetSubKey.lastIndexOf("/") + 1
                                      )}
                                      &nbsp;&#40;
                                      {facetValue[facetSubKey]}&#41;
                                    </span>
                                  </label>
                                );
                              })
                            : null}
                        </fieldset>
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          </div>
          <div className="col-span-3">
            <div className="flex flex-row justify-between mb-4">
              <div className="basis-1/4 flex flex-col">
                <label>Confidence</label>
                <div className="flex flex-row items-center">
                  <div className="text-sm font-bold text-neutral-default pr-5">
                    {minValue.toFixed(1)}
                  </div>
                  <ThemeProvider theme={theme}>
                    <Slider
                      value={[minValue, maxValue]}
                      onChange={handleChange}
                      valueLabelDisplay="off"
                      disableSwap
                      min={0}
                      step={0.1}
                      max={1}
                    />
                  </ThemeProvider>
                  <div className="text-sm font-bold text-neutral-default pl-5">
                    {maxValue.toFixed(1)}
                  </div>
                </div>
              </div>
              <div className="justify-end flex flex-row gap-4">
                <div className="flex group relative text-md">
                  <select className="input-default appearance-none pr-7 z-20 bg-transparent">
                    <option>Download as...</option>
                    <option>CSV</option>
                    <option>TSV</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
                <div className="flex group relative text-md">
                  <label className="self-center px-3">Show</label>
                  <select
                    value={rowsPerPage}
                    className="input-default appearance-none pr-7 z-20 bg-transparent"
                    onChange={(e) => {
                      const rows = parseInt(e.target.value);
                      setRowsPerPage((prev) => {
                        if (rows !== prev) {
                          setPage(0);
                        }
                        return rows;
                      });
                    }}
                  >
                    <option value={10}>10</option>
                    <option value={25}>25</option>
                    <option value={100}>100</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
              </div>
            </div>
            {paging.total_items > 0 && results.length > 0 ? (
              <div className="mb-4">
                <Pagination
                  page={page}
                  onPageChange={setPage}
                  dataCount={paging.total_items}
                  rowsPerPage={rowsPerPage}
                />
                <div className="flex flex-row text-neutral-default font-bold gap-3 my-4">
                  <div className="basis-4/12">Subject</div>
                  <div className="basis-3/12">Predicate</div>
                  <div className="basis-5/12">Object</div>
                </div>
                {results.map((searchResult: Mapping) => {
                  return (
                    <div
                      key={searchResult.getMappingId()}
                      className="flex flex-row items-center gap-3 mb-4"
                    >
                      <div className="basis-4/12 bg-yellow-300 rounded-lg px-4 py-2">
                        <strong>{searchResult.getSubjectCurie()}</strong>
                        <br />
                        {searchResult.getSubjectLabel()
                          ? `(${searchResult.getSubjectLabel()})`
                          : ""}
                        <br />
                        {searchResult.getSubjectCategory()}
                      </div>
                      <div className="basis-3/12 border-2 border-neutral-black rounded-lg px-4 py-2">
                        <span>{searchResult.getPredicateCurie()}</span>
                        <br />
                        {searchResult.getPredicateModifier()
                          ? "Modifier: " + searchResult.getPredicateModifier()
                          : ""}
                        <br />
                      </div>
                      <div className="basis-4/12 bg-yellow-300 rounded-lg px-4 py-2">
                        <strong>{searchResult.getObjectCurie()}</strong>
                        <br />
                        {searchResult.getObjectLabel()
                          ? `(${searchResult.getObjectLabel()})`
                          : ""}
                        <br />
                        {searchResult.getObjectCategory()}
                      </div>
                      <div className="basis-1/12 mt-4 grid grid-cols-2 justify-items-center text-xl">
                        <div
                          className="w-fit cursor-pointer"
                          onClick={() => {
                            const queryObject = new URLSearchParams({
                              subject_id: searchResult.getSubjectCurie()
                                ? searchResult.getSubjectCurie()
                                : searchResult.getSubjectId(),
                              predicate_id: searchResult.getPredicateCurie()
                                ? searchResult.getPredicateCurie()
                                : searchResult.getPredicateId(),
                              object_id: searchResult.getObjectCurie()
                                ? searchResult.getObjectCurie()
                                : searchResult.getObjectId(),
                              justif_uri: searchResult.getJustification()
                                ? searchResult.getJustification()
                                : "",
                              justif_confidence: searchResult.getConfidence()
                                ? searchResult.getConfidence().toString()
                                : "",
                              justif_provider: searchResult.getProvider()
                                ? searchResult.getProvider()
                                : "",
                              justif_subject_matches:
                                searchResult.getSubjectMatchFields()
                                  ? searchResult
                                      .getSubjectMatchFields()
                                      .join(", ")
                                  : "",
                              justif_object_matches:
                                searchResult.getObjectMatchFields()
                                  ? searchResult
                                      .getObjectMatchFields()
                                      .join(", ")
                                  : "",
                              justif_match_strings:
                                searchResult.getMatchStrings()
                                  ? searchResult.getMatchStrings().join(", ")
                                  : "",
                            });
                            setWidgetParams(queryObject);
                          }}
                        >
                          <i
                            title="Justifications"
                            className="icon icon-common icon-info text-link-hover"
                          />
                        </div>
                        <div
                          className="w-fit cursor-pointer"
                          onClick={() => {
                            navigate(
                              `/mapping/${encodeURIComponent(
                                searchResult.getMappingId()
                              )}`
                            );
                          }}
                        >
                          <i
                            title="View"
                            className="icon icon-common icon-eye text-link-default"
                          />
                        </div>
                      </div>
                    </div>
                  );
                })}
                <Pagination
                  page={page}
                  onPageChange={setPage}
                  dataCount={paging.total_items}
                  rowsPerPage={rowsPerPage}
                />
              </div>
            ) : (
              <div className="text-xl text-neutral-black font-bold">
                No results!
              </div>
            )}
          </div>
        </div>
      </main>
      <div
        className={`fixed top-0 right-0 z-30 w-96 h-full transition-transform bg-neutral-light shadow-card p-6 ${
          !widgetParams.entries().next().done
            ? "translate-x-0"
            : "translate-x-96"
        }`}
      >
        <div className="flex flex-row items-center gap-4 mb-4">
          <button
            type="button"
            onClick={() => setWidgetParams(new URLSearchParams())}
          >
            <Close />
          </button>
          <div className="text-neutral-default font-bold">Justification</div>
        </div>
        {allJustifs.mapping.subject ? (
          <div
            title={allJustifs.mapping.subject}
            className="bg-yellow-300 px-3 py-2 m-1 rounded-lg"
          >
            {allJustifs.mapping.subject}
          </div>
        ) : null}
        {allJustifs.mapping.predicate ? (
          <div
            title={allJustifs.mapping.predicate}
            className="bg-white px-3 py-2 m-1 rounded-lg truncate"
          >
            {allJustifs.mapping.predicate.substring(
              allJustifs.mapping.predicate.lastIndexOf("#") + 1
            )}
          </div>
        ) : null}
        {allJustifs.mapping.object ? (
          <div
            title={allJustifs.mapping.object}
            className="bg-yellow-300 px-3 py-2 m-1 rounded-lg"
          >
            {allJustifs.mapping.object}
          </div>
        ) : null}
        {allJustifs.justif.uri ? (
          <div className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4 my-4">
            <div
              title={allJustifs.justif.uri}
              className="text-xl font-bold mb-2 truncate"
            >
              {allJustifs.justif.uri.substring(
                allJustifs.justif.uri.lastIndexOf("/") + 1
              )}
            </div>
            <ul className="list-disc list-inside pl-2">
              {allJustifs.justif.confidence ? (
                <li>Confidence:&nbsp;{allJustifs.justif.confidence}</li>
              ) : null}
              {allJustifs.justif.provider ? (
                <li>Provider:&nbsp;{allJustifs.justif.provider}</li>
              ) : null}
              {allJustifs.justif.subjectMatches ? (
                <li>
                  Subject Match Field:&nbsp;{allJustifs.justif.subjectMatches}
                </li>
              ) : null}
              {allJustifs.justif.objectMatches ? (
                <li>
                  Object Match Field:&nbsp;{allJustifs.justif.objectMatches}
                </li>
              ) : null}
              {allJustifs.justif.matchStrings ? (
                <li>Match String:&nbsp;{allJustifs.justif.matchStrings}</li>
              ) : null}
            </ul>
          </div>
        ) : null}
        {allJustifs.otherJustifs.length > 1 ? (
          <div className="text-neutral-default font-bold m-2">
            Other Justifications
          </div>
        ) : null}
        {allJustifs.otherJustifs.map((justif) => {
          if (allJustifs.justif.uri !== justif.uri) {
            return (
              <div
                key={justif.uri}
                className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4 my-4"
              >
                <div
                  title={justif.uri}
                  className="text-xl font-bold mb-2 truncate"
                >
                  {justif.uri.substring(justif.uri.lastIndexOf("/") + 1)}
                </div>
                <ul className="list-disc list-inside pl-2">
                  {justif.confidence ? (
                    <li>Confidence:&nbsp;{justif.confidence}</li>
                  ) : null}
                  {justif.provider ? (
                    <li>Provider:&nbsp;{justif.provider}</li>
                  ) : null}
                  {justif.subjectMatches ? (
                    <li>Subject Match Field:&nbsp;{justif.subjectMatches}</li>
                  ) : null}
                  {justif.objectMatches ? (
                    <li>Object Match Field:&nbsp;{justif.objectMatches}</li>
                  ) : null}
                  {justif.matchStrings ? (
                    <li>Match String:&nbsp;{justif.matchStrings}</li>
                  ) : null}
                </ul>
              </div>
            );
          }
          return null;
        })}
        {loadingWidget ? (
          <div className="text-center my-3">
            <div className="spinner-default animate-spin w-10 h-10" />
          </div>
        ) : null}
      </div>
      <div
        className={`fixed top-0 right-0 backdrop-blur h-full w-full ${
          !widgetParams.entries().next().done ? "z-20" : "z-[-1]"
        }`}
        onClick={() => setWidgetParams(new URLSearchParams())}
      />
      {loadingSearch ? (
        <LoadingOverlay message="Loading search results..." />
      ) : null}
    </div>
  );
}
