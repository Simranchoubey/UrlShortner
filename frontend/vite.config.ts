import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Vite 8 defaults to lightningcss for CSS minification, but lightningcss
    // 1.33.0 throws a false "Unknown at rule: @keyframes" on our stylesheet
    // (the CSS is valid — esbuild and browsers parse it fine). Use esbuild,
    // which is the well-supported alternative.
    cssMinify: 'esbuild',
  },
})
