import assert from 'node:assert/strict';
import {after, before, test} from 'node:test';
import process from 'node:process';
import {URL} from 'node:url';
import {chromium} from 'playwright';

let browser;
before(async () => { browser = await chromium.launch({executablePath: process.env.BROWSER_EXECUTABLE || undefined}); });
after(async () => { await browser?.close(); });
const base = process.env.UI_BASE_URL || 'http://localhost:20100';
const records = Array.from({length: 26}, (_, i) => ({
    mapping_id: `support-${i}`, subject_id: 'DOID:1', subject_label: 'Example disease',
    predicate_id: 'skos:exactMatch', object_id: 'MONDO:1', object_label: 'Example disease',
    mapping_justification: i % 2 ? 'semapv:LexicalMatching' : 'semapv:ManualMappingCuration',
    mapping_set_id: `https://example.org/set-${i % 3}`, mapping_set_title: `Source ${i % 3}`,
    inference_type: 'ASSERTED', confidence: i === 0 ? 0 : undefined,
}));

async function open({mobile = false, fail = false, startPath = '/mapping/support-0#supporting-mappings'} = {}) {
    const page = await browser.newPage({viewport: mobile ? {width: 390, height: 844} : {width: 1400, height: 1000}});
    const requestedPages = [];
    let failing = fail;
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url());
        if (url.pathname.endsWith('/mappings/search')) {
            const request = route.request().postDataJSON();
            return route.fulfill({json: {mappings: {
                content: [{...records[0], group_members: JSON.stringify({total: records.length, members: records.slice(0, 2)})}],
                totalElements: 40, totalPages: 4, number: request.page, size: request.size,
            }}});
        }
        if (url.pathname.endsWith('/support')) {
            if (failing) return route.fulfill({status: 503, json: {}});
            const number = Number(url.searchParams.get('page'));
            requestedPages.push(number);
            return route.fulfill({json: {
                mappings: {content: records.slice(number * 20, number * 20 + 20), totalElements: 26, totalPages: 2, number, size: 20},
                justificationMethodCount: 2, assertedSourceCount: 3,
            }});
        }
        if (url.pathname.includes('/api/sssom/mappings/')) {
            return route.fulfill({json: records.find(r => url.pathname.endsWith(`/${r.mapping_id}`))});
        }
        return route.fulfill({json: []});
    });
    await page.goto(`${base}${startPath}`);
    return {page, requestedPages, recover: () => { failing = false; }};
}

test('all evidence is reachable beyond the inline cap, with dataset-wide counts and reset on record navigation', async () => {
    const {page, requestedPages} = await open();
    try {
        const support = page.getByRole('region', {name: 'Supporting mappings', exact: true});
        await support.getByText('Page 1 of 2', {exact: false}).waitFor();
        assert.equal(await support.locator('tbody tr').count(), 20);
        assert.match(await support.innerText(), /hidden by your search filters/);
        assert.equal(await support.locator('dl div').filter({hasText: 'Asserted source sets'}).locator('dd').innerText(), '3');
        assert.equal(await support.locator('tbody tr').first().locator('td').nth(3).innerText(), '0');
        await support.getByRole('button', {name: 'Next', exact: true}).click();
        await support.getByText('Page 2 of 2', {exact: false}).waitFor();
        assert.equal(await support.locator('tbody tr').count(), 6);
        assert.equal(await support.getByRole('button', {name: 'Next', exact: true}).isDisabled(), true);
        await support.getByRole('link', {name: 'View record', exact: true}).last().click();
        await page.waitForURL('**/mapping/support-25');
        await support.getByText('Page 1 of 2', {exact: false}).waitFor();
        assert.deepEqual(requestedPages.slice(0, 2), [0, 1]);
    } finally { await page.close(); }
});

