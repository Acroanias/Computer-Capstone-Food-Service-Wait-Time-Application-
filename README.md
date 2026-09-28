# CampusTable

A responsive, single-page campus dining occupancy preview. Built with semantic HTML, organized CSS, and vanilla JavaScript modules. No build step, backend, connector, or polling is included.

## Run locally

From the repository root:

```sh
python3 -m http.server 8000
```

Open http://localhost:8000. Use a local HTTP server because browsers restrict ES modules when opening files directly.

## Files

- `index.html` — page structure and dining card template.
- `css/variables-reset.css` — palette, typography, reset, and accessibility defaults.
- `css/layout.css` — page layout and responsive breakpoints.
- `css/components.css` — component styling and states.
- `js/mock-data.js` — all sample locations and snapshot data.
- `js/occupancy.js` — shared fullness thresholds.
- `js/app.js` — card rendering, summary totals, and display logic.
- `assets/rollins-logo.png` — Rollins header logo and favicon.

Fullness labels are Quiet (under 20%), Light (20–39%), Moderate (40–64%), Busy (65–84%), and Full (85–100%). They use three Rollins color bands: blue (`#27409B`) for Quiet/Light, gold (`#FAB20B`) for Moderate, and deep navy with a stripe pattern for Busy/Full. Visible text labels and accessible occupancy meters supplement color. System typography uses Avenir Next / Segoe UI and Iowan Old Style / Palatino / Georgia without external font requests.

To change the preview, edit only `js/mock-data.js`. For a future API integration, replace the data provider and pass snapshots of the same shape into `renderDiningSnapshot`. A roughly 60-second polling schedule can be added at that point; none runs now. Expanded location details are preserved when the snapshot is rendered again.
