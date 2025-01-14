import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import { getMapping, getMappings } from "./slice";

export default function MappingView({ appRef }: { appRef: any }) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const params = useParams();
  const mappingId: string = params.mappingId as string;
  const mapping = useAppSelector((state) => state.mapping.mapping);
  const loading = useAppSelector((state) => state.mapping.loadingMapping);
  const otherMappings = useAppSelector((state) => state.mapping.otherMappings);
  const loadingMappings = useAppSelector(
    (state) => state.mapping.loadingMappings
  );

  const [isSubjectCopied, setIsSubjectCopied] = useState(false);
  const [isObjectCopied, setIsObjectCopied] = useState(false);

  useEffect(() => {
    if (mappingId && mappingId !== appRef.current.mapping) {
      dispatch(getMapping(mappingId));
      appRef.current.mapping = mappingId;
    }
  }, [dispatch, appRef, mappingId]);

  useEffect(() => {
    if (mapping) {
      dispatch(
        getMappings({
          subjectId: mapping.getSubjectCurie(),
          predicateId: mapping.getPredicateCurie(),
          objectId: mapping.getObjectCurie(),
          limit: 100,
          page: 1,
        })
      );
    }
  }, [dispatch, mapping]);

  return (
    <main className="container mx-auto">
      <div className="text-2xl font-bold mt-6 mb-4">Mapping</div>
      <div className="mb-6 text-neutral-black flex flex-col items-stretch items-center lg:flex-row">
        <div className="flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none bg-yellow-300">
          <div className="text-center font-bold">
            <span
              className="pr-2 cursor-pointer hover:underline"
              onClick={() => {
                navigate(
                  `/entity/${encodeURIComponent(
                    mapping?.getSubjectCurie() || ""
                  )}`
                );
              }}
            >
              {mapping?.getSubjectCurie() || ""}
            </span>
            <a
              href={mapping?.getSubjectId()}
              title={mapping?.getSubjectId()}
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
                copyText(mapping?.getSubjectCurie() || "", setIsSubjectCopied);
              }}
            />
          </div>
          <div
            title={mapping?.getSubjectLabel()}
            className="text-center truncate"
          >
            {mapping?.getSubjectLabel() || ""}
          </div>
        </div>
        <div className="w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-y-neutral-light lg:border-l-yellow-300" />
        <div className="flex-none flex flex-col justify-center lg:min-w-0 lg:h-[5rem] bg-neutral-light px-6 py-3 rounded-2xl lg:rounded-none">
          <div
            title={mapping?.getPredicateId()}
            className="text-center font-bold"
          >
            {mapping?.getPredicateCurie()}
          </div>
          <div
            title={mapping?.getPredicateLabel()}
            className="text-center truncate"
          >
            {mapping?.getPredicateLabel() || ""}
          </div>
        </div>
        <div className="w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-l-neutral-light lg:border-y-yellow-300" />
        <div className="flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none bg-yellow-300">
          <div className="text-center font-bold">
            <span
              className="pr-2 cursor-pointer hover:underline"
              onClick={() => {
                navigate(
                  `/entity/${encodeURIComponent(
                    mapping?.getObjectCurie() || ""
                  )}`
                );
              }}
            >
              {mapping?.getObjectCurie()}
            </span>
            <a
              href={mapping?.getObjectId()}
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
                copyText(mapping?.getObjectCurie() || "", setIsObjectCopied);
              }}
            />
          </div>
          <div
            title={mapping?.getObjectLabel()}
            className="text-center truncate"
          >
            {mapping?.getObjectLabel() || ""}
          </div>
        </div>
      </div>
      {mapping?.getSubjectCategory() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Subject&nbsp;Category:&nbsp;</strong>
          {mapping.getSubjectCategory()}
        </div>
      ) : null}
      {mapping?.getSubjectType() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Subject&nbsp;Type:&nbsp;</strong>
          {mapping.getSubjectType()}
        </div>
      ) : null}
      {mapping?.getSubjectSource() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Subject&nbsp;Source:&nbsp;</strong>
          {mapping.getSubjectSource()}&nbsp;
          {mapping.getSubjectSourceVersion()}
        </div>
      ) : null}
      {mapping?.getObjectCategory() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Object&nbsp;Category:&nbsp;</strong>
          {mapping.getObjectCategory()}
        </div>
      ) : null}
      {mapping?.getObjectType() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Object&nbsp;Type:&nbsp;</strong>
          {mapping.getObjectType()}
        </div>
      ) : null}
      {mapping?.getObjectSource() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Object&nbsp;Source:&nbsp;</strong>
          {mapping.getObjectSource()}&nbsp;
          {mapping.getObjectSourceVersion()}
        </div>
      ) : null}
      {mapping?.getPredicateModifier() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Predicate&nbsp;Modifier:&nbsp;</strong>
          {mapping.getPredicateModifier()}
        </div>
      ) : null}
      {mapping?.getCardinality() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Cardinality:&nbsp;</strong>
          {mapping?.getCardinality()}
        </div>
      ) : null}
      {mapping?.getProvider() ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Provider:&nbsp;</strong>
          {mapping.getProvider()}
        </div>
      ) : null}
      {mapping && (mapping.getCreatorLabels() || mapping.getCreatorIds()) ? (
        <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
          <strong>Creators</strong>
          <ul>
            {(mapping.getCreatorLabels()
              ? mapping
                  .getCreatorLabels()
                  .map((label) => <li key={label}>{label}</li>)
              : []
            ).concat(
              mapping.getCreatorIds()
                ? mapping.getCreatorIds().map((id) => (
                    <li key={id} className="link-default">
                      <a href={id} target="_blank" rel="noopener noreferrer">
                        {id}
                      </a>
                    </li>
                  ))
                : []
            )}
          </ul>
        </div>
      ) : null}
      <div className="text-2xl font-bold mb-4">Justification</div>
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg mb-4 p-8 text-neutral-black">
        <div className="mb-4">
          <i
            title={mapping?.getJustificationCurie()}
            className="font-bold text-xl pr-2 truncate"
          >
            {mapping?.getJustificationCurie()}
          </i>
          <a
            href={mapping?.getJustificationId()}
            title={mapping?.getJustificationId()}
            target="_blank"
            rel="noopener noreferrer"
          >
            <i className="icon icon-common icon-external-link-alt icon-spacer" />
          </a>
        </div>
        {mapping?.getConfidence() ? (
          <div>
            <strong>Confidence:&nbsp;</strong>
            {mapping.getConfidence()}
          </div>
        ) : null}
        {mapping?.getSubjectMatchFields() ? (
          <div>
            <strong>Subject&nbsp;Match&nbsp;Field:&nbsp;</strong>
            {mapping.getSubjectMatchFields().join(", ")}
          </div>
        ) : null}
        {mapping?.getSubjectPre() ? (
          <div>
            <strong>Subject&nbsp;Preprocessing:&nbsp;</strong>
            {mapping.getSubjectPre().join(", ")}
          </div>
        ) : null}
        {mapping?.getObjectMatchFields() ? (
          <div>
            <strong>Object&nbsp;Match&nbsp;Field:&nbsp;</strong>
            {mapping.getObjectMatchFields().join(", ")}
          </div>
        ) : null}
        {mapping?.getObjectPre() ? (
          <div>
            <strong>Object&nbsp;Preprocessing:&nbsp;</strong>
            {mapping.getObjectPre().join(", ")}
          </div>
        ) : null}
        {mapping?.getMatchStrings() ? (
          <div>
            <strong>Match&nbsp;String:&nbsp;</strong>
            {mapping.getMatchStrings().join(", ")}
          </div>
        ) : null}
        {mapping?.getMappingDate() ? (
          <div>
            <strong>Created:&nbsp;</strong>
            {mapping.getMappingDate()}
          </div>
        ) : null}
        {mapping?.getTool() ? (
          <div>
            <strong>Tool:&nbsp;</strong>
            {mapping.getTool()}&nbsp;
            {mapping.getToolVersion()}
          </div>
        ) : null}
        {mapping?.getSimilarityScore() ? (
          <div>
            <strong>Semantic&nbsp;Similarity&nbsp;Score:&nbsp;</strong>
            {mapping.getSimilarityScore()}
          </div>
        ) : null}
        {mapping?.getSimilarityMeasure() ? (
          <div>
            <strong>Semantic&nbsp;Similarity&nbsp;Measure:&nbsp;</strong>
            {mapping.getSimilarityMeasure()}
          </div>
        ) : null}
        {mapping && (mapping.getAuthorLabels() || mapping.getAuthorIds()) ? (
          <div>
            <strong>Authors</strong>
            <ul>
              {(mapping.getAuthorLabels()
                ? mapping
                    .getAuthorLabels()
                    .map((label) => <li key={label}>{label}</li>)
                : []
              ).concat(
                mapping.getAuthorIds()
                  ? mapping.getAuthorIds().map((id) => (
                      <li key={id} className="link-default">
                        <a href={id} target="_blank" rel="noopener noreferrer">
                          {id}
                        </a>
                      </li>
                    ))
                  : []
              )}
            </ul>
          </div>
        ) : null}
        {mapping &&
        (mapping.getReviewerLabels() || mapping.getReviewerIds()) ? (
          <div>
            <strong>Reviewers</strong>
            <ul>
              {(mapping.getReviewerLabels()
                ? mapping
                    .getReviewerLabels()
                    .map((label) => <li key={label}>{label}</li>)
                : []
              ).concat(
                mapping.getReviewerIds()
                  ? mapping.getReviewerIds().map((id) => (
                      <li key={id} className="link-default">
                        <a href={id} target="_blank" rel="noopener noreferrer">
                          {id}
                        </a>
                      </li>
                    ))
                  : []
              )}
            </ul>
          </div>
        ) : null}
        {mapping?.getLicense() ? (
          <div>
            <strong>License:&nbsp;</strong>
            {mapping.getLicense()}
          </div>
        ) : null}
        {mapping?.getOtherRefs() ? (
          <div>
            <strong>See&nbsp;also:&nbsp;</strong>
            {mapping.getOtherRefs().join(", ")}
          </div>
        ) : null}
        {mapping?.getOther() ? (
          <div>
            <strong>Other:&nbsp;</strong>
            {mapping.getOther()}
          </div>
        ) : null}
        {mapping?.getComment() ? (
          <div>
            <strong>Comment:&nbsp;</strong>
            {mapping.getComment()}
          </div>
        ) : null}
      </div>
      {otherMappings.length > 1 ? (
        <div className="text-2xl font-bold mb-4">Other Justifications</div>
      ) : null}
      {otherMappings.map((otherMapping) => {
        if (
          mapping &&
          mapping.getJustificationId() !== otherMapping.getJustificationId()
        ) {
          return (
            <div
              key={otherMapping.getJustificationId()}
              className="bg-gradient-to-r from-neutral-light to-white rounded-lg mb-4 p-8 text-neutral-black"
            >
              <div className="mb-4">
                <i
                  title={otherMapping.getJustificationCurie()}
                  className="font-bold text-xl pr-2 truncate"
                >
                  {otherMapping.getJustificationCurie()}
                </i>
                <a
                  href={otherMapping.getJustificationId()}
                  title={otherMapping.getJustificationId()}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <i className="icon icon-common icon-external-link-alt icon-spacer" />
                </a>
              </div>
              <ul className="list-disc list-inside pl-2">
                {otherMapping.getConfidence() ? (
                  <li>Confidence:&nbsp;{otherMapping.getConfidence()}</li>
                ) : null}
                {otherMapping.getProvider() ? (
                  <li>Provider:&nbsp;{otherMapping.getProvider()}</li>
                ) : null}
                {otherMapping.getSubjectMatchFields() &&
                otherMapping.getSubjectMatchFields().length > 0 ? (
                  <li>
                    Subject Match Field:&nbsp;
                    {otherMapping.getSubjectMatchFields().join(", ")}
                  </li>
                ) : null}
                {otherMapping.getObjectMatchFields() &&
                otherMapping.getObjectMatchFields().length > 0 ? (
                  <li>
                    Object Match Field:&nbsp;
                    {otherMapping.getObjectMatchFields().join(", ")}
                  </li>
                ) : null}
                {otherMapping.getMatchStrings() &&
                otherMapping.getMatchStrings().length > 0 ? (
                  <li>
                    Match String:&nbsp;
                    {otherMapping.getMatchStrings().join(", ")}
                  </li>
                ) : null}
              </ul>
            </div>
          );
        }
        return null;
      })}
      {loadingMappings ? (
        <div className="text-center my-3">
          <div className="spinner-default animate-spin w-10 h-10" />
        </div>
      ) : null}
      <button
        className="button-secondary text-lg font-bold mb-6"
        onClick={() => navigate(-1)}
      >
        Back
      </button>
      {loading ? <LoadingOverlay message="Loading mapping..." /> : null}
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
