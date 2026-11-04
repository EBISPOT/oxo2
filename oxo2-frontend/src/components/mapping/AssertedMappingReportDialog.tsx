import { useId } from 'react';
import { useQuery } from '@tanstack/react-query';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import { fetchMappingById } from '../../pages/results/MappingResultsSlice';
import { EntityRefCell } from './EntityRefCell';
import { ReportIncorrectMapping } from './ReportIncorrectMapping';

/** Fetch the complete asserted mapping on demand; premise summaries omit report-critical metadata. */
export function AssertedMappingReportDialog({ mappingId, inferredMappingId, onClose }: {
    mappingId: string | null;
    inferredMappingId: string;
    onClose: () => void;
}) {
    const titleId = useId();
    const { data: mapping, isPending, isError, refetch } = useQuery({
        queryKey: ['mappingById', mappingId],
        queryFn: () => fetchMappingById(mappingId!),
        enabled: !!mappingId,
        staleTime: 5 * 60_000,
        retry: false,
    });
    const inferredMappingUrl = new URL(
        `${import.meta.env.BASE_URL}mapping/${encodeURIComponent(inferredMappingId)}`,
        window.location.origin,
    ).href;
    return (
        <Dialog open={!!mappingId} onClose={onClose} fullWidth maxWidth="md" aria-labelledby={titleId}>
            <DialogTitle id={titleId}>Report incorrect asserted mapping</DialogTitle>
            <DialogContent dividers>
                {isPending ? (
                    <p role="status">Loading asserted mapping…</p>
                ) : isError ? (
                    <p role="alert">
                        Could not load this asserted mapping.{' '}
                        <button type="button" className="text-link-default underline" onClick={() => void refetch()}>
                            Try again
                        </button>
                    </p>
                ) : !mapping || mapping.inferenceType !== 'ASSERTED' ? (
                    <p role="status">This asserted mapping is no longer available for reporting.</p>
                ) : (
                    <>
                        <section aria-label="Asserted mapping to report"
                            className="grid grid-cols-1 md:grid-cols-3 gap-4 rounded bg-gray-50 p-4 mb-5">
                            <div className="min-w-0">
                                <h3 className="text-sm text-gray-600">Subject</h3>
                                <EntityRefCell id={mapping.subjectId} iri={mapping.subjectIri} label={mapping.subjectLabel} />
                            </div>
                            <div className="min-w-0">
                                <h3 className="text-sm text-gray-600">Predicate</h3>
                                <EntityRefCell id={mapping.predicateId} iri={mapping.predicateIri}
                                    label={mapping.predicateLabel} modifier={mapping.predicateModifier} />
                            </div>
                            <div className="min-w-0">
                                <h3 className="text-sm text-gray-600">Object</h3>
                                <EntityRefCell id={mapping.objectId} iri={mapping.objectIri} label={mapping.objectLabel} />
                            </div>
                        </section>
                        <ReportIncorrectMapping key={mapping.mappingId} mapping={mapping} inline
                            inferredMappingUrl={inferredMappingUrl} />
                    </>
                )}
            </DialogContent>
            <DialogActions>
                <button type="button" className="text-link-default px-3 py-2 hover:underline" onClick={onClose}>
                    Close report
                </button>
            </DialogActions>
        </Dialog>
    );
}
