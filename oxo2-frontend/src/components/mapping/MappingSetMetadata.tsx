import type { MappingSetMetadataEntry } from '../../model/MappingSet';

function MetadataValue({ value }: { value: string }) {
    // ORCID identifiers are commonly supplied as CURIEs, including in Biomappings.
    const candidate = /^orcid:\d{4}-\d{4}-\d{4}-\d{3}[\dX]$/i.test(value)
        ? `https://orcid.org/${value.slice(6)}` : value;
    let href: string | undefined;
    try {
        const url = new URL(candidate);
        if (['https:', 'http:'].includes(url.protocol) && !url.username && !url.password) {
            href = url.href;
        }
    } catch { /* Plain labels and unresolved identifiers remain readable text. */ }
    return href
        ? <a href={href} target="_blank" rel="noopener noreferrer" className="text-link-default hover:underline">{value}</a>
        : <span>{value}</span>;
}

export function MappingSetMetadata({ entries = [] }: { entries?: MappingSetMetadataEntry[] }) {
    if (entries.length === 0) return null;
    return (
        <section aria-label="Mapping set metadata" className="mt-5 rounded border border-gray-200 bg-gray-50 p-4">
            <dl className="grid grid-cols-1 gap-x-8 gap-y-4 lg:grid-cols-2">
                {entries.map(({field, label, values}) => (
                    <div key={field} className="min-w-0 grid grid-cols-1 gap-1 sm:grid-cols-[11rem_minmax(0,1fr)] sm:gap-3">
                        <dt className="text-sm font-semibold text-gray-600">{label}</dt>
                        <dd className="min-w-0 text-sm whitespace-pre-wrap [overflow-wrap:anywhere]">
                            {values.length > 5 ? (
                                <details>
                                    <summary className="cursor-pointer text-link-default">Show all {values.length} values</summary>
                                    <ul className="mt-2 space-y-1">
                                        {values.map((value, index) => <li key={index}><MetadataValue value={value} /></li>)}
                                    </ul>
                                </details>
                            ) : values.map((value, index) => <div key={index}><MetadataValue value={value} /></div>)}
                        </dd>
                    </div>
                ))}
            </dl>
        </section>
    );
}
