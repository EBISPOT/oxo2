import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
    plugins: [
        react(),
        tailwindcss()
    ],
    envPrefix: ['REACT_APP_', 'OXO_'],
    server: {
        port: 8080,
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
