import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard } from "../../app/util";
import LoadingOverlay from "../../common/LoadingOverlay";
import { getMapping } from "./slice";

export default function Mapping() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const params = useParams();
  const mappingId: string = params.mappingId as string;
  const mapping = useAppSelector((state) => state.mapping.mapping);
  const loading = useAppSelector((state) => state.mapping.loadingMapping);

  const [isSubjectCopied, setIsSubjectCopied] = useState(false);
  const [isPredicateCopied, setIsPredicateCopied] = useState(false);
  const [isObjectCopied, setIsObjectCopied] = useState(false);

  useEffect(() => {
    dispatch(getMapping(mappingId));
  }, [dispatch, mappingId]);

  return (
    <main className="container mx-auto">
      <div className="text-2xl font-bold mt-6 mb-4">Mapping</div>
      <div className="mb-4 text-neutral-black grid grid-cols-1 lg:grid-cols-3">
        <div>
          {mapping?.getSubjectId() ? (
            <div>
              <div className="bg-yellow-default px-4 py-2 my-1 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none">
                <div title={mapping.getSubjectId()} className="italic truncate">
                  <i
                    title="Copy"
                    className={`icon icon-common icon-copy icon-spacer ${
                      isSubjectCopied ? "cursor-wait" : "cursor-pointer"
                    }`}
                    onClick={() => {
                      copyText(mapping.getSubjectId(), setIsSubjectCopied);
                    }}
                  />
                  {mapping.getSubjectId()}
                </div>
                <div className="font-bold text-center">
                  {mapping.getSubjectCurie()}
                </div>
              </div>
              {mapping.getSubjectLabel() ? (
                <div className="text-center">({mapping.getSubjectLabel()})</div>
              ) : null}
            </div>
          ) : null}
        </div>
        <div>
          {mapping?.getPredicateId() ? (
            <div>
              <div className="bg-link-dark px-4 py-2 my-1 text-white rounded-2xl lg:rounded-none">
                <div
                  title={mapping.getPredicateId()}
                  className="italic truncate"
                >
                  <i
                    title="Copy"
                    className={`icon icon-common icon-copy icon-spacer ${
                      isPredicateCopied ? "cursor-wait" : "cursor-pointer"
                    }`}
                    onClick={() => {
                      copyText(mapping.getPredicateId(), setIsPredicateCopied);
                    }}
                  />
                  {mapping.getPredicateId()}
                </div>
                <div>&nbsp;</div>
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
              <div className="bg-yellow-default px-4 py-2 my-1 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none">
                <div title={mapping.getObjectId()} className="italic truncate">
                  <i
                    title="Copy"
                    className={`icon icon-common icon-copy icon-spacer ${
                      isObjectCopied ? "cursor-wait" : "cursor-pointer"
                    }`}
                    onClick={() => {
                      copyText(mapping.getObjectId(), setIsObjectCopied);
                    }}
                  />
                  {mapping.getObjectId()}
                </div>
                <div className="font-bold text-center">
                  {mapping.getObjectCurie()}
                </div>
              </div>
              {mapping.getObjectLabel() ? (
                <div className="text-center">({mapping.getObjectLabel()})</div>
              ) : null}
            </div>
          ) : null}
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
            {mapping?.getProvider()}
          </div>
        ) : null}
        {mapping && (mapping.getCreatorLabels() || mapping.getCreatorIds()) ? (
          <div className="px-2 pt-1 text-neutral-black lg:col-span-3">
            <strong>Creators:&nbsp;</strong>
            {[
              ...(mapping.getCreatorLabels() ? mapping.getCreatorLabels() : []),
              ...(mapping.getCreatorIds() ? mapping.getCreatorIds() : []),
            ].join(", ")}
          </div>
        ) : null}
      </div>
      <div className="text-2xl font-bold mb-4">Justification</div>
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg mb-4 p-8 text-neutral-black grid grid-cols-1 gap-1">
        <div className="font-bold mb-4">{mapping?.getJustification()}</div>
        {mapping?.getConfidence() ? (
          <div>Confidence:&nbsp;{mapping.getConfidence()}</div>
        ) : null}
        {mapping?.getSubjectMatchFields() ? (
          <div>
            Subject&nbsp;Match&nbsp;Field:&nbsp;
            {mapping.getSubjectMatchFields().join(", ")}
          </div>
        ) : null}
        {mapping?.getSubjectPre() ? (
          <div>
            Subject&nbsp;Preprocessing:&nbsp;
            {mapping.getSubjectPre().join(", ")}
          </div>
        ) : null}
        {mapping?.getObjectMatchFields() ? (
          <div>
            Object&nbsp;Match&nbsp;Field:&nbsp;
            {mapping.getObjectMatchFields().join(", ")}
          </div>
        ) : null}
        {mapping?.getObjectPre() ? (
          <div>
            Object&nbsp;Preprocessing:&nbsp;{mapping.getObjectPre().join(", ")}
          </div>
        ) : null}
        {mapping?.getMatchStrings() ? (
          <div>
            Match&nbsp;String:&nbsp;{mapping.getMatchStrings().join(", ")}
          </div>
        ) : null}
        {mapping?.getMappingDate() ? (
          <div>Created:&nbsp;{mapping.getMappingDate()}</div>
        ) : null}
        {mapping?.getTool() ? (
          <div>
            Tool:&nbsp;{mapping.getTool()}&nbsp;
            {mapping.getToolVersion()}
          </div>
        ) : null}
        {mapping?.getSimilarityScore() ? (
          <div>
            Semantic&nbsp;Similarity&nbsp;Score:&nbsp;
            {mapping.getSimilarityScore()}
          </div>
        ) : null}
        {mapping?.getSimilarityMeasure() ? (
          <div>
            Semantic&nbsp;Similarity&nbsp;Measure:&nbsp;
            {mapping.getSimilarityMeasure()}
          </div>
        ) : null}
        {mapping && (mapping.getAuthorLabels() || mapping.getAuthorIds()) ? (
          <div>
            Authors:&nbsp;
            {[
              ...(mapping.getAuthorLabels() ? mapping.getAuthorLabels() : []),
              ...(mapping.getAuthorIds() ? mapping.getAuthorIds() : []),
            ].join(", ")}
          </div>
        ) : null}
        {mapping &&
        (mapping.getReviewerLabels() || mapping.getReviewerIds()) ? (
          <div>
            Reviewers:&nbsp;
            {[
              ...(mapping?.getReviewerLabels()
                ? mapping.getReviewerLabels()
                : []),
              ...(mapping?.getReviewerIds() ? mapping.getReviewerIds() : []),
            ].join(", ")}
          </div>
        ) : null}
        {mapping?.getLicense() ? (
          <div>License:&nbsp;{mapping.getLicense()}</div>
        ) : null}
        {mapping?.getOtherRefs() ? (
          <div>See&nbsp;also:&nbsp;{mapping.getOtherRefs().join(", ")}</div>
        ) : null}
        {mapping?.getOther() ? (
          <div>Other:&nbsp;{mapping.getOther()}</div>
        ) : null}
        {mapping?.getComment() ? (
          <div>Comment:&nbsp;{mapping.getComment()}</div>
        ) : null}
      </div>
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
