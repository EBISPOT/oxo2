export default function About() {
  return (
    <main className="container mx-auto">
      <div className="text-2xl font-bold my-6">About OxO</div>
      <div className="text-lg">
        <p className="mb-4">
          OxO is a service for finding mappings
          between entities based on the&thinsp;
          <a
              href={process.env.REACT_APP_SSSOM_HOME}
              className="link-default"
          >
            Simple Standard for Sharing Ontological Mappings (SSSOM)
          </a>
          .
        </p>
        <p className="mb-4">
          OxO is developed by the Samples, Phenotypes and Ontologies team. If
          you have any questions about OxO please&thinsp;
          <a
            href={process.env.REACT_APP_SPOT_OXO_CONTACT}
            className="link-default"
          >
            contact us
          </a>
          .
        </p>
      </div>
    </main>
  );
}
