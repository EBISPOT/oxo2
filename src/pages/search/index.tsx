import { Close, KeyboardArrowDown } from "@mui/icons-material";
import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import { Pagination } from "../../common/Pagination";
import { getMappingsAll } from "./slice";

export default function Search() {
  const dispatch = useAppDispatch();
  const results = useAppSelector((state) => state.search.mappings);
  const paging = useAppSelector((state) => state.search.pagination);
  const loadingSearch = useAppSelector((state) => state.search.loadingSearch);

  const location = useLocation();
  const passedQuery =
    location.state?.search && Array.isArray(location.state.search)
      ? location.state.search.join("\n")
      : "";

  const [query, setQuery] = useState<string>(passedQuery);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(25);
  const [currentCount, setCurrentCount] = useState<number>(0);

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
      getMappingsAll({
        limit: rowsPerPage,
        page: page + 1,
      })
    );
  }, [dispatch, page, rowsPerPage]);

  const [maxPage, setMaxPage] = useState<number>(0);

  console.log(maxPage);
  if (maxPage === page && paging.next !== "") {
    setCurrentCount(currentCount + results.length);
    setMaxPage(maxPage + 1);
  }

  //
  // sample data
  //
  const providerFacet = ["Monarch initiative", "EBI"];
  const justificationFacet = ["Lexical", "Manual curation"];
  const prefixFacet = ["MONDO", "HP"];
  const setFacet = ["Mapping Set 1", "Mapping Set 2"];

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
          <button className="button-primary text-lg font-bold">Search</button>
        </div>
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-1 mb-4">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 text-neutral-black">
              <div className="font-bold text-neutral-dark text-sm mb-4">
                {`Showing ${
                  currentCount > rowsPerPage ? rowsPerPage : currentCount
                } from a total of ${
                  paging.next === "" ? currentCount : currentCount + "+"
                }`}
              </div>
              <div className="font-semibold text-lg mb-2">Mapping Provider</div>
              <fieldset className="mb-4">
                {providerFacet.map((provider) => {
                  return (
                    <label key={randomString()} className="block p-1 w-fit">
                      <input
                        type="checkbox"
                        className="invisible hidden peer"
                      />
                      <span className="input-checkbox mr-4" />
                      <span className="capitalize mr-4">{provider}</span>
                    </label>
                  );
                })}
              </fieldset>
              <div className="font-semibold text-lg mb-2">
                Mapping Justification
              </div>
              <fieldset className="mb-4">
                {justificationFacet.map((justification) => {
                  return (
                    <label key={randomString()} className="block p-1 w-fit">
                      <input
                        type="checkbox"
                        className="invisible hidden peer"
                      />
                      <span className="input-checkbox mr-4" />
                      <span className="capitalize mr-4">{justification}</span>
                    </label>
                  );
                })}
              </fieldset>
              <div className="font-semibold text-lg mb-2">Object Prefix</div>
              <fieldset className="mb-4">
                {prefixFacet.map((prefix) => {
                  return (
                    <label key={randomString()} className="block p-1 w-fit">
                      <input
                        type="checkbox"
                        className="invisible hidden peer"
                      />
                      <span className="input-checkbox mr-4" />
                      <span className="capitalize mr-4">{prefix}</span>
                    </label>
                  );
                })}
              </fieldset>
              <div className="font-semibold text-lg mb-2">Mapping Set</div>
              <fieldset className="mb-4">
                {setFacet.map((set) => {
                  return (
                    <label key={randomString()} className="block p-1 w-fit">
                      <input
                        type="checkbox"
                        className="invisible hidden peer"
                      />
                      <span className="input-checkbox mr-4" />
                      <span className="capitalize mr-4">{set}</span>
                    </label>
                  );
                })}
              </fieldset>
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
                    disabled
                    value={rowsPerPage}
                    className="input-default appearance-none pr-7 z-20 bg-transparent"
                    onChange={(e) => {
                      const rows = parseInt(e.target.value);
                      setRowsPerPage((prev) => {
                        if (rows !== prev) {
                          setPage(0);
                          setMaxPage(0);
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
            <div className="mb-4">
              <Pagination
                page={page}
                onPageChange={setPage}
                dataCount={paging.next !== "" ? currentCount + 1 : currentCount}
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
                      <i>{searchResult.getSubjectId()}</i>
                      <br />"{searchResult.getSubjectLabel()}"
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
                      <i>{searchResult.getObjectId()}</i>
                      <br />"{searchResult.getObjectLabel()}"
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
                              objectField: searchResult.getObjectMatchFields(),
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
                      <div className="w-fit cursor-pointer">
                        <i title="View" className="icon icon-common icon-eye" />
                      </div>
                    </div>
                  </div>
                );
              })}
              <Pagination
                page={page}
                onPageChange={setPage}
                dataCount={paging.next !== "" ? currentCount + 1 : currentCount}
                rowsPerPage={rowsPerPage}
              />
            </div>
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
              Subject match field: {justif.lexicalMatch.subjectField.join(", ")}
            </li>
            <li>
              Object match field: {justif.lexicalMatch.objectField.join(", ")}
            </li>
            <li>Match string: {justif.lexicalMatch.string.join(", ")}</li>
          </ul>
        </div>
        <div className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4">
          <div className="text-xl text-neutral-black font-bold mb-2">
            Human Curated Match
          </div>
          <ul className="list-disc list-inside pl-2">
            <li>Provider: {justif.curatedMatch.provider}</li>
            <li>Author: {justif.curatedMatch.author.join(", ")}</li>
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
