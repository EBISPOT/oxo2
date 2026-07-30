import { useQuery } from "@tanstack/react-query";
import { CircleStackIcon } from "@heroicons/react/24/solid";
import { JSX } from "react";
import { fetchDataContent } from "./DataContentSlice";

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

                    <Total label="Mappings" value={data.mappings.total} />
                    <SubCount label="Asserted" value={data.mappings.asserted} />
                    <SubCount label="Inferred" value={data.mappings.inferred} />

                    <Total label="Mapping sets" value={data.mappingSets.total} />
                    <SubCount label="Curated sets" value={data.mappingSets.curated} />
                    <SubCount label="Ontologies" value={data.mappingSets.ontologies} />
                </dl>
            )}
        </div>
    );
}

/** A headline count: the total its sub-counts break down. */
function Total({ label, value }: { label: string; value: number }): JSX.Element {
    return (
        <div className="flex justify-between gap-2 pt-3 pb-1">
            <dt className="font-semibold">{label}</dt>
            <dd className="font-semibold text-right">{formatCount(value)}</dd>
        </div>
    );
}

/** One part of the total above it, indented to show it is a breakdown rather than a separate figure. */
function SubCount({ label, value }: { label: string; value: number }): JSX.Element {
    return (
        <div className="flex justify-between gap-2 py-0.5 pl-4 text-sm text-neutral-default">
            <dt>{label}</dt>
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
