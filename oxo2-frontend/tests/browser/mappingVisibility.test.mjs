import assert from 'node:assert/strict';
import {after, before, test} from 'node:test';
import process from 'node:process';
import {mkdir} from 'node:fs/promises';
import {URL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

let browser;
before(async () => {
    browser = await chromium.launch({executablePath: process.env.BROWSER_EXECUTABLE || undefined});
});
after(async () => { await browser?.close(); });
const base = process.env.UI_BASE_URL || 'http://localhost:20100';
const rows = [
    {key: 'sub1', weak: 'subClassOf'},
    {key: 'sub1', weak: 'subClassOf'}, // Two source records, one displayed SPO group.
    {key: 'sub2', weak: 'subClassOf'},
    {key: 'xref', weak: 'hasDbXref'},
    {key: 'old', obsolete: true},
    {key: 'old-xref', weak: 'hasDbXref', obsolete: true},
];

async function openSearch({dataset = rows, query = '', failCounts = false, slowCounts = false, path = '/search/Disease'} = {}) {
    const page = await browser.newPage({viewport: {width: 1440, height: 1100}});
    const requests = [];
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        if (url.pathname.endsWith('/mappings/search')) {
            const request = route.request().postDataJSON();
            requests.push(request);
            const isCount = request.size === 1;
            if (isCount && slowCounts) await delay(600);
            if (isCount && failCounts) return route.fulfill({status: 503, json: {}});
            const visible = request.queries.includes('missing') ? [] : dataset.filter(row =>
                (!row.weak || request.includeWeakPredicates?.includes(row.weak))
                && (!row.obsolete || request.includeObsolete));
            const grouped = request.groupBySpo ? [...new Map(visible.map(row => [row.key, row])).values()] : visible;
            return route.fulfill({json: {mappings: {
                content: grouped.slice(request.page * request.size, (request.page + 1) * request.size).map(row => ({
                    mapping_id: row.key, subject_id: 'DOID:1', subject_label: 'Disease',
                    predicate_id: row.weak || 'skos:exactMatch', object_id: `MONDO:${row.key}`,
                    inference_type: 'ASSERTED', mapping_set_id: 'source',
                })),
                totalElements: grouped.length, totalPages: Math.ceil(grouped.length / request.size),
                number: request.page, size: request.size,
            }}});
        }
        if (url.pathname.includes('/ontologies')) return route.fulfill({json: [
            {prefix: 'DOID', asSubject: 10, asObject: 4}, {prefix: 'MONDO', asSubject: 4, asObject: 10},
        ]});
        if (url.pathname.includes('data-content')) return route.fulfill({json: {
            releaseDate: null, mappings: {total: 0, asserted: 0, inferred: 0},
            mappingSets: {total: 0, curated: 0, ontologies: 0},
        }});
        return route.fulfill({json: []});
    });
    await page.goto(`${base}${path}${query}`);
    return {page, requests};
}

function option(page, name) {
    return page.getByRole('group', {name: 'Also include'}).locator('label').filter({hasText: name});
}

async function expectText(locator, expected) {
    await locator.filter({hasText: expected}).waitFor();
}

test('landing page explains each inclusion option on hover and keyboard focus', async () => {
    const {page} = await openSearch({path: '/'});
    try {
        for (const [label, explanation] of [
            ['Subclass', 'Ontology hierarchy, not equivalence.'],
            ['Cross-reference', 'A loose link between two identifiers, with no stated meaning.'],
            ['Obsolete', "Terms an ontology has deprecated. Hidden by default; a live term's mappings to a dead one are hidden too."],
        ]) {
            await option(page, label).hover();
            await page.getByRole('tooltip', {name: explanation, exact: true}).waitFor({timeout: 2000});
            await page.mouse.move(0, 0);
            await page.getByRole('tooltip').waitFor({state: 'hidden'});
            // Establish keyboard modality, then focus the actual checkbox.
            await page.keyboard.press('Tab');
            await option(page, label).getByRole('checkbox').focus();
            await page.getByRole('tooltip', {name: explanation, exact: true}).waitFor({timeout: 2000});
            await page.keyboard.press('Escape');
            await page.getByRole('tooltip').waitFor({state: 'hidden'});
            await page.locator('#home-search').focus();
        }
    } finally { await page.close(); }
});

test('visible home options submit together and do not invent counts before searching', async () => {
    const {page} = await openSearch({path: '/'});
    try {
        await page.getByRole('group', {name: 'Also include'}).waitFor();
        assert.equal(await page.locator('details[open]').count(), 0);
        await option(page, 'Cross-reference').getByRole('checkbox').check();
        await option(page, 'Obsolete').getByRole('checkbox').check();
        assert.ok(!(await option(page, 'Cross-reference').innerText()).includes('(+'));
        await page.locator('#home-search').fill('Disease');
        await page.getByRole('button', {name: 'Search', exact: true}).click();
        await page.waitForURL('**/search/**');
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 3');
        assert.equal(new URL(page.url()).searchParams.get('obs'), '1');
    } finally { await page.close(); }
});

