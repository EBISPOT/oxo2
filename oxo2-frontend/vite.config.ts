import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// OXO_PUBLIC_URL is deployed without a trailing slash (`/oxo2`) because two other consumers need it
// that way: api.ts concatenates it with paths that already start with a slash, and BrowserRouter
// wants a bare basename. Vite's `base`, and the `import.meta.env.BASE_URL` / `%BASE_URL%` it feeds,
// are concatenated with paths that do NOT start with a slash, so they need the trailing slash — Vite
// does not add one. Normalise here rather than at the deployment so the two meanings stay separate.
const publicUrl = process.env.OXO_PUBLIC_URL || '/';
const baseUrl = publicUrl.endsWith('/') ? publicUrl : `${publicUrl}/`;

// Vite 8 bundles with rolldown, whose `codeSplitting.groups` replaces rollup's `manualChunks`.
// The two are not the same operation. A `manualChunks` name list force-includes the named
// package whether or not anything imports it; a group `test` matches only modules already in
// the graph. These patterns are therefore written against the resolved module id — the on-disk
// path under node_modules — rather than the bare import specifier the name list took.
//
// The trailing separator is load-bearing: it keeps `react` off `react-router`, `react-is` and
// `react-transition-group`. Packages reached only through a grouped one follow it into that
// group, so `scheduler` lands beside `react-dom` without being named here.
const reactTest = /node_modules[\\/](?:react|react-dom)[\\/]/
const tablesTest = /node_modules[\\/]material-react-table[\\/]/
const graphsTest = /node_modules[\\/](?:react-force-graph-2d|@xyflow[\\/]react)[\\/]/

// https://vite.dev/config/
export default defineConfig({
    base: baseUrl,
    plugins: [
        react(),
        tailwindcss()
    ],
    envPrefix: ['REACT_APP_', 'OXO_'],
    server: {
        port: Number(process.env.OXO_FRONTEND_PORT) || 8080,
        strictPort: true
    },
    // To fix the issue of the bundle being too large, we need to split the bundle into smaller chunks.
    build: {
        rollupOptions: {
            output: {
                codeSplitting: {
                    groups: [
                        {name: 'react', test: reactTest},
                        {name: 'tables', test: tablesTest},
                        {name: 'graphs', test: graphsTest}
                    ]
                }
            }
        },
        chunkSizeWarningLimit: 1024 // optional once you confirm chunks are split
    }
})
