import {ReactNode, useId, useState} from "react";
import {InferredMapping, Mapping, MappingFields} from "../../model/Mapping";
import {mappingJustificationShortName, mappingJustificationLabel} from "../../model/MappingJustification";
import {Link, useNavigate} from "react-router";
import {mappingSetHref} from "../../util/mappingSetUrl";
import {MappingItem} from "../../components/mapping/MappingItem";
import {InferenceTypeBadge} from "../../components/mapping/InferenceTypeBadge";
import {ChevronDownIcon, ChevronUpIcon} from "@heroicons/react/24/solid";
import InferredMappings from "./InferredMappings.tsx";
import InferredMappingGraph from "../../components/mapping/InferredMappingGraph.tsx";
import {ReportIncorrectMapping} from "../../components/mapping/ReportIncorrectMapping";

const hasValue = (value?: string | number | string[] | Record<string, string> | InferredMapping | InferredMapping[]): boolean => {
    if (value == null) return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === 'object') return Object.keys(value).length > 0;
    if (typeof value === 'string') return value.trim() !== '';
    return true;
};

function LabeledValue({ label, value, title }: { label: string; value?: string | number; title?: string }) {
    if (!hasValue(value)) return null;

    return (
        <div className="grid grid-cols-1 sm:grid-cols-[10rem_minmax(0,1fr)] gap-x-4 gap-y-1 py-2 text-sm">
            <span className="font-medium text-gray-600">{label}</span>
            <span className="min-w-0 break-words [overflow-wrap:anywhere]" title={title}>{value}</span>
        </div>
    );
}

function Section({ title, children, renderComponent = true, useHideShow = true }: {
    title: string;
    children: ReactNode;
    renderComponent?: boolean;
    useHideShow?: boolean;
}) {
    const [isExpanded, setIsExpanded] = useState(true);
    const headingId = useId();
    const contentId = useId();
    if (!renderComponent) return null;
    return (
        <section aria-labelledby={headingId} className="min-w-0 rounded-lg border border-gray-200 bg-white">
            <div className="flex items-center justify-between gap-4 px-5 py-3 border-b border-gray-100">
                <h2 id={headingId} className="text-lg font-semibold">{title}</h2>
                {useHideShow && (
                    <button type="button" onClick={() => setIsExpanded(!isExpanded)}
                        aria-expanded={isExpanded} aria-controls={contentId}
                        aria-label={`${isExpanded ? 'Hide' : 'Show'} ${title.toLowerCase()}`}
                        className="flex items-center gap-1 text-sm text-link-default hover:underline">
                        {isExpanded ? <ChevronUpIcon className="h-4 w-4" /> : <ChevronDownIcon className="h-4 w-4" />}
                        {isExpanded ? 'Hide' : 'Show'}
                    </button>
                )}
            </div>
            <div id={contentId} hidden={useHideShow && !isExpanded} className="p-5">
                {children}
            </div>
        </section>
    );
}

