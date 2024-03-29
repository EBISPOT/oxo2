import { Close, KeyboardArrowDown } from "@mui/icons-material";
import { Slider, ThemeProvider } from "@mui/material";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { theme } from "../../app/mui";
import LoadingOverlay from "../../common/LoadingOverlay";
import { Pagination } from "../../common/Pagination";
import Mapping from "../../model/Mapping";
import { getMappings } from "../mapping/slice";
import { getMappingsByEntities } from "./slice";

export default function Search({ appRef }: { appRef: any }) {
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
  const refQuery = useRef(appRef.current.searchQuery);
  const [query, setQuery] = useState<string>(
    searchParams.get("ids")?.replaceAll(",", "\n") || ""
  );
  const [facetFields, setFacetFields] = useState<any>({});
  let facetFieldsCopy: any = {};
  const [page, setPage] = useState<number>(
    paging.page_number > 0 ? paging.page_number - 1 : 0
  );
  const [rowsPerPage, setRowsPerPage] = useState<number>(
    paging.total_pages > 1
      ? results.length
      : paging.total_items <= 10
      ? 10
      : paging.total_items <= 25
      ? 25
      : 100
  );
  const [hideFilters, setHideFilters] = useState<boolean>(true);

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
        uri: mapping.getJustificationId(),
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
    if (
      searchParams.get("ids") &&
      searchParams.get("ids") === refQuery.current
    ) {
      refQuery.current = "";
    } else {
      dispatch(
        getMappingsByEntities({
          entityIds: searchParams.get("ids")?.split(",") || [],
          facetIds: facetFields,
          confidence: { min: minValue, max: maxValue },
          limit: rowsPerPage,
          page: page + 1,
        })
      );
    }
  }, [
    dispatch,
    refQuery,
    searchParams,
    facetFields,
    minValue,
    maxValue,
    page,
    rowsPerPage,
  ]);

  useEffect(() => {
    const arc = appRef.current;
    return () => {
      arc.searchQuery = searchParams.get("ids");
    };
  });

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
                setPage(0);
                setFacetFields({});
              }
            }}
          >
            Search
          </button>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-4 lg:gap-8">
          <div
            className={`fixed top-0 left-0 mb-4 z-30 lg:z-0 lg:static lg:col-span-1 bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 text-neutral-black overflow-x-auto h-full lg:h-fit lg:translate-x-0 transition-transform ${
              hideFilters ? "-translate-x-full" : "translate-x-0"
            }`}
          >
            <div className="flex flex-row items-center justify-between mb-4">
              <div className="font-bold text-neutral-dark text-sm">
                {`Showing ${
                  paging.total_items > rowsPerPage
                    ? rowsPerPage
                    : paging.total_items
                } `}
                from a total&nbsp;of&nbsp;{paging.total_items}
              </div>
              <button
                className="lg:hidden"
                type="button"
                onClick={() => {
                  setHideFilters(true);
                }}
              >
                <Close />
              </button>
            </div>
            {facetKeys.length > 0 ? (
              <div className="text-neutral-black">
                {facetKeys.map((facetKey) => {
                  const facetValue = facets[facetKey];
                  if (facetKey.toLowerCase() !== "confidence") {
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
                                        setPage(0);
                                      }}
                                    />
                                    <span className="input-checkbox mr-4" />
                                    <span className="mr-4">
                                      {facetSubKey}
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
                  } else return null;
                })}
              </div>
            ) : null}
          </div>
          <div className="lg:col-span-3">
            <div className="flex flex-col-reverse lg:flex-row justify-between mb-4">
              <div className="lg:basis-1/3 flex flex-col">
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
              <div className="lg:justify-end justify-between flex flex-row items-center gap-4 mb-2">
                <button
                  className="lg:hidden button-secondary"
                  type="button"
                  onClick={() => {
                    setHideFilters(false);
                  }}
                >
                  Filters
                </button>
                <div className="flex group relative text-md">
                  <select className="input-default appearance-none pr-7 z-10 bg-transparent">
                    <option>Download as...</option>
                    <option>CSV</option>
                    <option>TSV</option>
                  </select>
                  <div className="absolute right-2 top-2 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
                <div className="flex group relative text-md items-center">
                  <label className="px-3">Show</label>
                  <select
                    value={rowsPerPage}
                    className="input-default appearance-none pr-7 z-10 bg-transparent"
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
                  <div className="absolute right-2 top-2 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
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
                {results.map((searchResult: Mapping) => {
                  return (
                    <div
                      key={searchResult.getMappingId()}
                      className="flex flex-col items-stretch items-center lg:flex-row my-4"
                    >
                      <div
                        className="flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none bg-yellow-300 cursor-pointer hover:underline"
                        onClick={() => {
                          navigate(
                            `/entity/${encodeURIComponent(
                              searchResult.getSubjectCurie()
                            )}`
                          );
                        }}
                      >
                        <div className="text-center font-bold">
                          {searchResult.getSubjectCurie()}
                        </div>
                        <div
                          title={searchResult.getSubjectLabel()}
                          className="text-center truncate"
                        >
                          {searchResult.getSubjectLabel() || ""}
                        </div>
                      </div>
                      <div className="w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-y-neutral-light lg:border-l-yellow-300" />
                      <div className="flex-none flex flex-col justify-center lg:min-w-0 lg:h-[5rem] bg-neutral-light px-6 py-3 rounded-2xl lg:rounded-none">
                        <div
                          title={searchResult.getPredicateId()}
                          className="text-center font-bold"
                        >
                          {searchResult.getPredicateCurie()}
                        </div>
                        <div
                          title={searchResult.getPredicateLabel()}
                          className="text-center truncate"
                        >
                          {searchResult.getPredicateLabel() || ""}
                        </div>
                      </div>
                      <div className="w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-l-neutral-light lg:border-y-yellow-300" />
                      <div
                        className="flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none bg-yellow-300 cursor-pointer hover:underline"
                        onClick={() => {
                          navigate(
                            `/entity/${encodeURIComponent(
                              searchResult.getObjectCurie()
                            )}`
                          );
                        }}
                      >
                        <div className="text-center font-bold">
                          {searchResult.getObjectCurie()}
                        </div>
                        <div
                          title={searchResult.getObjectLabel()}
                          className="text-center truncate"
                        >
                          {searchResult.getObjectLabel() || ""}
                        </div>
                      </div>
                      <div className="flex-0 grid grid-cols-2 m-3 gap-3 justify-items-center content-center text-xl">
                        <div
                          className="cursor-pointer self-center"
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
                              justif_uri: searchResult.getJustificationId()
                                ? searchResult.getJustificationId()
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
                          className="link-default text-sm font-bold cursor-pointer text-center self-center"
                          onClick={() => {
                            navigate(
                              `/mapping/${encodeURIComponent(
                                searchResult.getMappingId()
                              )}`
                            );
                          }}
                        >
                          View
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
        className={`fixed top-0 right-0 z-20 w-96 h-full transition-transform bg-neutral-light shadow-card p-6 ${
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
          !widgetParams.entries().next().done ? "z-10" : "hidden"
        }`}
        onClick={() => setWidgetParams(new URLSearchParams())}
      />
      {loadingSearch ? (
        <LoadingOverlay message="Loading search results..." />
      ) : null}
    </div>
  );
}
