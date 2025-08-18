import React, {ReactNode, useState} from "react";
import {AssertedMapping, Mapping, MappingFields} from "../../model/Mapping";
import {useNavigate} from "react-router-dom";
import {MappingItem} from "../../components/mapping/MappingItem";
import {ChevronDownIcon, ChevronUpIcon} from "@heroicons/react/24/solid";
import AssertedMappings from "./AssertedMappings.tsx";

const hasValue = (value?: string | number | string[] | Record<string, string> | AssertedMapping[]): boolean => {
    if (value === undefined) return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === 'object') return Object.keys(value).length > 0;
    if (typeof value === 'string') return value.trim() !== '';
    return true;
};

function LabeledValue({ label, value }: { label: string; value?: string | number }) {
    if (!hasValue(value)) return null;

    return (
        <div className="mb-2">
            <span className="font-semibold">{label}:</span>{" "}
            <span className="ml-2">{value}</span>
        </div>
    );
}

function Section({
                     title,
                     children,
                     renderComponent = true,
                     useHideShow = true,
                     showBackground = true
                 }: {
    title: string;
    children: ReactNode;
    renderComponent?: boolean;
    useHideShow?: boolean;
    showBackground?: boolean;
}) {
    const hasChildren = React.Children.count(children) > 0;
    const [isExpanded, setIsExpanded] = useState(true);

    if (!hasChildren || !renderComponent) return null;

    return (
        <div className="mb-8">
            <div className="section-title">
                <h1 className="section-heading">{title}</h1>
                {useHideShow && (
                    <button
                        onClick={() => setIsExpanded(!isExpanded)}
                        className="text-orange-500 hover:text-orange-950 focus:outline-none"
                    >
            <span className="flex items-center">
              {isExpanded ? <ChevronUpIcon className="h-4 w-4" /> : <ChevronDownIcon className="h-4 w-4" />}
                <span className="ml-1">{isExpanded ? "Hide" : "Show"}</span>
            </span>
                    </button>
                )}
            </div>
            {(!useHideShow || isExpanded) && (
                <div className={`section-content ${showBackground ? 'card' : ''}`}>
                    {children}
                </div>
            )}
        </div>
    );
}

function MappingDetails({ mapping }: { mapping: Mapping }) {
    const navigate = useNavigate();

    if (!mapping) {
        return (
            <div className="alert-info">
                No mapping details available.
            </div>
        );
    }

    const hasAnyValue = (fields: MappingFields[]) =>
        fields.some(field => hasValue(mapping[field]));

    return (
        <div className="container mx-auto px-4 py-8">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Mapping Details</h1>
                <button
                    className="button-primary text-base font-bold px-4 py-1"
                    onClick={() => navigate(-1)}
                >
                    Back to results
                </button>
            </div>


            <Section title="Mapping" useHideShow={false} showBackground={false}>
                <MappingItem
                    mapping={mapping}
                    navigateFn={navigate}
                    showDetailsLink={false}
                    alwaysHighlighted={true}
                />
            </Section>

            <Section
                title="Inference Details"
                renderComponent={hasAnyValue([
                    MappingFields.assertedMappings
                ])}
            >
                <AssertedMappings
                    mapping={mapping}/>
            </Section>

            <Section
                title="Justification"
                renderComponent={hasAnyValue([
                    MappingFields.mappingJustification,
                    MappingFields.confidence])}
            >
                <LabeledValue label="Mapping Justification" value={mapping.mappingJustification} />
                <LabeledValue label="Confidence" value={mapping.confidence} />
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
                <LabeledValue label="Mapping Set ID" value={mapping.mappingSetId} />
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
    );
}

export default MappingDetails;