import Header from "../../common/Header";

export default function About() {
  return (
    <div>
      <Header />
      <main className="container mx-auto">
        <div className="text-2xl font-bold my-6">About OxO</div>
        <div className="text-lg">
          <p className="mb-4">
            OxO is an database of ontology cross-references (xrefs) extracted
            from public ontologies and databases. Most of these cross-references
            have been extracted from ontologies in the{" "}
            <a href="https://www.ebi.ac.uk/ols" className="link-default">
              Ontology Lookup Service
            </a>
            &thinsp;by searching for database cross-reference annotations on
            terms. We have supplemented these cross-references with mappings
            from a subset of vocabularies in the{" "}
            <a
              href="https://www.nlm.nih.gov/research/umls/"
              className="link-default"
            >
              UMLS
            </a>
            .
          </p>
          <p className="mb-4">
            The semantics of a cross-reference are weakly specified, in most
            cases they mean some kind of operational equivalence, but there is
            no guarantee. Sometimes cross-references are used to indicate other
            types of relationships such as parent/child or that the terms are
            related in some other way (such as linking a disease concept to a
            pathway accession that is somehow related to that disease). OxO aims
            to provide simple and convenient access to cross-references, but is
            not a mapping prediction service, so always treat these xrefs with
            caution, especially if you are seeking true equivalence between two
            ontologies.
          </p>
          <p className="mb-4">
            OxO gives you access to existing mappings, you can also explore the
            neighbourhood of a mapping using the distance controller. By default
            OxO shows you direct asserted mappings, but you can use the slider
            on various pages to look for mappings that are up to three hops
            away. You may see some terms that don't have labels associated to
            them, we are doing our best to find labels for all of these terms,
            but sometimes the labels are missing from the sources that we
            extract mappings from.
          </p>
          <p className="mb-4">
            OxO is developed by the Samples, Phenotypes and Ontologies team. If
            you have any questions about OxO please{" "}
            <a
              href="https://www.ebi.ac.uk/spot/oxo/contact"
              className="link-default"
            >
              contact us
            </a>
            .
          </p>
        </div>
      </main>
    </div>
  );
}