test('counts match grouped results, preserve scope and refresh immediately; Back restores options', async () => {
    const query = '?from=DOID&to=MONDO&mapping_set_id=source&corpus=ontology&match=PARTIAL'
        + '&type=asserted&filter=object_label%3Ddisease&fx=object_label&sort=-subject_label&page=2';
    const {page, requests} = await openSearch({query});
    try {
        await expectText(option(page, 'Subclass'), '(+2)');
        await expectText(option(page, 'Cross-reference'), '(+1)');
        await expectText(option(page, 'Obsolete'), '(+1)');
        await page.getByRole('button', {name: 'Include all three · show 5 results', exact: true}).waitFor();
        for (const request of requests) {
            assert.deepEqual(request.queries, ['Disease']);
            assert.deepEqual(request.subjectPrefixes, ['DOID']);
            assert.deepEqual(request.objectPrefixes, ['MONDO']);
            assert.deepEqual(request.mappingSetIds, ['source']);
            assert.deepEqual(request.mappingSetCategory, ['ONTOLOGY']);
            assert.equal(request.labelMatch, 'PARTIAL');
            assert.deepEqual(request.inferenceType, ['ASSERTED']);
            assert.deepEqual(request.columnFilters, [{id: 'object_label', value: 'disease', match: 'EXACT'}]);
            assert.equal(request.groupBySpo, true);
        }
        if (process.env.UI_SCREENSHOT_DIR) {
            await mkdir(process.env.UI_SCREENSHOT_DIR, {recursive: true});
            await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/hidden-mappings-desktop.png`, fullPage: true});
        }
        await option(page, 'Cross-reference').getByRole('checkbox').check();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 1');
        await expectText(option(page, 'Obsolete'), '(+2)');
        const params = new URL(page.url()).searchParams;
        assert.equal(params.has('page'), false);
        assert.equal(params.get('filter'), 'object_label=disease');
        assert.equal(params.get('sort'), '-subject_label');
        assert.equal(params.get('fx'), 'object_label');
        await option(page, 'Obsolete').getByRole('checkbox').check();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 3');
        await page.goBack();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 1');
        assert.equal(await option(page, 'Obsolete').getByRole('checkbox').isChecked(), false);
        await page.goBack();
        await page.getByRole('button', {name: 'Include all three · show 5 results', exact: true}).click();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 5');
        assert.equal(await page.getByRole('group', {name: 'Also include'}).getByRole('checkbox', {checked: true}).count(), 3);
        await page.reload();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 5');
        assert.equal(await option(page, 'Obsolete').getByRole('checkbox').isChecked(), true);
    } finally { await page.close(); }
});

test('zero-count options remain selectable when only a combination reveals mappings', async () => {
    const {page} = await openSearch({dataset: [{key: 'old-xref', weak: 'hasDbXref', obsolete: true}]});
    try {
        await page.getByText('Some mappings require more than one option.', {exact: false}).waitFor();
        for (const label of ['Subclass', 'Cross-reference', 'Obsolete']) {
            await expectText(option(page, label), '(+0)');
            assert.equal(await option(page, label).getByRole('checkbox').isEnabled(), true);
        }
        await page.setViewportSize({width: 390, height: 844});
        if (process.env.UI_SCREENSHOT_DIR) {
            await mkdir(process.env.UI_SCREENSHOT_DIR, {recursive: true});
            await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/hidden-mappings-mobile.png`, fullPage: true});
        }
        await option(page, 'Cross-reference').getByRole('checkbox').check();
        await expectText(option(page, 'Obsolete'), '(+1)');
        await page.getByRole('button', {name: 'Include all three · show 1 result', exact: true}).click();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 1');
    } finally { await page.close(); }
});

test('draft criteria hide stale counts and submit with changed options', async () => {
    const {page} = await openSearch();
    try {
        await expectText(option(page, 'Subclass'), '(+2)');
        await page.locator('#home-search').fill('missing');
        await page.getByText('Search to apply your edited criteria and options.', {exact: true}).waitFor();
        assert.ok(!(await option(page, 'Subclass').innerText()).includes('(+'));
        await option(page, 'Obsolete').getByRole('checkbox').check();
        assert.equal(new URL(page.url()).searchParams.has('obs'), false);
        await page.getByRole('button', {name: 'Search', exact: true}).click();
        await page.waitForURL('**/search/missing?obs=1');
        await page.getByText('No mappings found, including subclass relationships', {exact: false}).waitFor();
        await expectText(option(page, 'Subclass'), '(+0)');
        assert.equal(await option(page, 'Obsolete').getByRole('checkbox').isChecked(), true);
        await page.goBack();
        await expectText(option(page, 'Subclass'), '(+2)');
        assert.equal(await page.locator('#home-search').inputValue(), 'Disease');
        assert.equal(await option(page, 'Obsolete').getByRole('checkbox').isChecked(), false);
    } finally { await page.close(); }
});

test('failed counts stay unknown and do not block revealing results', async () => {
    const {page} = await openSearch({failCounts: true});
    try {
        await page.getByText('Some counts are unavailable.', {exact: false}).waitFor();
        await expectText(option(page, 'Cross-reference'), '(unavailable)');
        assert.equal(await page.getByText('No mappings found, including subclass relationships', {exact: false}).count(), 0);
        await option(page, 'Cross-reference').getByRole('checkbox').check();
        await expectText(page.getByText('Mappings found:'), 'Mappings found: 1');
    } finally { await page.close(); }
});

test('slow responses cannot replace counts for a newer submitted search', async () => {
    const {page} = await openSearch({slowCounts: true});
    try {
        await page.getByText('Checking for mappings hidden by your options', {exact: false}).waitFor();
        await page.locator('#home-search').fill('missing');
        await page.getByRole('button', {name: 'Search', exact: true}).click();
        await page.getByText('No mappings found, including subclass relationships', {exact: false}).waitFor();
        await expectText(option(page, 'Subclass'), '(+0)');
        assert.equal(await page.getByRole('button', {name: /Include all three/}).count(), 0);
    } finally { await page.close(); }
});
