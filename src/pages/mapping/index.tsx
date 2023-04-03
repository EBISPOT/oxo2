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
      <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg mb-4 p-8 text-neutral-black grid grid-cols-2 gap-4">
        <div>
          <div className="text-lg font-bold">Subject</div>
          {mapping?.getSubjectId() ? (
            <div>
              <div className="bg-yellow-default px-3 py-2 my-1 rounded-lg w-fit">
                {mapping.getSubjectCurie()}
              </div>
              <div>"{mapping.getSubjectLabel()}"</div>
              <div className="italic">{mapping.getSubjectId()}</div>
              <div>{mapping.getSubjectCategory()}</div>
              <div>{mapping.getSubjectType()}</div>
              <div>
                Source:&nbsp;{mapping.getSubjectSource()}&nbsp;
                {mapping.getSubjectSourceVersion()}
              </div>
            </div>
          ) : null}
        </div>
        <div>
          <div className="text-lg font-bold">Object</div>
          {mapping?.getObjectId() ? (
            <div>
              <div className="bg-yellow-default px-3 py-2 my-1 rounded-lg w-fit">
                {mapping.getObjectCurie()}
              </div>
              <div>"{mapping.getObjectLabel()}"</div>
              <div className="italic">{mapping.getObjectId()}</div>
              <div>{mapping.getObjectCategory()}</div>
              <div>{mapping.getObjectType()}</div>
              <div>
                Source:&nbsp;{mapping.getObjectSource()}&nbsp;
                {mapping.getObjectSourceVersion()}
              </div>
            </div>
          ) : null}
        </div>
        <div>
          <div className="text-lg font-bold">Predicate</div>
          {mapping?.getPredicateId() ? (
            <div className="mb-4">
              <div>{mapping.getPredicateLabel()}</div>
              <div className="italic">{mapping.getPredicateId()}</div>
              <div>{mapping.getPredicateModifier()}</div>
            </div>
          ) : null}
          <div>Cardinality:&nbsp;{mapping?.getCardinality()}</div>
          <div>Provider:&nbsp;{mapping?.getProvider()}</div>
          <div>
            Creators:&nbsp;
            {[
              ...(mapping?.getCreatorLabels()
                ? mapping.getCreatorLabels()
                : []),
              ...(mapping?.getCreatorIds() ? mapping.getCreatorIds() : []),
            ].join(", ")}
          </div>
        </div>
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
        <div>
          Authors:&nbsp;
          {[
            ...(mapping?.getAuthorLabels() ? mapping.getAuthorLabels() : []),
            ...(mapping?.getAuthorIds() ? mapping.getAuthorIds() : []),
          ].join(", ")}
        </div>
        <div>
          Reviewers:&nbsp;
          {[
            ...(mapping?.getReviewerLabels()
              ? mapping.getReviewerLabels()
              : []),
            ...(mapping?.getReviewerIds() ? mapping.getReviewerIds() : []),
          ].join(", ")}
        </div>
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
