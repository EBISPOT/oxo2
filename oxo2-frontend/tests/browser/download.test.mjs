import assert from 'node:assert/strict';
import {after, before, test} from 'node:test';
import process from 'node:process';
import {URL} from 'node:url';
import {chromium} from 'playwright';

let browser;
before(async () => {
    browser = await chromium.launch({executablePath: process.env.BROWSER_EXECUTABLE || undefined});
});
after(async () => { await browser?.close(); });

const tsv = '# curie_map: {}\nsubject_id\tpredicate_id\tobject_id\nhttps://example.org/a\thttps://example.org/p\thttps://example.org/b\n';
const base = process.env.UI_BASE_URL || 'http://localhost:20100';

for (const failure of ['http', 'network']) {
    test(`download ${failure} failure is visible and retry saves the file`, async () => {
        const page = await browser.newPage();
        let attempts = 0;
        let release;
        const pending = new Promise(resolve => { release = resolve; });
        const exports = [];
        await page.route('**/api/**', async route => {
            const url = new URL(route.request().url());
            if (url.searchParams.get('format') === 'sssom-tsv') {
                exports.push(route.request().postDataJSON());
                if (++attempts === 1) {
                    await pending;
                    return failure === 'http'
                        ? route.fulfill({status: 503, body: 'Internal details must not be displayed'})
                        : route.abort('failed');
                }
                return route.fulfill({contentType: 'text/tab-separated-values', body: tsv});
            }
            return route.fulfill({json: url.pathname.endsWith('/mappings/search')
                ? {mappings: {content: [], totalElements: 0, totalPages: 0, number: 0, size: 10}}
                : []});
        });
        try {
            await page.goto(`${base}/search/diabetes?from=DOID&to=EFO`);
            const button = page.getByRole('button', {name: /Download/});
            await button.click();
            await page.getByRole('button', {name: 'Preparing download…', exact: true}).waitFor({timeout: 3000});
            assert.equal(await page.getByRole('button', {name: 'Preparing download…', exact: true}).isDisabled(), true);
            release();
            const alert = page.getByRole('alert');
            await alert.waitFor({timeout: 5000});
            assert.match(await alert.innerText(), /Download failed/);
            assert.match(await alert.innerText(), /try again/i);
            assert.doesNotMatch(await alert.innerText(), /Internal details/);
            assert.equal(await button.isEnabled(), true);

            const downloadPromise = page.waitForEvent('download');
            await button.click();
            const download = await downloadPromise;
            assert.equal(download.suggestedFilename(), 'oxo2-mappings.tsv');
            assert.equal(await download.failure(), null);
            let saved = '';
            for await (const chunk of await download.createReadStream()) saved += chunk.toString();
            assert.equal(saved, tsv);
            await alert.waitFor({state: 'hidden'});
            assert.equal(attempts, 2);
            assert.deepEqual(exports[1].queries, ['diabetes']);
            assert.deepEqual(exports[1].subjectPrefixes, ['DOID']);
            assert.deepEqual(exports[1].objectPrefixes, ['EFO']);
            assert.deepEqual(exports[1], exports[0]);
        } finally {
            release();
            await page.close();
        }
    });
}
