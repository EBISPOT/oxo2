import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import process from 'node:process';
import { URL } from 'node:url';
import { chromium } from 'playwright';

let browser;
before(async () => {
    browser = await chromium.launch({
        executablePath: process.env.BROWSER_EXECUTABLE || undefined,
    });
});
after(async () => { await browser?.close(); });

async function openHome() {
    const page = await browser.newPage();
    await page.route('**/api/**', route => route.fulfill({
        json: route.request().url().includes('data-content')
            ? { releaseDate: null, mappings: {total: 0, asserted: 0, inferred: 0},
                mappingSets: {total: 0, curated: 0, ontologies: 0} }
            : route.request().url().includes('/ontologies?')
                ? [{prefix: 'MONDO', count: 3}]
                : route.request().url().includes('/ontologies')
                    ? [{prefix: 'DOID', asSubject: 10, asObject: 4}, {prefix: 'MONDO', asSubject: 4, asObject: 3}, {prefix: 'UNKNOWN', asSubject: 1, asObject: 1}]
                    : route.request().url().includes('/mapping-sets')
                        ? [{mapping_set_id: 'doid', mapping_set_category: 'ONTOLOGY', prefix: 'DOID', ontology: 'Human Disease Ontology', mapping_set_description: 'A classification of human diseases.'},
                            {mapping_set_id: 'mondo', mapping_set_category: 'ONTOLOGY', prefix: 'MONDO', ontology: 'Mondo Disease Ontology'}]
                        : [],
    }));
    await page.goto(process.env.UI_BASE_URL || 'http://localhost:20100');
    return page;
}

for (const mode of ['Single term', 'Multiple terms']) {
    test(`${mode}: double-click selects text that can be replaced`, async () => {
        const page = await openHome();
        try {
            await page.getByRole('radio', { name: mode, exact: true }).click();
            const field = page.locator('#home-search');
            await field.fill('cataract');
            await field.dblclick({ position: { x: 35, y: 15 } });
            assert.equal(await field.evaluate(el => el.value.slice(el.selectionStart, el.selectionEnd)), 'cataract');
            await page.keyboard.type('melanoma');
            assert.equal(await field.inputValue(), 'melanoma');
        } finally { await page.close(); }
    });
}


test('ontology names are searchable and retained for reachable targets', async () => {
    const page = await openHome();
    try {
        const source = page.getByRole('combobox', {name: 'From ontologies'});
        await source.fill('Human Disease');
        const option = page.getByRole('option', {name: /Human Disease Ontology/});
        await option.waitFor();
        assert.match(await option.innerText(), /A classification of human diseases/);
        await option.click();
        const target = page.getByRole('combobox', {name: 'To ontologies'});
        await target.fill('Mondo Disease');
        await page.getByRole('option', {name: /Mondo Disease Ontology/}).click();
        await page.getByRole('button', {name: 'Search', exact: true}).click();
        await page.waitForURL('**/search/**');
        const url = new URL(page.url());
        assert.equal(url.searchParams.get('from'), 'DOID');
        assert.equal(url.searchParams.get('to'), 'MONDO');
    } finally { await page.close(); }
});


test('ontology prefixes remain usable without descriptive metadata', async () => {
    const page = await openHome();
    try {
        await page.getByRole('combobox', {name: 'From ontologies'}).fill('UNKNOWN');
        await page.getByRole('option', {name: /UNKNOWN/}).click();
        assert.ok(await page.getByText('UNKNOWN', {exact: true}).count());
    } finally { await page.close(); }
});
