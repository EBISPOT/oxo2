import { useQuery } from '@tanstack/react-query';
import olsLogo from '/public/logo.svg';
import { fetchOlsTermUrl } from '../../util/olsUrl';

/** Optional enhancement: unknown terms, pending lookups and OLS failures all leave the icon hidden. */
export function OlsLink({ id, iri, large = false }: { id?: string; iri?: string; large?: boolean }) {
    const { data: href } = useQuery({
        queryKey: ['olsTerm', id, iri],
        queryFn: ({signal}) => fetchOlsTermUrl(id, iri, signal),
        enabled: !!(id || iri),
        staleTime: 24 * 60 * 60_000,
        gcTime: 30 * 60_000,
        retry: false,
        refetchOnWindowFocus: false,
    });
    if (!href) return null;
    return (
        <a
            href={href}
            title={`View ${id || iri} in OLS`}
            target="_blank"
            rel="noopener noreferrer"
            onClick={event => event.stopPropagation()}
            className="inline-flex shrink-0 ml-1 align-middle"
        >
            <img src={olsLogo} alt="OLS" className={large ? 'h-6 w-6' : 'h-4 w-4'} />
        </a>
    );
}
