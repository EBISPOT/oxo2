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
  const [justifs, setJustifs] = useState<{
    mapping: {
      subject: string;
      predicate: string;
      object: string;
    };
    default: {
      uri: string;
      confidence: number;
      provider: string;
      subjectField: string[];
      objectField: string[];
      string: string[];
      tool: string;
      toolVersion: string;
    };
  }>({
    mapping: {
      subject: "",
      predicate: "",
      object: "",
    },
    default: {
      uri: "",
      confidence: 0,
      provider: "",
      subjectField: [],
      objectField: [],
      string: [],
      tool: "",
      toolVersion: "",
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
                <div className="flex flex-row text-neutral-default font-bold gap-3 my-4">
                  <div className="basis-4/12">Subject</div>
                  <div className="basis-3/12">Predicate</div>
                  <div className="basis-5/12">Object</div>
                </div>
                {results.map((searchResult) => {
                  return (
                    <div
                      key={randomString()}
                      className="flex flex-row items-start gap-3 mb-4"
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
                      <div className="basis-3/12 border-2 border-neutral-black rounded-lg px-4 py-2 break-all">
                        <i>{searchResult.getPredicateId()}</i>
                        <br />
                        {searchResult.getPredicateModifier()
                          ? "Modifier: " + searchResult.getPredicateModifier()
                          : ""}
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
                            setJustifs({
                              mapping: {
                                subject: searchResult.getSubjectCurie()
                                  ? searchResult.getSubjectCurie()
                                  : searchResult.getSubjectId(),
                                predicate: searchResult.getPredicateLabel()
                                  ? searchResult.getPredicateLabel()
                                  : searchResult.getPredicateId(),
                                object: searchResult.getObjectCurie()
                                  ? searchResult.getObjectCurie()
                                  : searchResult.getObjectId(),
                              },
                              default: {
                                uri: searchResult.getJustification(),
                                confidence: searchResult.getConfidence(),
                                provider: searchResult.getProvider(),
                                subjectField:
                                  searchResult.getSubjectMatchFields(),
                                objectField:
                                  searchResult.getObjectMatchFields(),
                                string: searchResult.getMatchStrings(),
                                tool: searchResult.getTool(),
                                toolVersion: searchResult.getToolVersion(),
                              },
                            });
                            setOpenJustif(true);
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
          openJustif ? "translate-x-0" : "translate-x-96"
        }`}
      >
        <div className="flex flex-row items-center gap-4 mb-4">
          <button type="button" onClick={() => setOpenJustif(false)}>
            <Close />
          </button>
          <div className="text-neutral-default font-bold">Justifications</div>
        </div>
        <div
          title={justifs.mapping.subject}
          className="bg-yellow-300 px-3 py-2 m-1 rounded-lg"
        >
          {justifs.mapping.subject}
        </div>
        <div
          title={justifs.mapping.predicate}
          className="bg-orange-600 text-white px-3 py-2 m-1 rounded-lg truncate"
        >
          {justifs.mapping.predicate}
        </div>
        <div
          title={justifs.mapping.object}
          className="bg-yellow-300 px-3 py-2 m-1 rounded-lg"
        >
          {justifs.mapping.object}
        </div>
        <div className="shadow-card border-b-8 border-link-default rounded-md bg-white text-neutral-black p-4 my-4">
          <div
            title={justifs.default.uri}
            className="text-xl font-bold mb-2 truncate"
          >
            {justifs.default.uri.substring(
              justifs.default.uri.lastIndexOf("/") + 1
            )}
          </div>
          <ul className="list-disc list-inside pl-2">
            {justifs.default.confidence ? (
              <li>Confidence:&nbsp;{justifs.default.confidence}</li>
            ) : null}
            {justifs.default.provider ? (
              <li>Provider:&nbsp;{justifs.default.provider}</li>
            ) : null}
            {justifs.default.subjectField &&
            justifs.default.subjectField.length > 0 ? (
              <li>
                Subject&nbsp;match&nbsp;field:&nbsp;
                {justifs.default.subjectField?.join(", ")}
              </li>
            ) : null}
            {justifs.default.objectField &&
            justifs.default.objectField.length > 0 ? (
              <li>
                Object&nbsp;match&nbsp;field:&nbsp;
                {justifs.default.objectField?.join(", ")}
              </li>
            ) : null}
            {justifs.default.string && justifs.default.string.length > 0 ? (
              <li>
                Match&nbsp;string:&nbsp;
                {justifs.default.string?.join(", ")}
              </li>
            ) : null}
            {justifs.default.tool ? (
              <div>
                Tool:&nbsp;{justifs.default.tool}&nbsp;
                {justifs.default.toolVersion}
              </div>
            ) : null}
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
