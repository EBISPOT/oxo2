import React, { useState, useEffect } from "react";
import { useDispatch } from "react-redux";
import { setSearchInput, SearchStatus } from "../search/slice";
import { Link, createSearchParams, useNavigate } from "react-router";

export default function Home({ appRef }: { appRef: any }) {
  const dispatch = useDispatch();
  const [searchInput, setSearchInputState] = useState<string>("");
  const navigate = useNavigate();

  useEffect(() => {
    dispatch(setSearchInput(searchInput));
  }, [searchInput, dispatch]);

  const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    setSearchInputState(event.target.value);
  };

  const handleSearch = () => {
    if (!searchInput)
      return;
    appRef.current.searchQuery = searchInput
        .split(/[\n,]+/)
        .map(id => id.trim())
        .filter(Boolean)
        .join(",");
    navigate({
      pathname: "/search"
    });
  };

  return (
      <main className="container mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-4 lg:gap-8">
          <div className="lg:col-span-3">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
              <div className="text-3xl mb-4 text-neutral-black font-bold">
                Welcome to the EMBL-EBI OxO Mapping Service
              </div>
              <div className="flex flex-col md:flex-row gap-4">
                <div className="w-full">
                  <div className="flex flex-col md:flex-row justify-between text-neutral-black mb-2">
                    <div>
                      Enter identifiers (CURIE format) separated by comma or
                      newline:
                    </div>
                    <div
                        className="link-default md:mx-0.5"
                        onClick={() => {
                          setSearchInputState("UBERON:0002107\nHP:0000518\nMP:0001289\nMP:0000564");
                        }}
                    >
                      Examples...
                    </div>
                  </div>
                  <textarea
                      id="home-search"
                      rows={2}
                      style={{ resize: "vertical", minHeight: "5rem" }}
                      placeholder={"Search OxO..."}
                      className="input-default text-lg"
                      value={searchInput}
                      onChange={handleInputChange}
                  />
                </div>
                <button
                    className="button-primary text-lg font-bold self-end md:self-center"
                    onClick={handleSearch}
                >
                  Search
                </button>
              </div>
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-browse icon-spacer text-yellow-default" />
                  <Link to={"/about"} className="link-default">
                    About OxO
                  </Link>
                </div>
                <p>
                  OxO is a service for finding mappings
                  between entities based on the&thinsp;
                  <a
                      href={process.env.REACT_APP_SSSOM_HOME}
                      className="link-default"
                  >
                    Simple Standard for Sharing Ontological Mappings (SSSOM)
                  </a>
                  .
                  OxO is developed and maintained by the Samples, Phenotypes and
                  Ontologies Team (SPOT) at&thinsp;
                  <a
                      href={process.env.REACT_APP_EBI_HOME}
                      className="link-default"
                  >
                    EMBL-EBI
                  </a>
                  .
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-tool icon-spacer text-yellow-default" />
                  <a
                      href={process.env.REACT_APP_SPOT_ONTOTOOLS}
                      className="link-default"
                  >
                    Related Tools
                  </a>
                </div>
                <p>
                  In addition to OxO, SPOT also provides&thinsp;
                  <a
                      href={process.env.REACT_APP_SPOT_OLS}
                      className="link-default"
                  >
                    OLS
                  </a>
                  &thinsp;and&thinsp;
                  <a
                      className="link-default"
                      href={process.env.REACT_APP_SPOT_ZOOMA}
                  >
                    ZOOMA
                  </a>
                  &thinsp;services. OLS provides access to the latest ontology
                  versions. ZOOMA is a service to assist in mapping strings of text to
                  ontology terms in OLS.
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-exclamation-triangle icon-spacer text-yellow-default" />
                  <a
                      href={`${process.env.REACT_APP_SPOT_OXO2_REPO}/issues`}
                      className="link-default"
                  >
                    Report an Issue
                  </a>
                </div>
                <p>
                  For feedback, suggestion or requests about OxO please use
                  our&thinsp;
                  <a
                      href={`${process.env.REACT_APP_SPOT_OXO2_REPO}/issues`}
                      className="link-default"
                  >
                    GitHub issue tracker
                  </a>
                  . For announcements relating to OxO, such as new releases and
                  new features sign up to the&thinsp;
                  <a
                      href={process.env.REACT_APP_SPOT_OLS_ANNOUNCE}
                      className="link-default"
                  >
                    OLS announce mailing list
                  </a>
                  .
                </p>
              </div>
            </div>
          </div>
        </div>
      </main>
  );
}