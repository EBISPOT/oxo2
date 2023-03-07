import { useNavigate, useParams } from "react-router-dom";

export default function Mapping() {
  const navigate = useNavigate();
  const params = useParams();
  const mappingId: string = params.mappingId as string;

  return (
    <main className="container mx-auto">
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8 text-neutral-black">
        <div className="text-2xl font-bold mb-4">
          Mapping Information&nbsp;
          <span className="text-sm text-neutral-default italic">
            ({mappingId})
          </span>
        </div>
        <p>
          <div>
            <strong>From term </strong> EFO:0000400 <strong>to</strong>{" "}
            MONDO:0005015
          </div>
          <div>
            <strong>Scope: </strong> RELATED
          </div>
          <div>
            <strong>Created date: </strong> Fri Nov 18 00:00:00 GMT 2022
          </div>
          <div>
            <strong>Mapping source: </strong> Experimental Factor Ontology EFO
          </div>
          <div>
            <strong>Source type: </strong> ONTOLOGY
          </div>
        </p>
      </div>
      <div>
        <button
          className="button-secondary text-lg font-bold"
          onClick={() => navigate(-1)}
        >
          Back
        </button>
      </div>
    </main>
  );
}
