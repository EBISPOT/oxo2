import assert from 'node:assert/strict';
import { test } from 'node:test';
import { URL } from 'node:url';
import { fetchOlsTermUrl } from '../src/util/olsUrl.ts';

const iri = 'http://purl.obolibrary.org/obo/MONDO_0000001';
const imported = {iri, obo_id: 'MONDO:0000001', ontology_name: 'efo'};

test('exact IRI lookup prefers the defining ontology and double-encodes the link', async t => {
    t.mock.method(globalThis, 'fetch', async url => {
        assert.equal(new URL(url).searchParams.get('iri'), iri);
        return {ok: true, json: async () => ({_embedded: {terms: [imported,
            {...imported, ontology_name: 'mondo', is_defining_ontology: true}]}})};
    });
    assert.equal(await fetchOlsTermUrl('MONDO:0000001', iri),
        `https://www.ebi.ac.uk/ols4/ontologies/mondo/classes/${encodeURIComponent(encodeURIComponent(iri))}`);
});

test('an imported term links to the ontology where OLS actually contains it', async t => {
    t.mock.method(globalThis, 'fetch', async () => ({ok: true, json: async () => ({_embedded: {terms: [imported]}})}));
    assert.match(await fetchOlsTermUrl('MONDO:0000001', iri), /\/ontologies\/efo\/classes\//);
});

test('CURIE-only entities use an exact obo_id lookup', async t => {
    t.mock.method(globalThis, 'fetch', async url => {
        assert.equal(new URL(url).searchParams.get('obo_id'), 'MONDO:0000001');
        return {ok: true, json: async () => ({_embedded: {terms: [imported]}})};
    });
    assert.ok(await fetchOlsTermUrl('MONDO:0000001'));
});

test('absent entities and unrelated responses never produce an OLS link', async t => {
    for (const data of [{}, {_embedded: {terms: []}}, {_embedded: {terms: [imported]}}]) {
        t.mock.method(globalThis, 'fetch', async () => ({ok: true, json: async () => data}));
        assert.equal(await fetchOlsTermUrl('OMIMPS:116200', 'https://omim.org/phenotypicSeries/PS116200'), null);
    }
});

test('404 means absent; service failures remain errors so the cache can recover', async t => {
    t.mock.method(globalThis, 'fetch', async () => ({ok: false, status: 404}));
    assert.equal(await fetchOlsTermUrl('MONDO:0000001', iri), null);
    t.mock.method(globalThis, 'fetch', async () => ({ok: false, status: 503}));
    await assert.rejects(fetchOlsTermUrl('MONDO:0000001', iri), /503/);
});

test('empty and literal references do not query OLS', async t => {
    const fetch = t.mock.method(globalThis, 'fetch', () => assert.fail('Unexpected request'));
    assert.equal(await fetchOlsTermUrl(), null);
    assert.equal(await fetchOlsTermUrl('plain label'), null);
    assert.equal(fetch.mock.callCount(), 0);
});
