import { useId, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { Mapping } from '../../model/Mapping';
import { fetchMappingSetById } from '../../pages/results/MappingSetsSlice';
import { createMappingReport, reportingDestinations } from '../../util/mappingReport';

export function ReportIncorrectMapping({ mapping, inline = false, inferredMappingUrl }: {
    mapping: Mapping;
    inline?: boolean;
    inferredMappingUrl?: string;
}) {
    const [expanded, setExpanded] = useState(false);
    const [copyStatus, setCopyStatus] = useState('');
    const panelId = useId();
    const textId = useId();
    const { data: mappingSet, isPending, isError, refetch } = useQuery({
        queryKey: ['mappingSetById', mapping.mappingSetId],
        queryFn: () => fetchMappingSetById(mapping.mappingSetId),
        enabled: Boolean(mapping.mappingSetId),
        staleTime: Infinity,
    });

    const mappingUrl = new URL(
        `${import.meta.env.BASE_URL}mapping/${encodeURIComponent(mapping.mappingId)}`,
        window.location.origin,
    ).href;
    const report = createMappingReport(mapping, mappingUrl, inferredMappingUrl);
    const reportText = `${report.title}\n\n${report.body}`;
    const destinations = mappingSet ? reportingDestinations(mappingSet, report) : [];

    async function copyReport() {
        try {
            await navigator.clipboard.writeText(reportText);
            setCopyStatus('Report text copied.');
        } catch {
            setCopyStatus('Could not copy automatically. Select and copy the report text below.');
        }
    }

    if (!mapping.mappingSetId) {
        return <p className="mb-6 text-sm text-gray-600">No reporting contact available for this mapping source.</p>;
    }
    if (isPending) {
        return <p className="mb-6 text-sm text-gray-600" role="status">Loading reporting contacts…</p>;
    }
    if (isError) {
        return (
            <p className="mb-6 text-sm text-gray-600" role="status">
                Could not load reporting contacts.{' '}
                <button type="button" className="text-link-default underline" onClick={() => void refetch()}>
                    Try again
                </button>
            </p>
        );
    }
    if (destinations.length === 0) {
        return <p className="mb-6 text-sm text-gray-600">No reporting contact available for this mapping source.</p>;
    }

    return (
        <div className="mb-8">
            {!inline && <button
                type="button"
                className="button-primary px-4 py-2"
                aria-expanded={expanded}
                aria-controls={panelId}
                onClick={() => setExpanded(!expanded)}
            >
                Report incorrect mapping
            </button>}
            {(inline || expanded) && (
                <div id={panelId} className={inline ? 'space-y-3' : 'card mt-3 p-4 space-y-3'}>
                    <p>
                        Report this asserted mapping to the maintainers of{' '}
                        <strong className="break-all">{mappingSet?.mappingSetTitle || mapping.mappingSetId}</strong>.
                        {' '}Explain what is wrong, then review and submit your report at the destination.
                    </p>
                    <div className="flex flex-wrap gap-4 items-start">
                        {destinations.map((destination, index) => (
                            <div key={destination.label}>
                                <a
                                    href={destination.href}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className={index === 0 ? 'button-primary inline-block px-4 py-2' : 'text-link-default underline inline-block py-2'}
                                >
                                    {destination.label}
                                </a>
                                <p className="text-sm text-gray-600 mt-1">
                                    {destination.prefilled ? 'Mapping details are prefilled.' : 'Copy the report text to include the mapping details.'}
                                </p>
                            </div>
                        ))}
                    </div>
                    <div>
                        <label htmlFor={textId} className="block font-semibold mb-2">Report text</label>
                        <textarea
                            id={textId}
                            value={reportText}
                            readOnly
                            rows={12}
                            className="w-full border border-gray-300 rounded p-3 text-sm bg-white"
                        />
                        <button type="button" className="text-link-default underline mt-2" onClick={() => void copyReport()}>
                            Copy report text
                        </button>
                        <p role="status" className="text-sm mt-1">{copyStatus}</p>
                    </div>
                </div>
            )}
        </div>
    );
}
