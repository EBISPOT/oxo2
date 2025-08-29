import { useState, JSX } from "react";
import { Mapping } from "../../model/Mapping";
import { EyeIcon } from "@heroicons/react/24/solid";
import olsLogo from "/public/logo.svg";

export async function copyToClipboard(text: string): Promise<void> {
    if (!navigator.clipboard) {
        throw new Error('Clipboard API not available');
    }
    return navigator.clipboard.writeText(text);
}

function copyText(text: string, setToggle: (toggle: boolean) => void) {
    copyToClipboard(text)
        .then(() => {
            setToggle(true);
            setTimeout(() => {
                setToggle(false);
            }, 500);
        })
        .catch((err) => {
            console.log(err);
        });
}

export function MappingItem({
                                mapping,
                                navigateFn,
                                showDetailsLink = true,
                                alwaysHighlighted = false
                            }: {
    mapping: Mapping;
    navigateFn: (path: string, options?: { state: { mapping: Mapping } }) => void;
    showDetailsLink?: boolean;
    alwaysHighlighted?: boolean;
}): JSX.Element {
    const [isSubjectCopied, setIsSubjectCopied] = useState(false);
    const [isObjectCopied, setIsObjectCopied] = useState(false);

    function EntityBox({
                           id,
                           label,
                           isCopied,
                           setCopied,
                           isLeftSide
                       }: {
        id: string;
        label: string;
        isCopied: boolean;
        setCopied: (val: boolean) => void;
        isLeftSide: boolean;
    }): JSX.Element {
            const entityClasses = `mapping-entity 
                              ${isLeftSide ? 'mapping-entity-left' : 'mapping-entity-right'} 
                              ${alwaysHighlighted ? 'mapping-entity-highlighted' : 'mapping-entity-normal'}`;
        
        return (
            <div className={entityClasses}>
                <div className="text-center font-bold">
                    <span
                        className="pr-2 cursor-pointer hover:underline wrap-slash"
                        onClick={() => navigateFn(`/entity/${encodeURIComponent(id)}`)}
                    >
                    {id}
                    </span>
                    <i
                        title="Copy"
                        className={`icon icon-common icon-copy icon-spacer ${
                            isCopied ? "cursor-wait" : "cursor-pointer"
                        }`}
                        onClick={() => copyText(id, setCopied)}
                    />
                    <a
                        href={`http://www.ebi.ac.uk/ols4?termId=${encodeURIComponent(id)}`}
                        title={`View ${id} in OLS`}
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        <img
                            src={olsLogo}
                            alt="OLS"
                            className="h-6 w-6 inline-block icon-spacer"
                        />
                    </a>
                </div>
                <div title={label} className="text-center truncate-text">
                    {label}
                </div>
            </div>
        );
    }

    const mappingItemClasses = `group ${alwaysHighlighted ? 'mapping-item mapping-item-highlighted' : 'mapping-item mapping-item-hover'}`;
    const predicateClasses = `mapping-predicate ${alwaysHighlighted ? 'mapping-predicate-highlighted' : 'mapping-predicate-normal'}`;

    return (
        <div className={mappingItemClasses}>
            <div className="w-full lg:w-1/3">
                <EntityBox
                    id={mapping.subjectId}
                    label={mapping.subjectLabel}
                    isCopied={isSubjectCopied}
                    setCopied={setIsSubjectCopied}
                    isLeftSide={true}
                />
            </div>

            <div className={predicateClasses}>
                <div title={mapping.predicateId} className="text-center font-bold">
                    {mapping.predicateId}
                </div>
                <div title={mapping.predicateLabel} className="text-center truncate-text">
                    {mapping.predicateLabel}
                </div>
            </div>

            <div className="w-full lg:w-1/3">
                <EntityBox
                    id={mapping.objectId}
                    label={mapping.objectLabel}
                    isCopied={isObjectCopied}
                    setCopied={setIsObjectCopied}
                    isLeftSide={false}
                />
            </div>

            {showDetailsLink && (
                <div
                    className="link-default text-center cursor-pointer self-center my-2 mx-4"
                    onClick={() => navigateFn(`/mapping/${encodeURIComponent(mapping.mappingId)}`, { state: { mapping } })}
                    title="View mapping details"
                >
                    <EyeIcon className="h-5 w-5" />
                </div>
            )}
        </div>
    );
}