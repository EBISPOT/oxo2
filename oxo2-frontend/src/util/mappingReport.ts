import type { Mapping } from '../model/Mapping';
import type { MappingSet } from '../model/MappingSet';

export interface MappingReport {
    title: string;
    body: string;
}

export interface ReportingDestination {
    label: string;
    href: string;
    prefilled: boolean;
}

function entityDescription(id: string, iri: string, label: string): string {
    const identifier = id || iri;
    return identifier ? (label ? `${identifier} (${label})` : identifier) : JSON.stringify(label);
}

export function createMappingReport(mapping: Mapping, mappingUrl: string): MappingReport {
    const subject = entityDescription(mapping.subjectId, mapping.subjectIri, mapping.subjectLabel);
    const object = entityDescription(mapping.objectId, mapping.objectIri, mapping.objectLabel);
    const predicate = [mapping.predicateModifier, mapping.predicateId || mapping.predicateIri]
        .filter(Boolean).join(' ');
    return {
        title: `Incorrect mapping: ${subject} ${predicate} ${object}`.replace(/\s+/g, ' ').slice(0, 200),
        body: [
            'I would like to report a potentially incorrect asserted mapping:',
            '',
            `Subject: ${subject}`,
            `Predicate: ${predicate}`,
            `Object: ${object}`,
            `Source mapping set: ${mapping.mappingSetId}`,
            ...(mapping.mappingSetTitle ? [`Mapping set title: ${mapping.mappingSetTitle}`] : []),
            `OxO mapping: ${mappingUrl}`,
            '',
            'Why I believe this mapping is incorrect:',
            '[Please describe the problem and any supporting evidence.]',
        ].join('\n'),
    };
}

function webDestination(value?: string): URL | undefined {
    if (!value?.trim() || /[\r\n\t]/.test(value)) return undefined;
    try {
        const url = new URL(value.trim());
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return undefined;
        return url;
    } catch {
        return undefined;
    }
}

// Keep links small enough for browsers and mail clients. The full report remains copyable in the UI.
const MAX_PREFILLED_URL_LENGTH = 2000;

function trackerDestination(value: string | undefined, report: MappingReport): ReportingDestination | undefined {
    const url = webDestination(value);
    if (!url) return undefined;
    const originalHref = url.href;
    const githubRepository = url.pathname.match(/^\/([^/]+)\/([^/]+?)(?:\/issues(?:\/new)?)?\/?$/);
    // Custom issue forms have their own field IDs; preserve those links without inventing a schema.
    if (url.hostname === 'github.com' && !url.port && githubRepository && !url.searchParams.has('template')) {
        url.pathname = `/${githubRepository[1]}/${githubRepository[2]}/issues/new`;
        url.search = new URLSearchParams({ title: report.title, body: report.body }).toString();
        url.hash = '';
        if (url.href.length <= MAX_PREFILLED_URL_LENGTH) {
            return { label: 'Open issue tracker', href: url.href, prefilled: true };
        }
    }
    return { label: 'Open issue tracker', href: originalHref, prefilled: false };
}

function mailingListDestination(value: string | undefined, report: MappingReport): ReportingDestination | undefined {
    if (!value?.trim()) return undefined;
    const url = webDestination(value);
    if (url) return { label: 'Open mailing list', href: url.href, prefilled: false };

    const address = value.trim().replace(/^mailto:/i, '');
    // A single address only: metadata must not supply recipients or arbitrary mail headers.
    if (!/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$/i.test(address)) {
        return undefined;
    }
    const [localPart, domain] = address.split('@');
    const mailto = `mailto:${encodeURIComponent(localPart)}@${encodeURIComponent(domain)}`;
    const href = `${mailto}?subject=${encodeURIComponent(report.title)}&body=${encodeURIComponent(report.body.replace(/\n/g, '\r\n'))}`;
    return href.length <= MAX_PREFILLED_URL_LENGTH
        ? { label: 'Open email draft', href, prefilled: true }
        : { label: 'Open email draft', href: mailto, prefilled: false };
}

export function reportingDestinations(
    mappingSet: Pick<MappingSet, 'issueTracker' | 'mailingList'>,
    report: MappingReport,
): ReportingDestination[] {
    return [trackerDestination(mappingSet.issueTracker, report), mailingListDestination(mappingSet.mailingList, report)]
        .filter((destination): destination is ReportingDestination => destination !== undefined);
}
