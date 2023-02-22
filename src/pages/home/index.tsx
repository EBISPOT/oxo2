import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Header from "../../common/Header";

export default function Home() {
  const navigate = useNavigate();
  const [query, setQuery] = useState<string>("");

  return (
    <div>
      <Header />
      <main className="container mx-auto">
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-3">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
              <div className="text-3xl mb-4 text-neutral-black font-bold">
                Welcome to the EMBL-EBI Mapping Lookup Service
              </div>
              <div className="flex flex-row gap-4">
                <div className="w-full">
                  <div className="flex flex-row justify-between text-neutral-black mb-2">
                    <div>Enter identifiers separated by comma or newline:</div>
                    <div
                      className="link-default"
                      onClick={() => {
                        setQuery(
                          "EFO:0001360" +
                            "\nDOID:162" +
                            "\nOMIM:180200" +
                            "\nMESH:D009202" +
                            "\nUBERON_0002107" +
                            "\nHP_0005978"
                        );
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
                    value={query}
                    onChange={(e) => {
                      setQuery(e.target.value);
                    }}
                  />
                </div>
                <button
                  className="button-primary text-lg font-bold self-center"
                  onClick={() => {
                    if (query) {
                      navigate("/search", {
                        state: {
                          search: query.split(/[\n,]+/).map((id) => id.trim()),
                        },
                      });
                    }
                  }}
                >
                  Search
                </button>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-8 mb-8">
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-browse icon-spacer text-orange-default" />
                  <Link to={"/about"} className="link-default">
                    About OxO
                  </Link>
                </div>
                <p>
                  OxO is a service for finding mappings (or cross-references)
                  between terms from ontologies, vocabularies and coding
                  standards. OxO imports mappings from a variety of sources
                  including the&thinsp;
                  <a
                    href="https://www.ebi.ac.uk/ols4/"
                    className="link-default"
                  >
                    Ontology Lookup Service (OLS)
                  </a>
                  &thinsp;and a subset of mappings provided by the&thinsp;
                  <a
                    href="https://www.nlm.nih.gov/research/umls/"
                    className="link-default"
                  >
                    UMLS
                  </a>
                  . OxO is developed and maintained by the Samples, Phenotypes
                  and Ontologies Team (SPOT) at&thinsp;
                  <a href="https://www.ebi.ac.uk" className="link-default">
                    EMBL-EBI
                  </a>
                  .
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-tool icon-spacer text-orange-default" />
                  <a
                    href="https://www.ebi.ac.uk/spot/ontology/"
                    className="link-default"
                  >
                    Related Tools
                  </a>
                </div>
                <p>
                  In addition to OxO, SPOT also provides&thinsp;
                  <a
                    className="link-default"
                    href="https://www.ebi.ac.uk/ols4/"
                  >
                    OLS
                  </a>
                  &thinsp;and&thinsp;
                  <a
                    className="link-default"
                    href="https://www.ebi.ac.uk/spot/zooma"
                  >
                    ZOOMA
                  </a>
                  &thinsp;services. OLS provides access to the latest ontology
                  versions. ZOOMA is a service to assist in mapping data to
                  ontologies in OLS.
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-exclamation-triangle icon-spacer text-orange-default" />
                  <a
                    href="https://github.com/EBISPOT/oxo2/issues"
                    className="link-default"
                  >
                    Report an Issue
                  </a>
                </div>
                <p>
                  For feedback, suggestion or requests about OxO please use
                  our&thinsp;
                  <a
                    href="https://github.com/EBISPOT/oxo2/issues"
                    className="link-default"
                  >
                    GitHub issue tracker
                  </a>
                  . For announcements relating to OLS, such as new releases and
                  new features sign up to the&thinsp;
                  <a
                    href="https://listserver.ebi.ac.uk/mailman/listinfo/ols-announce"
                    className="link-default"
                  >
                    OLS announce mailing list
                  </a>
                  .
                </p>
              </div>
            </div>
          </div>
          <div className="col-span-1">
            <div className="shadow-card border-b-8 border-link-default rounded-md my-8 p-4">
              <div className="text-2xl text-neutral-black font-bold mb-3">
                <i className="icon icon-common icon-analyse-graph icon-spacer" />
                <span>Data Content</span>
              </div>

              <div className="text-neutral-black">
                <div className="mb-2 text-sm italic">Updated</div>
                <ul className="list-disc list-inside pl-2">
                  <li>mapping sets</li>
                  <li>mappings</li>
                  <li>mapping providers</li>
                  <li>mapped entities</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
