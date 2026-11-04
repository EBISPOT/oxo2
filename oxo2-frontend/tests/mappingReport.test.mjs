import assert from 'node:assert/strict';
import { test } from 'node:test';
import { URL } from 'node:url';
import { createMappingReport, reportingDestinations } from '../src/util/mappingReport.ts';

const mapping = {
    subjectId: 'MONDO:0000001', subjectIri: '', subjectLabel: 'Disease & disorder',
    predicateId: 'skos:exactMatch', predicateIri: '', predicateModifier: 'Not',
    objectId: 'EFO:0000001', objectIri: '', objectLabel: 'Example disease',
    mappingSetId: 'https://example.org/source?version=1&format=tsv',
    mappingSetTitle: 'Example mapping set',
};
const mappingUrl = 'https://example.org/oxo2/mapping/example-id';
const report = createMappingReport(mapping, mappingUrl);

test('report preserves negation, source identity and a shareable mapping link', () => {
    assert.match(report.body, /Predicate: Not skos:exactMatch/);
    const lines = report.body.split('\n');
    assert.equal(lines.find(line => line.startsWith('Source mapping set: ')), `Source mapping set: ${mapping.mappingSetId}`);
    assert.equal(lines.find(line => line.startsWith('OxO mapping: ')), `OxO mapping: ${mappingUrl}`);
    assert.match(report.body, /Subject: MONDO:0000001 \(Disease & disorder\)/);
    assert.match(report.title, /Not skos:exactMatch/);
});

test('literal subjects retain their exact text instead of becoming an empty identifier', () => {
    const literal = createMappingReport({ ...mapping, subjectId: '', subjectLabel: '  α "disease"\nβ  ' }, mappingUrl);
    assert.ok(literal.body.includes('Subject: "  α \\"disease\\"\\nβ  "'));
});

test('GitHub prefills correctly encoded title and body and is preferred over email', () => {
    const destinations = reportingDestinations({
        issueTracker: 'https://github.com/example/ontology/issues', mailingList: 'users@example.org',
    }, report);
    assert.equal(destinations.length, 2);
    const github = new URL(destinations[0].href);
    assert.equal(github.pathname, '/example/ontology/issues/new');
    assert.equal(github.searchParams.get('title'), report.title);
    assert.equal(github.searchParams.get('body'), report.body);
    assert.equal(destinations[0].prefilled, true);
    const mail = new URL(destinations[1].href);
    assert.equal(mail.protocol, 'mailto:');
    assert.equal(mail.pathname, 'users@example.org');
    assert.equal(mail.searchParams.get('subject'), report.title);
    assert.equal(mail.searchParams.get('body'), report.body.replace(/\n/g, '\r\n'));
});

test('repository roots and existing new-issue URLs produce new GitHub reports', () => {
    for (const path of ['/example/ontology', '/example/ontology/', '/example/ontology/issues/', '/example/ontology/issues/new']) {
        const [destination] = reportingDestinations({ issueTracker: `https://github.com${path}` }, report);
        assert.equal(new URL(destination.href).pathname, '/example/ontology/issues/new');
        assert.equal(destination.prefilled, true);
    }
});

test('generic trackers, custom forms and mailing-list websites remain unchanged', () => {
    for (const issueTracker of [
        'https://gitlab.example.org/project/-/issues',
        'https://github.com/example/ontology/issues/new?template=bug.yml',
        'https://github.com.example.org/example/ontology/issues',
    ]) {
        const [destination] = reportingDestinations({ issueTracker }, report);
        assert.equal(destination.href, issueTracker);
        assert.equal(destination.prefilled, false);
    }
    const [list] = reportingDestinations({ mailingList: 'https://groups.google.com/g/example' }, report);
    assert.equal(list.href, 'https://groups.google.com/g/example');
    assert.equal(list.prefilled, false);
});

test('mailing-list-only sets support bare and mailto email addresses, including plus addressing', () => {
    for (const address of ['users+ontology@example.org', 'mailto:users+ontology@example.org']) {
        const [destination] = reportingDestinations({ mailingList: address }, report);
        const url = new URL(destination.href);
        assert.equal(decodeURIComponent(url.pathname), 'users+ontology@example.org');
        assert.equal(url.searchParams.get('subject'), report.title);
        assert.equal(destination.prefilled, true);
    }
});

test('missing or unsafe destinations never become actionable links', () => {
    assert.deepEqual(reportingDestinations({}, report), []);
    for (const value of ['', '  ', 'javascript:alert(1)', 'data:text/html,hello', '//example.org',
        'https://user:password@example.org', 'mailto:users@example.org?bcc=other@example.org',
        'users@example.org\r\nBcc: other@example.org', 'users@extra@example.org']) {
        assert.deepEqual(reportingDestinations({ issueTracker: value, mailingList: value }, report), [], value);
    }
});

test('email recipient punctuation stays encoded without introducing headers or extra recipients', () => {
    const address = 'users+tag%40%40?bcc=other&name#section@example.org';
    const [destination] = reportingDestinations({ mailingList: address }, report);
    const url = new URL(destination.href);
    assert.equal(decodeURIComponent(url.pathname), address);
    assert.equal(url.hash, '');
    assert.deepEqual([...url.searchParams.keys()], ['subject', 'body']);
    assert.equal(url.searchParams.get('subject'), report.title);
    assert.equal(url.searchParams.get('body'), report.body.replace(/\n/g, '\r\n'));
});

test('oversized reports fall back to destination links without truncating report text', () => {
    const longReport = createMappingReport({ ...mapping, subjectLabel: 'α'.repeat(5000) }, mappingUrl);
    const destinations = reportingDestinations({
        issueTracker: 'https://github.com/example/ontology/issues', mailingList: 'users@example.org',
    }, longReport);
    assert.ok(longReport.body.includes('α'.repeat(5000)));
    assert.equal(destinations[0].href, 'https://github.com/example/ontology/issues');
    assert.equal(destinations[1].href, 'mailto:users@example.org');
    assert.ok(destinations.every(destination => !destination.prefilled));
});
