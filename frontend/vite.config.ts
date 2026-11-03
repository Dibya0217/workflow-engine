import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/swagger-ui": "http://localhost:8080",
      "/v3/api-docs": "http://localhost:8080",
    },
  },
  build: { chunkSizeWarningLimit: 900 },
});
