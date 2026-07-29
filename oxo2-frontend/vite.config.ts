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
                manualChunks: {
                    react: ['react', 'react-dom'],
                    tables: ['material-react-table'],
                    graphs: ['react-force-graph-2d', '@xyflow/react']
                }
            }
        },
        chunkSizeWarningLimit: 1024 // optional once you confirm chunks are split
    }    
})
