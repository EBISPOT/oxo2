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
      <div className="text-2xl font-bold mt-6 mb-4">Mapping</div>
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg mb-4 p-8 text-neutral-black">
        {mapping ? (
          <div className="grid grid-cols-2 gap-3">
            {mapping.getSubjectId() ? (
              <div>
                <div className="text-lg font-bold">Subject</div>
                <div className="bg-yellow-default px-3 py-2 m-1 rounded-lg w-fit">{mapping.getSubjectCurie()}</div>
                <div className="mx-2">{mapping.getSubjectLabel()}</div>
                <div className="italic mx-2">{mapping.getSubjectId()}</div>
                <div>{mapping.getSubjectCategory()}</div>
                <div>{mapping.getSubjectType()}</div>
                <div>{mapping.getSubjectSource()}</div>
                <div>{mapping.getSubjectSourceVersion()}</div>
              </div>
            ) : null}
            {mapping.getPredicateId() ? (
              <div>
                <div className="text-lg font-bold">Predicate</div>
                <div>{mapping.getPredicateLabel()}</div>
                <div className="italic">{mapping.getPredicateId()}</div>
                <div>{mapping.getPredicateModifier()}</div>
              </div>
            ) : null}
            {mapping.getObjectId() ? (
              <div>
                <div className="text-lg font-bold">Object</div>
                <div>{mapping.getObjectLabel()}</div>
                <div className="italic">{mapping.getObjectId()}</div>
                <div>{mapping.getObjectCategory()}</div>
                <div>{mapping.getObjectType()}</div>
                <div>{mapping.getObjectSource()}</div>
                <div>{mapping.getObjectSourceVersion()}</div>
              </div>
            ) : null}
            {mapping.getJustification() ? (
              <div>
                <div className="text-lg font-bold">Justification</div>
                <div className="italic">{mapping.getJustification()}</div>
              </div>
            ) : null}
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
