import {MappingVisibility} from "../../pages/results/useMappingVisibility";

export function EmptyMappingResults({visibility, onIncludeAll}: {
    visibility: MappingVisibility;
    onIncludeAll: () => void;
}) {
    const {allTotal, additional, loading, unavailable} = visibility;
    const needsCombination = !loading && !unavailable && allTotal !== undefined && allTotal > 0
        && Object.values(additional).length > 0 && Object.values(additional).every(count => count === 0);
    return (
        <div className="p-6 text-left not-italic whitespace-normal max-w-[calc(100vw-2rem)]" role="status">
            <p className="font-bold">No mappings match your current settings.</p>
            {allTotal !== undefined && allTotal > 0 ? <>
                <p className="mt-2">Additional mappings are hidden by the options above. Select an option to include them.</p>
                {needsCombination && <p className="mt-2">Some mappings require more than one option. Include all three to reveal them.</p>}
                <button type="button" className="button-primary mt-3 px-4 py-2" onClick={onIncludeAll}>
                    Include all three · show {allTotal.toLocaleString()} {allTotal === 1 ? 'result' : 'results'}
                </button>
            </> : allTotal === 0 ? <>
                <p className="mt-2">No mappings found, including subclass relationships, cross-references and obsolete terms.</p>
                <p className="mt-2">Try reviewing your ontology restrictions, column filters or label matching under More options.</p>
            </> : <p className="mt-2">{loading ? 'Checking for mappings hidden by your options…'
                : unavailable ? 'Counts are unavailable. Try the Also include options above, or review your search filters.'
                    : 'Try the Also include options above, or review your search filters.'}</p>}
        </div>
    );
}
