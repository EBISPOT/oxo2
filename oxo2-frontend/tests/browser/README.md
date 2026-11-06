# Frontend browser checks

Run the frontend development server on port 20100, then run:

```sh
npx playwright install chromium
npm run test:browser
```

The checks stub backend responses in the browser and do not need a dataload or backend.
Set `UI_BASE_URL` to test a different frontend URL. Set `BROWSER_EXECUTABLE` to use an
already installed Chrome/Chromium instead of Playwright's downloaded browser.
