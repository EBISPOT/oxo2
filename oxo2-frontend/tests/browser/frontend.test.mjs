import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import process from 'node:process';
import { URL } from 'node:url';
import { mkdir } from 'node:fs/promises';
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
                            {mapping_set_id: 'mondo', mapping_set_category: 'ONTOLOGY', prefix: 'MONDO', ontology: 'Mondo Disease Ontology'},
                            {mapping_set_id: 'doid.obsolete', mapping_set_category: 'ONTOLOGY', prefix: 'DOID', ontology: 'Human Disease Ontology', mapping_set_description: 'Obsolete terms only.', obsolete: true}]
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

const mappingFixture = {
    mapping_id: 'example', inference_type: 'ASSERTED', mapping_set_id: 'source',
    subject_id: 'MONDO:0000001', subject_iri: 'http://purl.obolibrary.org/obo/MONDO_0000001', subject_label: 'Disease',
    predicate_id: 'skos:exactMatch',
    mapping_justification: 'semapv:ManualMappingCuration',
    mapping_set_title: 'Example disease mappings',
    mapping_set_description: 'Mappings between disease terminology and phenotypic series.',
    creator_label: 'Example curators', mapping_date: '2026-11-04',
    subject_source: 'MONDO', object_source: 'OMIMPS',
    object_id: 'OMIMPS:116200', object_iri: 'https://omim.org/phenotypicSeries/PS116200', object_label: 'Example phenotypic series',
};

async function openMapping(mapping = mappingFixture, olsStatus = 200) {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    await page.route('**/api/**', route => route.fulfill({json:
        route.request().url().includes('/api/sssom/mappings/') ? mapping : {mapping_set_id: 'source', mapping_set_title: 'Example disease mappings', issue_tracker: 'https://github.com/example/ontology/issues'},
    }));
    await page.route('https://www.ebi.ac.uk/ols4/api/terms?**', route => route.fulfill({status: olsStatus, json: {
        _embedded: {terms: route.request().url().includes('MONDO') ? [{
            iri: mapping.subject_iri, obo_id: mapping.subject_id,
            ontology_name: 'mondo', is_defining_ontology: true,
        }] : []},
    }}));
    await page.goto(`${process.env.UI_BASE_URL || 'http://localhost:20100'}/mapping/example`);
    await page.getByRole('heading', {name: 'Mapping Details', exact: true}).waitFor();
    return page;
}

test('OLS links appear only for confirmed terms', async () => {
    const page = await openMapping();
    try {
        const known = page.getByRole('link', {name: 'OLS', exact: true});
        await known.first().waitFor();
        assert.equal(await page.locator('a[title="View OMIMPS:116200 in OLS"]').count(), 0);
        assert.equal(await known.count(), 1);
        assert.match(await known.getAttribute('href'), /\/ontologies\/mondo\/classes\//);
    } finally { await page.close(); }
});


test('OLS unavailability leaves the mapping readable without speculative links', async () => {
    const page = await openMapping(mappingFixture, 503);
    try {
        assert.equal(await page.getByRole('link', {name: 'OLS', exact: true}).count(), 0);
        assert.ok(await page.getByText('Example phenotypic series', {exact: true}).count());
    } finally { await page.close(); }
});

const explanation = {
    subject_id: 'MONDO:0000001', predicate_id: 'skos:exactMatch', object_id: 'OMIMPS:116200',
    chain_rule_applications: {
        chain_rule: {chain_rule_name: 'T', chain_rule_long_form: 'Transitivity', chain_rule_abbreviated: '(a, P, c) ← (a, P, b), (b, P, c)'},
        premises: [
            {subject_id: 'MONDO:0000001', predicate_id: 'skos:exactMatch', object_id: 'EFO:0001', mapping_set_id: 'first source', chain_rule_applications: {chain_rule: {chain_rule_name: 'Asserted', chain_rule_long_form: 'Asserted', chain_rule_abbreviated: ''}}},
            {subject_id: 'EFO:0001', predicate_id: 'skos:exactMatch', object_id: 'OMIMPS:116200', mapping_set_id: 'second source', chain_rule_applications: {chain_rule: {chain_rule_name: 'Asserted', chain_rule_long_form: 'Asserted', chain_rule_abbreviated: ''}}},
        ],
    },
};

test('mapping details explicitly summarize missing scores', async () => {
    const page = await openMapping();
    try {
        await page.getByText('Not provided: confidence, similarity score.', {exact: true}).waitFor();
        await page.getByRole('img', {name: 'OxO logo', exact: true}).evaluate(img => img.decode());
        if (process.env.UI_SCREENSHOT_DIR) {
            await mkdir(process.env.UI_SCREENSHOT_DIR, {recursive: true});
            await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/mapping-details.png`, fullPage: true, animations: 'disabled'});
        }
        await page.getByRole('button', {name: 'Report incorrect mapping', exact: true}).click();
        await page.getByLabel('Report text', {exact: true}).waitFor();
        await page.getByRole('button', {name: 'Report incorrect mapping', exact: true}).click();
        await page.getByRole('button', {name: 'Hide provenance', exact: true}).click();
        assert.equal(await page.getByRole('button', {name: 'Show provenance', exact: true}).getAttribute('aria-expanded'), 'false');
        await page.getByRole('button', {name: 'Show provenance', exact: true}).click();
        await page.setViewportSize({width: 390, height: 844});
        assert.equal(await page.locator('main').evaluate(el => el.scrollWidth <= el.clientWidth), true);
        if (process.env.UI_SCREENSHOT_DIR) {
            await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/mapping-mobile.png`, fullPage: true, animations: 'disabled'});
        }
    } finally { await page.close(); }
});

test('zero-valued confidence and similarity remain visible', async () => {
    const page = await openMapping({...mappingFixture, confidence: 0, similarity_score: 0, similarity_measure: 'Jaccard', predicate_modifier: 'Not'});
    try {
        const section = page.getByRole('region', {name: 'Justification'});
        assert.match(await section.innerText(), /Confidence.*0/s);
        assert.match(await section.innerText(), /Similarity score.*0/s);
        assert.match(await section.innerText(), /Jaccard/);
        assert.equal(await page.getByRole('region', {name: 'Mapping', exact: true}).getByText('Not', {exact: true}).count(), 1);
        assert.equal(await page.getByText(/Not provided:/).count(), 0);
    } finally { await page.close(); }
});

test('inference graph expands even without an asserted-mappings list, and Escape restores focus', async () => {
    const page = await openMapping({...mappingFixture, inference_type: 'SSSOM_INFERENCE', explanation: JSON.stringify(explanation)});
    try {
        const expand = page.getByRole('button', {name: 'Expand graph', exact: true});
        await expand.click();
        const dialog = page.getByRole('dialog', {name: 'Explanation of inferred mapping'});
        await dialog.waitFor();
        assert.equal(await dialog.locator('.react-flow__node').count(), 3);
        const bounds = await dialog.locator('.react-flow').boundingBox();
        assert.ok(bounds.width > 1300 && bounds.height > 800);
        if (process.env.UI_SCREENSHOT_DIR) {
            await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/expanded-graph.png`, animations: 'disabled'});
        }
        await page.keyboard.press('Escape');
        await dialog.waitFor({state: 'hidden'});
        assert.equal(await expand.evaluate(el => el === el.ownerDocument.activeElement), true);
    } finally { await page.close(); }
});
