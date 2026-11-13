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

async function open({mobile = false, fail = false} = {}) {
    const page = await browser.newPage({viewport: mobile ? {width: 390, height: 844} : {width: 1400, height: 1000}});
    const requestedPages = [];
    let failing = fail;
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url());
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
    await page.goto(`${base}/mapping/support-0#supporting-mappings`);
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
