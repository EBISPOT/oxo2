import { CorpusMode, CORPUS_HINTS, CORPUS_LABELS, CORPUS_ORDER } from "../../model/MappingSetCategory";

/**
 * "Where should mappings come from?" — the asserted corpora a search covers (ADR-0027).
 *
 * A segmented control rather than a dropdown: there are only three choices and the trade-off between
 * them is the point, so they should all be visible at once. Inferred mappings are unaffected by this
 * choice; that is the inference-type filter's job, on the results table.
 */
export function CorpusSelector({ value, onChange }: {
    value: CorpusMode;
    onChange: (mode: CorpusMode) => void;
}) {
    return (
        <div>
            <div className="text-tertiary mb-2">Where should mappings come from?</div>
            <div role="radiogroup" aria-label="Where should mappings come from?" className="flex flex-wrap gap-1">
                {CORPUS_ORDER.map((mode) => {
                    const selected = mode === value;
                    return (
                        <button
                            key={mode}
                            type="button"
                            role="radio"
                            aria-checked={selected}
                            title={CORPUS_HINTS[mode]}
                            className={`px-3 py-1 rounded-md border-1 text-base cursor-pointer transition-colors ${
                                selected
                                    ? "bg-link-default text-white border-link-default"
                                    : "bg-white text-neutral-dark border-neutral-dark hover:border-link-default hover:text-link-default"
                            }`}
                            onClick={() => onChange(mode)}
                        >
                            {CORPUS_LABELS[mode]}
                        </button>
                    );
                })}
            </div>
            <div className="text-tertiary text-sm mt-1">{CORPUS_HINTS[value]}</div>
        </div>
    );
}
