import { Close, KeyboardArrowDown } from "@mui/icons-material";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import { Pagination } from "../../common/Pagination";
import { getMappingsByEntityIds } from "./slice";

export default function Search() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const results = useAppSelector((state) => state.search.mappings);
  const paging = useAppSelector((state) => state.search.pagination);
  const facets = useAppSelector((state) => state.search.facets);
  const facetKeys = Object.keys(facets);
  const loadingSearch = useAppSelector((state) => state.search.loadingSearch);

  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState<string>(
    searchParams.get("ids")?.replaceAll(",", "\n") || ""
  );
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);

  const [openJustif, setOpenJustif] = useState<boolean>(false);
  const [justif, setJustif] = useState<{
    lexicalMatch: {
      confidence: number;
      provider: string;
      subjectField: string[];
      objectField: string[];
      string: string[];
    };
    curatedMatch: {
      provider: string;
      author: string[];
    };
  }>({
    lexicalMatch: {
      confidence: 0,
      provider: "",
      subjectField: [],
      objectField: [],
      string: [],
    },
    curatedMatch: {
      provider: "",
      author: [],
    },
  });

  useEffect(() => {
    dispatch(
      getMappingsByEntityIds({
        entityIds: searchParams.get("ids")?.split(",") || [],
        limit: rowsPerPage,
        page: page + 1,
      })
    );
  }, [dispatch, searchParams, page, rowsPerPage]);

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
                                      onChange={(e) => {
                                        console.log(facetSubKey + " clicked!");
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
              <div className="flex flex-col justify-center">
                <label>Confidence</label>
                <input
                  type="range"
                  min="1"
                  max="3"
                  className="apperance-none bg-transparent accent-petrol-default cursor-pointer"
                  defaultValue={2}
                />
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
                <div className="flex flex-row text-neutral-default font-bold gap-4 my-4">
                  <div className="basis-4/12">Subject</div>
                  <div className="basis-3/12">Predicate</div>
                  <div className="basis-5/12">Object</div>
                </div>
                {results.map((searchResult) => {
                  return (
                    <div
                      key={randomString()}
                      className="flex flex-row items-start gap-4 mb-4 break-all"
                    >
                      <div className="basis-4/12 bg-yellow-default rounded-lg px-4 py-2">
                        <i>{searchResult.getSubjectCurie()}</i>
                        <br />
                        {searchResult.getSubjectLabel()
                          ? `"${searchResult.getSubjectLabel()}"`
                          : ""}
                        <br />
                        {searchResult.getSubjectCategory()}
                      </div>
                      <div className="basis-3/12 border-2 border-neutral-black rounded-lg px-4 py-2">
                        <i>{searchResult.getPredicateId()}</i>
                        <br />
                        {searchResult.getPredicateModifier()
                          ? "Modifier: " + searchResult.getPredicateModifier()
                          : ""}
                      </div>
                      <div className="basis-4/12 bg-yellow-default rounded-lg px-4 py-2">
                        <i>{searchResult.getObjectCurie()}</i>
                        <br />
                        {searchResult.getObjectLabel()
                          ? `"${searchResult.getObjectLabel()}"`
                          : ""}
                        <br />
                        {searchResult.getObjectCategory()}
                      </div>
                      <div className="basis-1/12 self-center grid grid-cols-2 justify-items-center text-xl">
                        <div
                          className="w-fit cursor-pointer"
                          onClick={() => {
                            setJustif({
                              lexicalMatch: {
                                confidence: searchResult.getConfidence(),
                                provider: searchResult.getProvider(),
                                subjectField:
                                  searchResult.getSubjectMatchFields(),
                                objectField:
                                  searchResult.getObjectMatchFields(),
                                string: searchResult.getMatchStrings(),
                              },
                              curatedMatch: {
                                provider: searchResult.getProvider(),
                                author: searchResult.getAuthorLabels(),
                              },
                            });
                            setOpenJustif(true);
                          }}
                        >
                          <i
                            title="Info"
                            className="icon icon-common icon-info"
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
                            className="icon icon-common icon-eye"
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
          openJustif ? "translate-x-0" : "translate-x-96"
        }`}
      >
        <div className="flex flex-row items-center gap-4 mb-4">
          <button type="button" onClick={() => setOpenJustif(false)}>
            <Close />
          </button>
          <div className="text-neutral-default font-bold">Justification</div>
        </div>
        <div className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4 mb-4">
          <div className="text-xl font-bold mb-2">Lexical Match</div>
          <ul className="list-disc list-inside pl-2">
            <li>Confidence: {justif.lexicalMatch.confidence}</li>
            <li>Provider: {justif.lexicalMatch.provider}</li>
            <li>
              Subject match field: {justif.lexicalMatch.subjectField?.join(", ")}
            </li>
            <li>
              Object match field: {justif.lexicalMatch.objectField?.join(", ")}
            </li>
            <li>Match string: {justif.lexicalMatch.string?.join(", ")}</li>
          </ul>
        </div>
        <div className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4">
          <div className="text-xl text-neutral-black font-bold mb-2">
            Human Curated Match
          </div>
          <ul className="list-disc list-inside pl-2">
            <li>Provider: {justif.curatedMatch.provider}</li>
            <li>Author: {justif.curatedMatch.author?.join(", ")}</li>
          </ul>
        </div>
      </div>
      <div
        className={`fixed top-0 right-0 backdrop-blur h-full w-full ${
          openJustif ? "z-20" : "z-[-1]"
        }`}
        onClick={() => setOpenJustif(false)}
      />
      {loadingSearch ? (
        <LoadingOverlay message="Loading search results..." />
      ) : null}
    </div>
  );
}
