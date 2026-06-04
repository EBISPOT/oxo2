import { useState, type JSX, type MouseEvent } from "react";
import { CheckIcon, ClipboardDocumentIcon } from "@heroicons/react/24/outline";
import olsLogo from "/public/logo.svg";

/**
 * Small copy-to-clipboard button. Stops click propagation so it never triggers
 * the surrounding table header (sort) or any row-level handler.
 */
export function CopyButton({ value, title = "Copy" }: { value?: string; title?: string }): JSX.Element | null {
    const [copied, setCopied] = useState(false);

    if (!value) {
        return null;
    }

    const handleCopy = (event: MouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        if (!navigator.clipboard) {
            return;
        }
        navigator.clipboard
            .writeText(value)
            .then(() => {
                setCopied(true);
                setTimeout(() => setCopied(false), 800);
            })
            .catch(() => {});
    };

    return (
        <button
            type="button"
            title={title}
            onClick={handleCopy}
            className="inline-flex shrink-0 ml-1 align-middle text-gray-400 hover:text-link-default cursor-pointer"
        >
            {copied ? <CheckIcon className="h-4 w-4" /> : <ClipboardDocumentIcon className="h-4 w-4" />}
        </button>
    );
}

/**
 * Renders one SSSOM entity reference (subject, object, or predicate) as a stacked
 * id › label › IRI cell. The id is primary (with copy and, for subject/object, an
 * OLS link); the label is secondary; the IRI is a muted line with its own copy
 * button. A predicate carries no OLS link (skos:/owl:/RO: predicates rarely resolve
 * in OLS) but surfaces predicate_modifier as a NOT badge when present, since the
 * modifier negates the mapping's meaning.
 */
export function EntityRefCell({
    id,
    iri,
    label,
    showOlsLink = false,
    modifier,
}: {
    id?: string;
    iri?: string;
    label?: string;
    showOlsLink?: boolean;
    modifier?: string;
}): JSX.Element {
    const primary = id || iri || "—";

    return (
        <div className="flex flex-col gap-0.5 py-1 min-w-0">
            <div className="flex items-center flex-wrap font-bold break-all">
                {modifier && (
                    <span
                        title={`predicate_modifier = ${modifier}`}
                        className="mr-1 px-1.5 py-0.5 text-xs font-bold rounded bg-red-600 text-white uppercase"
                    >
                        {modifier}
                    </span>
                )}
                <span className="break-all">{primary}</span>
                {id && <CopyButton value={id} title="Copy identifier" />}
                {showOlsLink && id && (
                    <a
                        href={`http://www.ebi.ac.uk/ols4?termId=${encodeURIComponent(id)}`}
                        title={`View ${id} in OLS`}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={(event) => event.stopPropagation()}
                        className="inline-flex shrink-0 ml-1 align-middle"
                    >
                        <img src={olsLogo} alt="OLS" className="h-4 w-4 inline-block" />
                    </a>
                )}
            </div>
            {label && <div className="text-sm text-neutral-black break-words">{label}</div>}
            {iri && (
                <div className="flex items-start text-xs text-gray-500">
                    <span className="break-all">{iri}</span>
                    <CopyButton value={iri} title="Copy IRI" />
                </div>
            )}
        </div>
    );
}
