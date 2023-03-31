import { useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import LoadingOverlay from "../../common/LoadingOverlay";
import { getMapping } from "./slice";

export default function Mapping() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const params = useParams();
  const mappingId: string = params.mappingId as string;
  const mapping = useAppSelector((state) => state.mapping.mapping);
  const loading = useAppSelector((state) => state.mapping.loadingMapping);

  useEffect(() => {
    dispatch(getMapping(mappingId));
  }, [dispatch, mappingId]);

  return (
    <main className="container mx-auto">
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8 text-neutral-black">
        <div className="text-2xl font-bold mb-4">Mapping</div>
        {mapping ? (
          <div className="flex flex-col gap-2">
            <div>
              <strong>Subject</strong>
              <br />
              {mapping.getSubjectLabel()} (<i>{mapping?.getSubjectId()}</i>)
            </div>
            <div>
              <strong>Predicate</strong>
              <br />
              {mapping.getPredicateId()}
            </div>
            <div>
              <strong>Object</strong>
              <br />
              {mapping.getObjectLabel()} (<i>{mapping.getObjectId()}</i>)
            </div>
            <div>
              <strong>Justification</strong>
              <br />
              {mapping.getJustification()}
            </div>
          </div>
        ) : null}
      </div>
      <div>
        <button
          className="button-secondary text-lg font-bold mb-6"
          onClick={() => navigate(-1)}
        >
          Back
        </button>
      </div>
      {loading ? <LoadingOverlay message="Loading mapping..." /> : null}
    </main>
  );
}