function MappingDetails({ mapping }: { mapping: Mapping }) {
    const navigate = useNavigate();
    // React Router keeps its history index in window.history.state.idx; 0 means this page was
    // reached directly (shared link, bookmark), so there are no results to go back to.
    const hasInAppHistory = (window.history.state?.idx ?? 0) > 0;

    if (!mapping) {
        return (
            <div className="alert-info">
                No mapping details available.
            </div>
        );
    }

    const hasAnyValue = (fields: MappingFields[]) =>
        fields.some(field => hasValue(mapping[field]));

    const missingScores = [
        !hasValue(mapping.confidence) && 'confidence',
        !hasValue(mapping.similarityScore) && 'similarity score',
    ].filter(Boolean);

    return (
        // Match the landing page's reading width; the expanded graph uses its own fullscreen dialog.
        <main className="container mx-auto py-6 space-y-6">
            <div className="flex flex-wrap justify-between items-center gap-3">
                <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold">Mapping Details</h1>
                    <InferenceTypeBadge value={mapping.inferenceType} />
                </div>
                <button
                    className="button-primary text-base font-bold px-4 py-1"
                    onClick={() => hasInAppHistory ? navigate(-1) : navigate("/")}
                >
                    {hasInAppHistory ? "Back to results" : "New search"}
                </button>
            </div>


            <section aria-labelledby="mapping-summary-heading">
                <h2 id="mapping-summary-heading" className="section-heading">Mapping</h2>
                <div className="p-4">
                    <MappingItem
                        mapping={mapping}
                        navigateFn={navigate}
                        showDetailsLink={false}
                        alwaysHighlighted
                    />
                </div>
            </section>

            {mapping.inferenceType === 'ASSERTED' && (
                <ReportIncorrectMapping key={mapping.mappingId} mapping={mapping} />
            )}

            <Section title="Inference Details"
                renderComponent={!!mapping.explanation || hasAnyValue([MappingFields.assertedMappings])}>
                {mapping.explanation && <InferredMappingGraph explanation={mapping.explanation} />}
                {!!mapping.assertedMappings?.length && (
                    <div className={mapping.explanation ? 'mt-6' : ''}>
                        <InferredMappings mapping={mapping} />
                    </div>
                )}
            </Section>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
                <Section title="Justification">
                    <LabeledValue label="Mapping justification"
                        value={mappingJustificationShortName(mapping.mappingJustification)}
                        title={mappingJustificationLabel(mapping.mappingJustification)} />
                    <LabeledValue label="Confidence" value={mapping.confidence} />
                    <LabeledValue label="Similarity score" value={mapping.similarityScore} />
                    <LabeledValue label="Similarity measure" value={mapping.similarityMeasure} />
                    {missingScores.length > 0 && (
                        <p className="text-sm text-gray-500 py-2">Not provided: {missingScores.join(', ')}.</p>
                    )}
                </Section>

                <Section
                    title="Provenance"
                    renderComponent={hasAnyValue([
                        MappingFields.authorId,
                        MappingFields.authorLabel,
                        MappingFields.creatorId,
                        MappingFields.creatorLabel,
                        MappingFields.reviewerId,
                        MappingFields.reviewerLabel,
                        MappingFields.mappingDate,
                        MappingFields.publicationDate,
                        MappingFields.mappingProvider
                    ])}
                >
                    <LabeledValue label="Author Id" value={mapping.authorId} />
                    <LabeledValue label="Author Label" value={mapping.authorLabel} />
                    <LabeledValue label="Creator Id" value={mapping.creatorId} />
                    <LabeledValue label="Creator Label" value={mapping.creatorLabel} />
                    <LabeledValue label="Reviewer Id" value={mapping.reviewerId} />
                    <LabeledValue label="Reviewer Label" value={mapping.reviewerLabel} />
                    <LabeledValue label="Mapping Date" value={mapping.mappingDate} />
                    <LabeledValue label="Publication Date" value={mapping.publicationDate} />
                    <LabeledValue label="Mapping Provider" value={mapping.mappingProvider} />
                </Section>

                <Section
                    title="Mapping Set"
                    renderComponent={hasAnyValue([
                        MappingFields.mappingSetId,
                        MappingFields.mappingSetTitle,
                        MappingFields.mappingSetDescription,
                        MappingFields.mappingSetSource,
                        MappingFields.mappingSetVersion
                    ])}
                >
                    {mapping.mappingSetId && (
                        <div className="grid grid-cols-1 sm:grid-cols-[10rem_minmax(0,1fr)] gap-x-4 gap-y-1 py-2 text-sm">
                            <span className="font-medium text-gray-600">Mapping Set ID</span>
                            <Link
                                to={mappingSetHref(mapping.mappingSetId)}
                                className="text-link-default hover:underline break-all"
                                title="View mapping set details"
                            >
                                {mapping.mappingSetId}
                            </Link>
                        </div>
                    )}
                    <LabeledValue label="Mapping Set Title" value={mapping.mappingSetTitle} />
                    <LabeledValue label="Mapping Set Description" value={mapping.mappingSetDescription} />
                    <LabeledValue label="Mapping Set Source" value={mapping.mappingSetSource} />
                    <LabeledValue label="Mapping Set Version" value={mapping.mappingSetVersion} />
                </Section>

                <Section
                    title="Additional Information"
                    renderComponent={hasAnyValue([
                        MappingFields.license,
                        MappingFields.comment,
                        MappingFields.mappingTool,
                        MappingFields.mappingToolVersion,
                        MappingFields.mappingSource
                    ])}
                >
                    <LabeledValue label="License" value={mapping.license} />
                    <LabeledValue label="Comment" value={mapping.comment} />
                    <LabeledValue label="Mapping Tool" value={mapping.mappingTool} />
                    <LabeledValue label="Mapping Tool Version" value={mapping.mappingToolVersion} />
                    <LabeledValue label="Mapping Source" value={mapping.mappingSource} />
                </Section>

                <Section
                    title="Subject Details"
                    renderComponent={hasAnyValue([
                        MappingFields.subjectCategory,
                        MappingFields.subjectType,
                        MappingFields.subjectSource,
                        MappingFields.subjectSourceVersion
                    ])}
                >
                    <LabeledValue label="Subject Category" value={mapping.subjectCategory} />
                    <LabeledValue label="Subject Type" value={mapping.subjectType} />
                    <LabeledValue label="Subject Source" value={mapping.subjectSource} />
                    <LabeledValue label="Subject Source Version" value={mapping.subjectSourceVersion} />
                </Section>

                <Section
                    title="Object Details"
                    renderComponent={hasAnyValue([
                        MappingFields.objectCategory,
                        MappingFields.objectType,
                        MappingFields.objectSource,
                        MappingFields.objectSourceVersion
                    ])}
                >
                    <LabeledValue label="Object Category" value={mapping.objectCategory} />
                    <LabeledValue label="Object Type" value={mapping.objectType} />
                    <LabeledValue label="Object Source" value={mapping.objectSource} />
                    <LabeledValue label="Object Source Version" value={mapping.objectSourceVersion} />
                </Section>
            </div>
        </main>
    );
}

export default MappingDetails;

