import {useEffect, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link, useLocation} from 'react-router';
import {get} from '../../app/api';
import {MappingSearchResponse, fromMappingResponse} from '../../pages/results/MappingResultsSlice';
import {mappingJustificationLabel, mappingJustificationShortName} from '../../model/MappingJustification';
import {mappingSetHref} from '../../util/mappingSetUrl';
import {InferenceTypeBadge} from './InferenceTypeBadge';
import {InferenceType} from '../../model/InferenceType';

interface SupportResponse extends MappingSearchResponse {
    justificationMethodCount: number;
    assertedSourceCount: number;
}

/** Counts and pages always cover the dataset-wide group, including the selected record itself. */
export function SupportingMappings({mappingId, inferenceType}: {mappingId: string; inferenceType?: InferenceType}) {
    const [page, setPage] = useState(0);
    const section = useRef<HTMLElement>(null);
    const {hash} = useLocation();
    const {data, isPending, isError, refetch} = useQuery({
        queryKey: ['mappingSupport', mappingId, page],
        queryFn: () => get<SupportResponse>(`/api/v2/mappings/${encodeURIComponent(mappingId)}/support?page=${page}&size=20`),
        staleTime: 5 * 60 * 1000,
    });
    // An inferred record alone adds no supporting mappings beyond the explanation below.
    // Wait for the total before showing the section, avoiding a flash of a redundant panel.
    const hidden = inferenceType === 'SSSOM_INFERENCE'
        && (isPending || (data !== undefined && data.mappings.totalElements <= 1));
    useEffect(() => {
        if (!hidden && hash === '#supporting-mappings') section.current?.scrollIntoView();
    }, [hash, mappingId, hidden]);

    if (hidden) return null;

    return (
        <section ref={section} id="supporting-mappings" aria-labelledby="supporting-mappings-heading"
            className="rounded-lg border border-gray-200 bg-white p-4 space-y-3">
            <h2 id="supporting-mappings-heading" className="text-lg font-semibold">Supporting mappings</h2>
            <p className="text-sm text-gray-600">
                All records for this mapping across the dataset, including this record and any hidden by your search filters.
                Source sets may share evidence; their count does not measure independent confirmation.
            </p>
            {isPending && <p role="status">Loading supporting mappings…</p>}
            {isError && <div role="alert">Could not load supporting mappings.{' '}
                <button type="button" className="text-link-default underline" onClick={() => void refetch()}>Retry</button>
            </div>}
            {data && <>
                <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
                    <div><dt className="text-gray-600">Supporting records</dt><dd className="text-xl font-semibold">{data.mappings.totalElements}</dd></div>
                    <div><dt className="text-gray-600">Justification methods</dt><dd className="text-xl font-semibold">{data.justificationMethodCount}</dd></div>
                    <div><dt className="text-gray-600">Asserted source sets</dt><dd className="text-xl font-semibold">{data.assertedSourceCount}</dd></div>
                </dl>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <caption className="sr-only">Supporting mapping records</caption>
                        <thead><tr className="border-b border-gray-200">
                            {['Type', 'Justification', 'Mapping set / provider', 'Confidence', 'Details'].map(label =>
                                <th key={label} scope="col" className="py-2 pr-4 font-medium">{label}</th>)}
                        </tr></thead>
                        <tbody>{data.mappings.content.map(fromMappingResponse).map(mapping => (
                            <tr key={mapping.mappingId} className="border-b border-gray-100 align-top">
                                <td className="py-2 pr-4"><InferenceTypeBadge value={mapping.inferenceType} /></td>
                                <td className="py-2 pr-4" title={mappingJustificationLabel(mapping.mappingJustification)}>
                                    {mappingJustificationShortName(mapping.mappingJustification) || 'Not provided'}
                                </td>
                                <td className="py-2 pr-4 break-words">
                                    {mapping.mappingSetId ? <Link className="text-link-default hover:underline" to={mappingSetHref(mapping.mappingSetId)}>
                                        {mapping.mappingSetTitle || mapping.mappingSetId}
                                    </Link> : 'Not provided'}
                                    {mapping.mappingProvider && <div className="text-gray-600">{mapping.mappingProvider}</div>}
                                </td>
                                <td className="py-2 pr-4">{mapping.confidence ?? '—'}</td>
                                <td className="py-2 pr-4 whitespace-nowrap">
                                    {mapping.mappingId === mappingId ? <span>Current record</span> :
                                        <Link className="text-link-default hover:underline" to={`/mapping/${encodeURIComponent(mapping.mappingId)}`}>
                                            {mapping.inferenceType === 'SSSOM_INFERENCE' ? 'View explanation' : 'View record'}
                                        </Link>}
                                </td>
                            </tr>
                        ))}</tbody>
                    </table>
                </div>
                <nav aria-label="Supporting mappings pagination" className="flex items-center justify-between gap-3 text-sm">
                    <button type="button" className="button-secondary px-3 py-1 disabled:opacity-40" disabled={page === 0}
                        onClick={() => setPage(page - 1)}>Previous</button>
                    <span aria-live="polite">Page {page + 1} of {Math.max(1, data.mappings.totalPages)} · {data.mappings.totalElements} records</span>
                    <button type="button" className="button-secondary px-3 py-1 disabled:opacity-40" disabled={page + 1 >= data.mappings.totalPages}
                        onClick={() => setPage(page + 1)}>Next</button>
                </nav>
            </>}
        </section>
    );
}
