import {
    AdvancedFieldDef,
    AdvancedFieldGroup,
    GROUP_LABELS,
    GROUP_ORDER,
    groupFields,
} from "../../model/AdvancedFields";
import { SuggestField } from "./SuggestField";

interface AdvancedSearchProps {
    values: Record<string, string>;
    onChange: (field: string, value: string) => void;
    onSubmit: () => void;
    onClear: () => void;
}

export function AdvancedSearch({ values, onChange, onSubmit, onClear }: AdvancedSearchProps) {
    const grouped = groupFields();

    const placeholderFor = (fd: AdvancedFieldDef) => {
        // A field that completes tells you so, rather than describing its match semantics — the
        // dropdown is the more useful thing to advertise.
        if (fd.suggest === "entity") {
            return "Start typing a label, CURIE or IRI…";
        }
        if (fd.suggest === "vocab") {
            return "Start typing, or pick from the list…";
        }
        if (fd.type === "text_general") {
            return fd.multiValued ? "Text search (matches any list item)" : "Text search";
        }
        return fd.multiValued ? "Exact match (any list item)" : "Exact match, case-sensitive";
    };

    return (
        <div className="w-full">
            <div className="text-tertiary mb-2">
                Fill any combination of fields. All filled fields are combined with AND.
            </div>
            <div className="flex flex-col gap-2">
                {GROUP_ORDER.map((group: AdvancedFieldGroup) => (
                    <details
                        key={group}
                        className="border border-gray-200 rounded p-2"
                        open={group === "subject"}
                    >
                        <summary className="cursor-pointer font-semibold py-1">
                            {GROUP_LABELS[group]}
                        </summary>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-2">
                            {grouped[group].map((fd) => (
                                <div key={fd.field} className="flex flex-col">
                                    <label
                                        htmlFor={`adv-${fd.field}`}
                                        className="text-sm text-tertiary"
                                        title={fd.field}
                                    >
                                        {fd.label}
                                    </label>
                                    <SuggestField
                                        fieldDef={fd}
                                        value={values[fd.field] ?? ""}
                                        onChange={onChange}
                                        placeholder={placeholderFor(fd)}
                                    />
                                </div>
                            ))}
                        </div>
                    </details>
                ))}
            </div>
            <div className="flex gap-2 mt-3">
                <button
                    className="button-primary text-base font-bold px-4 py-1"
                    onClick={onSubmit}
                >
                    Search
                </button>
                <button
                    className="button-primary text-base font-bold px-4 py-1"
                    onClick={onClear}
                >
                    Clear
                </button>
            </div>
        </div>
    );
}
