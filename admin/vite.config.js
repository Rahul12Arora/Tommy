import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// ARCHITECTURE §14.11. The backend serves the build at /console/ (D-42); in development Vite serves it and passes
// API calls through to the backend, so the browser sees one origin and the session cookie works without CORS.
export default defineConfig({
  base: "/console/",
  plugins: [react()],
  // RJSF + Ajv are ~600 kB minified; one admin on a desktop browser, so one bundle is fine
  build: { chunkSizeWarningLimit: 800 },
  server: {
    port: 5173,
    proxy: {
      "/admin": "http://localhost:3000",
      "/v1": "http://localhost:3000",
    },
    // The form is generated from ../shared/config.schema.json, outside this folder
    fs: { allow: [".", "../shared"] },
  },
});
