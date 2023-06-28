import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import Mapping from "../../model/Mapping";
import { getEntities } from "./slice";

export default function EntityView({ appRef }: { appRef: any }) {
  const params = useParams();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const entityId: string = params.entityId as string;

  const entityMappings = useAppSelector((state) => state.entity.entityMappings);
  const loading = useAppSelector((state) => state.entity.loadingEntity);

  const [mappings, setMappings] = useState<Mapping[]>([]);
  const [isSubjectCopied, setIsSubjectCopied] = useState(false);
  const [isObjectCopied, setIsObjectCopied] = useState(false);

  useEffect(() => {
    if (entityId && entityId !== appRef.current.entity) {
      dispatch(getEntities(entityId));
      appRef.current.entity = entityId;
    }
  }, [dispatch, appRef, entityId]);
  useEffect(() => {
    let entityMappingsCopy = [...entityMappings];
    entityMappingsCopy.sort((a, b) => {
      if (a.getSubjectCurie() && a.getSubjectCurie() === entityId) return 1;
      else if (b.getSubjectCurie() && b.getSubjectCurie() === entityId)
        return 1;
      else return -1;
    });
    setMappings(entityMappingsCopy);
  }, [entityMappings, setMappings, entityId]);
  return (
    <main className="container mx-auto">
      <div className="text-2xl font-bold mt-6 mb-4">{entityId}</div>
      {mappings && mappings.length > 0
        ? mappings.map((mapping) => {
            return (
              <div
                key={mapping.getMappingId()}
                className="mb-6 text-neutral-black flex flex-col items-stretch items-center lg:flex-row"
              >
                <div
                  className={`flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none ${
                    mapping.getSubjectCurie() === entityId
                      ? "bg-yellow-300"
                      : "bg-grey-300"
                  }`}
                >
                  <div className="text-center font-bold">
                    <span className="pr-2">{mapping.getSubjectCurie()}</span>
                    <i
                      title="Copy"
                      className={`icon icon-common icon-copy icon-spacer ${
                        isSubjectCopied ? "cursor-wait" : "cursor-pointer"
                      }`}
                      onClick={() => {
                        copyText(mapping.getSubjectCurie(), setIsSubjectCopied);
                      }}
                    />
                    <a
                      href={mapping.getSubjectId()}
                      title={mapping.getSubjectId()}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <i className="icon icon-common icon-external-link-alt icon-spacer" />
                    </a>
                  </div>
                  <div
                    title={mapping.getSubjectLabel()}
                    className="text-center truncate"
                  >
                    {mapping.getSubjectLabel() || ""}
                  </div>
                </div>
                <div
                  className={`w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-y-neutral-light ${
                    mapping.getSubjectCurie() === entityId
                      ? "lg:border-l-yellow-300"
                      : "lg:border-l-grey-300"
                  }`}
                ></div>
                <div className="flex-none flex flex-col justify-center lg:min-w-0 lg:h-[5rem] bg-neutral-light px-6 py-3 rounded-2xl lg:rounded-none">
                  <div
                    title={mapping.getPredicateId()}
                    className="text-center font-bold"
                  >
                    {mapping.getPredicateCurie()}
                  </div>
                  <div
                    title={mapping.getPredicateLabel()}
                    className="text-center truncate"
                  >
                    {mapping.getPredicateLabel() || ""}
                  </div>
                </div>
                <div
                  className={`w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-l-neutral-light ${
                    mapping.getObjectCurie() === entityId
                      ? "lg:border-y-yellow-300"
                      : "lg:border-y-grey-300"
                  }`}
                ></div>
                <div
                  className={`flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none ${
                    mapping.getObjectCurie() === entityId
                      ? "bg-yellow-300"
                      : "bg-grey-300"
                  }`}
                >
                  <div className="text-center font-bold">
                    <span className="pr-2">{mapping.getObjectCurie()}</span>
                    <i
                      title="Copy"
                      className={`icon icon-common icon-copy icon-spacer ${
                        isObjectCopied ? "cursor-wait" : "cursor-pointer"
                      }`}
                      onClick={() => {
                        copyText(mapping.getObjectCurie(), setIsObjectCopied);
                      }}
                    />
                    <a
                      href={mapping.getObjectId()}
                      title={mapping.getObjectId()}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <i className="icon icon-common icon-external-link-alt icon-spacer" />
                    </a>
                  </div>
                  <div
                    title={mapping.getObjectLabel()}
                    className="text-center truncate"
                  >
                    {mapping.getObjectLabel() || ""}
                  </div>
                </div>
                <div
                  className="link-default text-sm font-bold text-center cursor-pointer self-center my-2 mx-4"
                  onClick={() => {
                    navigate(
                      `/mapping/${encodeURIComponent(mapping.getMappingId())}`
                    );
                  }}
                >
                  View
                  <br />
                  mapping
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
