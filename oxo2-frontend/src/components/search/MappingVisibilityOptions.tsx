import {useId} from "react";
import {Tooltip} from "@mui/material";
import {WeakPredicate, WEAK_PREDICATE_ORDER, WEAK_PREDICATE_HINTS, WEAK_PREDICATE_LABELS} from "../../model/WeakPredicate";
import {MappingVisibility, VisibilityOption} from "../../pages/results/useMappingVisibility";

export function MappingVisibilityOptions({weakPredicates, includeObsolete, onChange, counts, pendingSearch}: {
    weakPredicates: WeakPredicate[];
    includeObsolete: boolean;
    onChange: (weak: WeakPredicate[], obsolete: boolean) => void;
    counts?: MappingVisibility;
    pendingSearch?: boolean;
}) {
    const id = useId();
    const options: {key: VisibilityOption; label: string; hint: string; checked: boolean}[] = [
        {key: 'subClassOf', label: WEAK_PREDICATE_LABELS.subClassOf, hint: WEAK_PREDICATE_HINTS.subClassOf,
            checked: weakPredicates.includes('subClassOf')},
        {key: 'hasDbXref', label: WEAK_PREDICATE_LABELS.hasDbXref, hint: WEAK_PREDICATE_HINTS.hasDbXref,
            checked: weakPredicates.includes('hasDbXref')},
        {key: 'obsolete', label: 'Obsolete terms',
            hint: "Terms an ontology has deprecated. Hidden by default; a live term's mappings to a dead one are hidden too.",
            checked: includeObsolete},
    ];
    return (
        <fieldset className="mt-4" aria-describedby={pendingSearch || counts ? `${id}-counts` : undefined}>
            <legend className="sr-only">Also include</legend>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
                <span className="text-tertiary" aria-hidden="true">Also include:</span>
                {options.map(option => (
                    <Tooltip key={option.key} title={option.hint} describeChild arrow enterDelay={200}>
                        <label className="flex items-center gap-2 whitespace-nowrap">
                            <input type="checkbox" checked={option.checked} aria-describedby={`${id}-${option.key}`}
                                onChange={event => onChange(
                                    option.key === 'obsolete' ? weakPredicates : WEAK_PREDICATE_ORDER.filter(code =>
                                        code === option.key ? event.target.checked : weakPredicates.includes(code)),
                                    option.key === 'obsolete' ? event.target.checked : includeObsolete,
                                )} />
                            <span>{option.label}</span>
                            {counts && <span className="text-sm text-tertiary">
                                {option.checked ? '(included)' : counts.additional[option.key] !== undefined
                                    ? `(+${counts.additional[option.key]!.toLocaleString()})`
                                    : counts.loading ? '(…)' : '(unavailable)'}
                            </span>}
                            <span id={`${id}-${option.key}`} className="sr-only">{option.hint}</span>
                        </label>
                    </Tooltip>
                ))}
            </div>
            {(pendingSearch || counts) && <p id={`${id}-counts`} className="mt-2 text-sm text-tertiary" role="status">
                {pendingSearch ? 'Search to apply your edited criteria and options.'
                    : counts?.unavailable ? 'Some counts are unavailable. You can still select options to search.'
                        : 'Counts show additional results for your current search. Selecting an option updates results immediately.'}
            </p>}
        </fieldset>
    );
}
