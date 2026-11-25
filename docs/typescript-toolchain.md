# Frontend TypeScript toolchain

The frontend uses TypeScript 7.0.2 for `npm run typecheck` and TypeScript 6.0.3 for
ESLint's compiler API. Both are development dependencies.

## Why two versions are needed

[PR #174](https://github.com/EBISPOT/oxo2/pull/174) replaces the `typescript`
dependency with 7.0.2. That fails dependency resolution: the TypeScript ESLint
packages require `typescript >=4.8.4 <6.1.0`. This was reproduced with npm 12.2.3
against the dev lockfile and with npm 11.19.0 in a fresh project containing only
TypeScript 7.0.2 and `@typescript-eslint/parser` 8.74.0. The npm 12 pin did not
introduce this conflict. It does not establish precisely where GitHub's separate
55-minute Dependabot job stalled, since its full logs were unavailable.

TypeScript 7.0 provides a native compiler but no replacement for the JavaScript
compiler API used by ESLint. Microsoft documents
[running the two versions side by side](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/#running-side-by-side-with-typescript-6-0).
Our setup retains the existing `typescript` 6.x dependency for those imports and
adds `typescript-native` as an npm alias for `typescript` 7.x.

`npm run typecheck` explicitly invokes `node node_modules/typescript-native/bin/tsc --noEmit`.
Both packages advertise a `tsc` executable, so invoking bare `tsc` could select the
wrong compiler. Use the npm script for project checks. The alias also avoids the
`@typescript/native` name, which newer TypeScript ESLint releases reserve for
their optional TypeScript 7.1 API integration.

## Updating the toolchain

Dependabot's ordinary major-version updates for `typescript` are held while
ESLint needs the 6.x API. Patch and minor updates remain eligible. Revisit the hold
when the installed ESLint packages support the replacement API; do not bypass
peer dependency checks with `--force` or `--legacy-peer-deps`.

Do not assume Dependabot maintains the `typescript-native` alias: its upstream
parser currently skips npm aliases by default. Check native compiler releases
alongside routine toolchain maintenance and update the alias and lockfile with:

```sh
npm install --save-dev typescript-native@npm:typescript@^7.0.2
npm ci
npm run lint
npm run typecheck
npm run build
npm test
```

Substitute the reviewed release range when upgrading. Check the selected native
compiler with `node node_modules/typescript-native/bin/tsc --version` and the
ESLint API version with `node -p "require('typescript').version"`.

The native compiler uses platform-specific optional packages. Keep optional
dependencies enabled in CI and local installs. This setup has been validated on
Linux x64 with Node 26.13.1 and npm 12.2.3; other platforms have not been tested.

## Validation on 2026-11-25

The original PR head `c9b8ff2` also fails `npm ci --dry-run` with the same peer
dependency conflict. Against dev commit `a33ec92`, this replacement passes clean
`npm ci`, `npm ls --all`, lint, typecheck, build and all three frontend test files.
The project typecheck command rejects an intentional `TS2322` error and succeeds
when that error is corrected. No existing locked dependency versions changed.
These are local checks; the replacement has not been run through remote CI.