test('support failures can be retried and the table fits a narrow screen', async () => {
    const {page, recover} = await open({mobile: true, fail: true});
    try {
        const support = page.getByRole('region', {name: 'Supporting mappings', exact: true});
        await support.getByRole('button', {name: 'Retry'}).waitFor({timeout: 15000});
        recover();
        await support.getByRole('button', {name: 'Retry'}).click();
        await support.getByText('Page 1 of 2', {exact: false}).waitFor();
        // eslint-disable-next-line no-undef -- evaluated inside the browser
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    } finally { await page.close(); }
});

test('cached supporting-record navigation replaces the reporting notice instead of accumulating notices', async () => {
    const {page} = await open();
    try {
        const support = page.getByRole('region', {name: 'Supporting mappings', exact: true});
        const notice = page.getByText('No reporting contact available for this mapping source.', {exact: true});
        await notice.waitFor();
        // Revisit cached records: an uncached lookup temporarily unmounts the whole details page,
        // which can hide reconciliation errors between its reporting and supporting sections.
        for (const mappingId of ['support-1', 'support-0', 'support-1', 'support-0']) {
            await support.locator(`a[href$="/mapping/${mappingId}"]`).click();
            await page.waitForURL(`**/mapping/${mappingId}`);
            // URL changes before React replaces the old table. Wait until the chosen record's
            // link becomes Current record before checking the new page's reporting notice.
            await support.locator(`a[href$="/mapping/${mappingId}"]`).waitFor({state: 'detached'});
            await support.locator('tbody tr').filter({hasText: 'Current record'}).waitFor();
            await notice.first().waitFor();
            assert.equal(await notice.count(), 1, `Only ${mappingId}'s reporting notice should remain`);
            assert.equal(await page.getByRole('heading', {name: 'Mapping Details', exact: true}).count(), 1);
            assert.equal(await support.count(), 1);
        }
    } finally { await page.close(); }
});


for (const entry of ['row details', 'support badge', 'expanded member', 'support overflow']) {
    test(`Back to results preserves the original URL via ${entry} and supporting records`, async () => {
        const resultsPath = '/search/DOID%3A1?page=3&size=10&sort=subject_id&filter=object_label%3DExample';
        const {page} = await open({startPath: resultsPath});
        try {
            if (entry === 'expanded member' || entry === 'support overflow') {
                await page.getByRole('button', {name: 'Expand', exact: true}).click();
            }
            if (entry === 'support badge') {
                await page.getByRole('link', {name: /matching records · View all support/}).click();
            } else if (entry === 'support overflow') {
                await page.getByRole('link', {name: 'View all supporting mappings', exact: true}).click();
            } else {
                const details = page.getByRole('link', {name: 'View mapping details', exact: true});
                await (entry === 'expanded member' ? details.last() : details.first()).click();
            }
            const support = page.getByRole('region', {name: 'Supporting mappings', exact: true});
            for (const mappingId of ['support-2', 'support-3']) {
                await support.locator(`a[href$="/mapping/${mappingId}"]`).click();
                await page.waitForURL(`**/mapping/${mappingId}`);
            }
            // The return destination is part of the navigation entry, so a reload preserves it.
            await page.reload();
            await page.getByRole('button', {name: 'Back to results', exact: true}).click();
            await page.waitForURL(url => !url.pathname.endsWith('/mapping/support-3'));
            assert.equal(page.url(), `${base}${resultsPath}`);
        } finally { await page.close(); }
    });
}

test('direct mapping links keep New search after supporting-record navigation', async () => {
    const {page} = await open();
    try {
        const support = page.getByRole('region', {name: 'Supporting mappings', exact: true});
        await support.locator('a[href$="/mapping/support-1"]').click();
        await page.waitForURL('**/mapping/support-1');
        await page.reload();
        await page.getByRole('button', {name: 'New search', exact: true}).click();
        const homePath = new URL(base).pathname.replace(/\/$/, '');
        await page.waitForURL(url => url.pathname.replace(/\/$/, '') === homePath);
        assert.equal(await page.getByRole('button', {name: 'Back to results', exact: true}).count(), 0);
    } finally { await page.close(); }
});
