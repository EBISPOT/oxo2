import { useQuery } from "@tanstack/react-query";
import { CircleStackIcon } from "@heroicons/react/24/solid";
import { JSX } from "react";
import { fetchDataContent } from "./DataContentSlice";
import { HelpTerm } from "../../components/common/HelpTerm";

/**
 * Plain-language explanations of the six counts, shown on hover (or keyboard focus) of the row label.
 *
 * This is the landing page — the reader is the visitor who knows least about OxO2, and six bare
 * numbers labelled with domain terms tell them nothing. "Mappings" and "mapping sets" are not
 * self-evident, and neither is why one number is split the way it is.
 *
 * Unlike COLUMN_HELP, there is no exported topic union: these keys are read by direct property access
 * from the call sites a few lines below, where a typo is already a compile error. The union exists in
 * ColumnHelp only because its key arrives as a prop from other files.
 */
const DATA_CONTENT_HELP = {
    mappings:
        "Every mapping held; counted individually, so the same mapping appearing in 2 different sets " +
        "counts twice. Asserted + inferred mappings split it exactly.",
    asserted:
        "Mappings taken directly from a loaded mapping set, exactly as its publisher stated them. " +
        "OxO2 has added nothing to them.",
    inferred:
        "Derived by OxO2 chaining sets — A→B plus B→C gives A→C. Appears in no input file.",
    mappingSets:
        "The published collections of mappings loaded into OxO2. Each set is one file or release from " +
        "one provider. OxO2's own inferences are not a loaded set and are not counted here.",
    curated: "Assembled by people and published as mappings in their own right.",
    ontologies: "Sets from an ontology's own cross-references, one per ontology.",
} as const;

/**
 * The landing page's Data Content block (ADR-0043): the release date of the loaded corpus, the mapping
 * count split into asserted and inferred, and the mapping-set count split into curated sets and
 * ontologies. Occupies the right-hand column of the home grid.
 *
 * Sub-counts are indented under their total rather than shown as siblings, because they are a
 * breakdown, not more statistics: asserted + inferred is the mapping total, and curated + ontologies is
 * the mapping-set total. Presenting them flat would invite reading six independent numbers and adding
 * the wrong ones.
 */
export function DataContent(): JSX.Element | null {
    const { data, isLoading, isError } = useQuery({
        queryKey: ["dataContent"],
        queryFn: fetchDataContent,
        // The corpus only changes on a dataload, so there is nothing to gain from refetching within a
        // session — and this sits on the landing page, which every visit and every "back" hits.
        staleTime: Infinity,
    });

    // A summary is decoration, not function: if it cannot load, the search still works, so fail silent
    // rather than showing an error box beside the search box.
    if (isError) {
        return null;
    }

    return (
        <div className="card mb-8">
            <div className="flex items-center space-x-3 mb-4">
                <CircleStackIcon className="w-6 h-6 text-yellow-default" />
                <span className="text-2xl text-neutral-default">Data Content</span>
            </div>

            {isLoading && (
                <div className="flex justify-center p-4">
                    <div className="spinner-default w-6 h-6 animate-spin" role="status">
                        <span className="sr-only">Loading data content...</span>
                    </div>
                </div>
            )}

            {data && (
                <dl className="text-base text-neutral-dark">
                    {/* Omitted entirely when unknown: an empty or "unknown" release date reads as a
                        data problem, when in fact it only means the corpus predates the field. */}
                    {data.releaseDate && (
                        <div className="flex justify-between gap-2 py-1 border-b border-gray-200">
                            <dt>Data release</dt>
                            <dd className="font-semibold text-right">
                                {formatReleaseDate(data.releaseDate)}
                            </dd>
                        </div>
                    )}

                    <Total
                        label="Mappings"
                        help={DATA_CONTENT_HELP.mappings}
                        value={data.mappings.total}
                    />
                    <SubCount
                        label="Asserted"
                        help={DATA_CONTENT_HELP.asserted}
                        value={data.mappings.asserted}
                    />
                    <SubCount
                        label="Inferred"
                        help={DATA_CONTENT_HELP.inferred}
                        value={data.mappings.inferred}
                    />

                    <Total
                        label="Mapping sets"
                        help={DATA_CONTENT_HELP.mappingSets}
                        value={data.mappingSets.total}
                    />
                    <SubCount
                        label="Curated sets"
                        help={DATA_CONTENT_HELP.curated}
                        value={data.mappingSets.curated}
                    />
                    <SubCount
                        label="Ontologies"
                        help={DATA_CONTENT_HELP.ontologies}
                        value={data.mappingSets.ontologies}
                    />
                </dl>
            )}
        </div>
    );
}

/** A headline count: the total its sub-counts break down. */
function Total({
    label,
    help,
    value,
}: {
    label: string;
    help: string;
    value: number;
}): JSX.Element {
    return (
        <div className="flex justify-between gap-2 pt-3 pb-1">
            <dt className="font-semibold">
                <HelpTerm help={help} label={label} />
            </dt>
            <dd className="font-semibold text-right">{formatCount(value)}</dd>
        </div>
    );
}

/** One part of the total above it, indented to show it is a breakdown rather than a separate figure. */
function SubCount({
    label,
    help,
    value,
}: {
    label: string;
    help: string;
    value: number;
}): JSX.Element {
    return (
        <div className="flex justify-between gap-2 py-0.5 pl-4 text-sm text-neutral-default">
            <dt>
                <HelpTerm help={help} label={label} />
            </dt>
            <dd className="text-right">{formatCount(value)}</dd>
        </div>
    );
}

/** Thousands separators in the user's locale — these numbers run to eight digits. */
function formatCount(value: number): string {
    return value.toLocaleString();
}

/**
 * Render the release instant as a plain date. The time of day is an artefact of when the dataload
 * happened to run, not information about the data, so it is dropped. An unparseable value falls back to
 * the raw string rather than showing "Invalid Date".
 */
function formatReleaseDate(isoInstant: string): string {
    const date = new Date(isoInstant);
    if (Number.isNaN(date.getTime())) {
        return isoInstant;
    }
    return date.toLocaleDateString(undefined, {
        day: "numeric",
        month: "long",
        year: "numeric",
        timeZone: "UTC",
    });
}
