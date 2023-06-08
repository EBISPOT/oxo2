import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import { getEntities } from "./slice";

export default function EntityView() {
  const params = useParams();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const entityId: string = params.entityId as string;

  const entityMappings = useAppSelector((state) => state.entity.entityMappings);
  const loading = useAppSelector((state) => state.entity.loadingEntity);

  const [isSubjectCopied, setIsSubjectCopied] = useState(false);
  const [isPredicateCopied, setIsPredicateCopied] = useState(false);
  const [isObjectCopied, setIsObjectCopied] = useState(false);

  useEffect(() => {
    dispatch(getEntities(entityId));
  }, [dispatch, entityId]);
  return (
    <main className="container mx-auto">
      <div className="text-2xl font-bold mt-6 mb-4">{entityId}</div>
      {entityMappings && entityMappings.length > 0
        ? entityMappings.map((mapping) => {
            return (
              <div className="mb-4 text-neutral-black grid grid-cols-1 lg:grid-cols-3">
                <div>
                  {mapping?.getSubjectId() ? (
                    <div>
                      <div className="bg-yellow-300 px-6 py-3 my-1 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none">
                        <div className="font-bold text-center">
                          {mapping.getSubjectCurie()}
                        </div>
                        <div
                          title={mapping.getSubjectId()}
                          className="italic truncate"
                        >
                          <a
                            href={mapping.getSubjectId()}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <i className="icon icon-common icon-external-link-alt icon-spacer" />
                          </a>
                          <i
                            title="Copy"
                            className={`icon icon-common icon-copy icon-spacer ${
                              isSubjectCopied ? "cursor-wait" : "cursor-pointer"
                            }`}
                            onClick={() => {
                              copyText(
                                mapping.getSubjectId(),
                                setIsSubjectCopied
                              );
                            }}
                          />
                          {mapping.getSubjectId()}
                        </div>
                      </div>
                      {mapping.getSubjectLabel() ? (
                        <div className="text-center">
                          ({mapping.getSubjectLabel()})
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
                <div>
                  {mapping?.getPredicateId() ? (
                    <div>
                      <div className="bg-neutral-light px-6 py-3 my-1 rounded-2xl lg:rounded-none">
                        <div className="font-bold text-center">
                          {mapping.getPredicateCurie()}
                        </div>
                        <div
                          title={mapping.getPredicateId()}
                          className="italic truncate"
                        >
                          <i
                            title="Copy"
                            className={`icon icon-common icon-copy icon-spacer ${
                              isPredicateCopied
                                ? "cursor-wait"
                                : "cursor-pointer"
                            }`}
                            onClick={() => {
                              copyText(
                                mapping.getPredicateId(),
                                setIsPredicateCopied
                              );
                            }}
                          />
                          {mapping.getPredicateId()}
                        </div>
                      </div>
                      {mapping.getPredicateLabel() ? (
                        <div className="text-center">
                          ({mapping.getPredicateLabel()})
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
                <div>
                  {mapping?.getObjectId() ? (
                    <div>
                      <div className="bg-yellow-300 px-6 py-3 my-1 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none">
                        <div className="font-bold text-center">
                          {mapping.getObjectCurie()}
                        </div>
                        <div
                          title={mapping.getObjectId()}
                          className="italic truncate"
                        >
                          <a
                            href={mapping.getObjectId()}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <i className="icon icon-common icon-external-link-alt icon-spacer" />
                          </a>
                          <i
                            title="Copy"
                            className={`icon icon-common icon-copy icon-spacer ${
                              isObjectCopied ? "cursor-wait" : "cursor-pointer"
                            }`}
                            onClick={() => {
                              copyText(
                                mapping.getObjectId(),
                                setIsObjectCopied
                              );
                            }}
                          />
                          {mapping.getObjectId()}
                        </div>
                      </div>
                      {mapping.getObjectLabel() ? (
                        <div className="text-center">
                          ({mapping.getObjectLabel()})
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })
        : null}
      {loading ? <LoadingOverlay message="Loading entity..." /> : null}
      <button
        className="button-secondary text-lg font-bold mb-6"
        onClick={() => navigate(-1)}
      >
        Back
      </button>
    </main>
  );
}

function copyText(text: string, setToggle: (toggle: boolean) => void) {
  copyToClipboard(text)
    .then(() => {
      setToggle(true);
      // revert after a few seconds
      setTimeout(() => {
        setToggle(false);
      }, 500);
    })
    .catch((err) => {
      console.log(err);
    });
}
