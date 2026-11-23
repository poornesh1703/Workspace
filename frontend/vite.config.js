import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Any call to /api/... from the browser is forwarded to the Spring Boot backend.
// This avoids CORS problems during development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8080" },
  },
});
