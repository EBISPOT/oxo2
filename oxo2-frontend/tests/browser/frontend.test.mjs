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

test('asserted mappings precede the graph and fit the details panel without horizontal scrolling', async () => {
    const premises = [
        {subject_id: 'MONDO:0000001', subject_label: 'Disease', subject_iri: 'http://purl.obolibrary.org/obo/MONDO_0000001',
            predicate_id: 'skos:exactMatch', object_id: 'EFO:0000001', object_label: 'Example disease',
            object_iri: 'http://www.ebi.ac.uk/efo/EFO_0000001', mapping_justification: 'semapv:ManualMappingCuration',
            mapping_set_id: 'https://w3id.org/commons/ols/mappings/mondo.ols.sssom.tsv'},
    ];
    const page = await openMapping({...mappingFixture, inference_type: 'SSSOM_INFERENCE',
        asserted_mappings: JSON.stringify(premises), explanation: JSON.stringify(explanation)});
    try {
        const heading = page.getByRole('heading', {name: 'Asserted Mappings', exact: true});
        const graphHeading = page.getByRole('heading', {name: 'Explanation of inferred mapping', exact: true});
        await heading.waitFor();
        assert.equal(await page.getByRole('button', {name: 'Report incorrect mapping', exact: true}).isDisabled(), true);
        assert.ok((await heading.boundingBox()).y < (await graphHeading.boundingBox()).y);
        for (const width of [1440, 1024, 390]) {
            await page.setViewportSize({width, height: 1000});
            const container = page.locator('.MuiTableContainer-root');
            assert.ok(await container.evaluate(el => el.scrollWidth <= el.clientWidth + 1), `Table overflows at ${width}px`);
            assert.equal(await page.getByRole('columnheader').count(), 6);
            if (process.env.UI_SCREENSHOT_DIR) {
                await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/asserted-table-${width}.png`, fullPage: true, animations: 'disabled'});
            }
        }
    } finally { await page.close(); }
});

const reportPremises = [
    {...mappingFixture, mapping_id: 'premise-one', mapping_set_id: 'first-source'},
    {...mappingFixture, mapping_id: 'premise-two', mapping_set_id: 'second-source', subject_id: 'EFO:0001'},
];

test('mapping set rows report the selected mapping without inference context', async () => {
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    const mappings = [mappingFixture, {...mappingFixture, mapping_id: 'second-mapping', subject_id: 'EFO:0001'}];
    const fetched = [];
    try {
        await page.route('**/api/**', route => {
            const url = new URL(route.request().url());
            if (url.pathname.includes('/api/sssom/mappings/')) {
                const id = url.pathname.split('/').at(-1);
                fetched.push(id);
                return route.fulfill({json: {...mappings.find(mapping => mapping.mapping_id === id), predicate_modifier: 'Not'}});
            }
            if (url.pathname.endsWith('/mappings/search')) {
                return route.fulfill({json: {mappings: {content: mappings, totalElements: 2, totalPages: 1, number: 0, size: 10}}});
            }
            return route.fulfill({json: {mapping_set_id: 'source', mapping_set_title: 'Example disease mappings',
                issue_tracker: 'https://github.com/example/ontology/issues'}});
        });
        await page.route('https://www.ebi.ac.uk/ols4/api/terms?**', route => route.fulfill({json: {_embedded: {terms: []}}}));
        await page.goto(`${process.env.UI_BASE_URL || 'http://localhost:20100'}/mapping-set?id=source`);
        const originalUrl = page.url();
        const buttons = page.getByRole('button', {name: 'Report incorrect mapping', exact: true});
        await buttons.first().waitFor();
        assert.equal(await buttons.count(), 2);
        assert.deepEqual(fetched, []);
        for (const [index, mapping] of mappings.entries()) {
            await buttons.nth(index).click();
            const dialog = page.getByRole('dialog', {name: 'Report incorrect asserted mapping', exact: true});
            const report = dialog.getByLabel('Report text', {exact: true});
            await report.waitFor();
            const body = await report.inputValue();
            assert.ok(body.includes(`Subject: ${mapping.subject_id}`));
            assert.ok(body.includes(`/mapping/${mapping.mapping_id}`));
            assert.ok(body.includes('Predicate: Not skos:exactMatch'));
            assert.ok(!body.includes('Noticed while reviewing this inferred mapping'));
            const destination = new URL(await dialog.getByRole('link', {name: 'Open issue tracker'}).getAttribute('href'));
            assert.equal(destination.pathname, '/example/ontology/issues/new');
            assert.ok(destination.searchParams.get('body').includes(`/mapping/${mapping.mapping_id}`));
            await dialog.getByRole('button', {name: 'Close report'}).click();
            await dialog.waitFor({state: 'hidden'});
            assert.equal(page.url(), originalUrl);
            assert.equal(await buttons.nth(index).evaluate(el => el === el.ownerDocument.activeElement), true);
        }
        assert.deepEqual(fetched, mappings.map(mapping => mapping.mapping_id));
    } finally { await page.close(); }
});

async function openReportableInference() {
    return openMapping({...mappingFixture, inference_type: 'SSSOM_INFERENCE',
        asserted_mappings: JSON.stringify(reportPremises), explanation: JSON.stringify(explanation)});
}

test('each asserted row reports its own full mapping and contacts with inference context', async () => {
    const page = await openReportableInference();
    try {
        const fetched = [];
        await page.route('**/api/sssom/mappings/premise-*', route => {
            const id = route.request().url().split('/').at(-1);
            fetched.push(id);
            return route.fulfill({json: {...reportPremises.find(premise => premise.mapping_id === id),
                predicate_modifier: 'Not'}});
        });
        await page.route('**/api/v2/mapping-sets/by-id?**', route => {
            const id = new URL(route.request().url()).searchParams.get('mappingSetId');
            return route.fulfill({json: {mapping_set_id: id, mapping_set_title: id,
                ...(id === 'first-source' ? {issue_tracker: 'https://github.com/example/first/issues'}
                    : {mailing_list: 'second@example.org'})}});
        });
        const originalUrl = page.url();
        const buttons = page.getByRole('button', {name: 'Report incorrect mapping', exact: true});
        const dialog = page.getByRole('dialog', {name: 'Report incorrect asserted mapping', exact: true});
        assert.deepEqual(fetched, []);
        for (const [index, premise] of reportPremises.entries()) {
            await buttons.nth(index).click();
            const report = dialog.getByLabel('Report text', {exact: true});
            await report.waitFor();
            const body = await report.inputValue();
            assert.ok(body.includes(`Source mapping set: ${premise.mapping_set_id}`));
            assert.ok(body.includes(`OxO mapping: ${originalUrl.replace('/example', `/${premise.mapping_id}`)}`));
            assert.ok(body.includes(`Noticed while reviewing this inferred mapping: ${originalUrl}`));
            assert.ok(body.includes('Predicate: Not skos:exactMatch'));
            assert.equal(await dialog.getByRole('region', {name: 'Asserted mapping to report'}).getByText('Not', {exact: true}).count(), 1);
            const link = dialog.getByRole('link', {name: index === 0 ? 'Open issue tracker' : 'Open email draft'});
            const destination = new URL(await link.getAttribute('href'));
            assert.equal(destination.pathname, index === 0 ? '/example/first/issues/new' : 'second@example.org');
            assert.ok(destination.searchParams.get('body').includes(originalUrl));
            if (index === 0) {
                for (const width of [1440, 390]) {
                    await page.setViewportSize({width, height: 1000});
                    assert.ok(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1));
                    if (process.env.UI_SCREENSHOT_DIR) {
                        await page.screenshot({path: `${process.env.UI_SCREENSHOT_DIR}/asserted-report-${width}.png`, animations: 'disabled'});
                    }
                }
                await page.setViewportSize({width: 1440, height: 1000});
                await page.keyboard.press('Escape');
            } else {
                await dialog.getByRole('button', {name: 'Close report'}).click();
            }
            await dialog.waitFor({state: 'hidden'});
            assert.equal(page.url(), originalUrl);
            assert.equal(await buttons.nth(index).evaluate(el => el === el.ownerDocument.activeElement), true);
        }
        assert.deepEqual(fetched, ['premise-one', 'premise-two']);
    } finally { await page.close(); }
});

test('asserted report lookup can retry and shows missing mappings or missing contacts clearly', async () => {
    const page = await openReportableInference();
    try {
        let status = 503;
        await page.route('**/api/sssom/mappings/premise-one', route => route.fulfill({status,
            json: status === 200 ? reportPremises[0] : {message: 'Unavailable'}}));
        await page.route('**/api/sssom/mappings/premise-two', route => route.fulfill({status: 404, json: {}}));
        await page.route('**/api/v2/mapping-sets/by-id?**', route => route.fulfill({json: {mapping_set_id: 'first-source'}}));
        const buttons = page.getByRole('button', {name: 'Report incorrect mapping', exact: true});
        await buttons.first().click();
        const dialog = page.getByRole('dialog', {name: 'Report incorrect asserted mapping', exact: true});
        await dialog.getByRole('alert').waitFor();
        assert.equal(await dialog.getByLabel('Report text', {exact: true}).count(), 0);
        status = 200;
        await dialog.getByRole('button', {name: 'Try again'}).click();
        await dialog.getByText('No reporting contact available for this mapping source.', {exact: true}).waitFor();
        await dialog.getByRole('button', {name: 'Close report'}).click();
        await dialog.waitFor({state: 'hidden'});
        await buttons.nth(1).click();
        await dialog.getByText('This asserted mapping is no longer available for reporting.', {exact: true}).waitFor();
        assert.equal(await dialog.getByLabel('Report text', {exact: true}).count(), 0);
    } finally { await page.close(); }
});
