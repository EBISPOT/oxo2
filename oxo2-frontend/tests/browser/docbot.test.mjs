import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import process from 'node:process';
import { chromium } from 'playwright';

const base = process.env.UI_BASE_URL || 'http://localhost:20100';
const sdkOrigin = 'https://docbot-64c680.gitdocs.ebi.ac.uk';
let browser;
before(async () => {
    browser = await chromium.launch({executablePath: process.env.BROWSER_EXECUTABLE || undefined});
});
after(async () => { await browser?.close(); });

test('one DocBot instance preserves chat state across client-side navigation', async () => {
    const page = await browser.newPage();
    let sdkRequests = 0;
    try {
        await page.route('https://www.ebi.ac.uk/docbot/**', route => route.abort());
        await page.route(`${sdkOrigin}/**`, route => {
            if (route.request().url() !== `${sdkOrigin}/Chatbot-Modal.web.js`) {
                return route.fulfill({contentType: 'text/css', body: ''});
            }
            sdkRequests++;
            // Model a stateful custom element without relying on the external chat service.
            return route.fulfill({contentType: 'text/javascript', body: `
                customElements.define('docbot-modal', class extends HTMLElement {
                    connectedCallback() {
                        this.innerHTML = '<label>DocBot question <input /></label>';
                    }
                });
            `});
        });
        await page.goto(`${base}/about`);
        const question = page.getByRole('textbox', {name: 'DocBot question'});
        await question.fill('How do I find mappings?');
        const originalWidget = await page.locator('docbot-modal').elementHandle();
        for (const destination of ['Documentation', 'About']) {
            await page.getByRole('navigation').getByRole('link', {name: destination, exact: true}).click();
            await page.waitForURL(`**/${destination === 'Documentation' ? 'docs' : 'about'}`);
            assert.equal(await page.locator('docbot-modal').count(), 1);
            assert.equal(await originalWidget.evaluate(element => element.isConnected), true);
            assert.equal(await question.inputValue(), 'How do I find mappings?');
        }
        assert.equal(sdkRequests, 1);
        assert.equal(await originalWidget.getAttribute('selectedteam'), 'ontology_tools');
        assert.equal(await originalWidget.getAttribute('backendurl'), 'https://www.ebi.ac.uk/docbot/api');
        assert.equal(await originalWidget.getAttribute('enableselector'), 'false');
    } finally { await page.close(); }
});

test('OxO navigation and standalone help remain usable when DocBot assets fail', async () => {
    const page = await browser.newPage();
    const failures = [];
    try {
        page.on('pageerror', error => failures.push(error.message));
        await page.route(`${sdkOrigin}/**`, route => route.abort());
        await page.route('https://www.ebi.ac.uk/docbot/**', route => route.abort());
        await page.goto(`${base}/about`);
        await page.getByRole('navigation').getByRole('link', {name: 'Documentation', exact: true}).click();
        await page.waitForURL('**/docs');
        const fallback = page.getByRole('link', {name: 'Ask Ontology Tools DocBot', exact: true});
        await fallback.waitFor({state: 'visible'});
        assert.equal(await fallback.isVisible(), true);
        assert.equal(await fallback.getAttribute('href'), 'https://www.ebi.ac.uk/docbot/?selected=ontology_tools');
        assert.equal(await fallback.getAttribute('target'), '_blank');
        assert.equal(await page.getByRole('link', {name: 'Swagger UI', exact: true}).isVisible(), true);
        await page.getByRole('navigation').getByRole('link', {name: 'About', exact: true}).click();
        await page.waitForURL('**/about');
        assert.deepEqual(failures, []);
    } finally { await page.close(); }
});
