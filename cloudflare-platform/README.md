# Elysium Training — Separate Cloudflare D1 Web Platform

This folder contains a standalone web platform powered by **Cloudflare Workers + D1** that displays:
- **Each person's profile** (Name, Height, Weight, BMI, Body Fat %, Sex, Age)
- **Exercise progress** (PRs, progression bars, total volume)
- **Logged sets, weights (kg), and reps** across sessions
- **Offline caching** via `localStorage` once loaded in the browser

## Quick Setup
1. Update `database_id` in `wrangler.toml` with your `CLOUDFLARE_DATABASE_ID`.
2. Deploy from this folder:
   ```bash
   npx wrangler deploy
   ```
   *(Or paste `worker.js` directly into Cloudflare Dashboard → Workers & Pages → Create Worker and bind your D1 database as variable name `DB`.)*
3. Copy your deployed `https://elysium-d1-platform.<subdomain>.workers.dev` URL into the Elysium Training Android app under **Settings → Linked Cloud Platform (D1 Dashboard)**.
