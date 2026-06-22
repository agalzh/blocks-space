# Stack Unknown — demo pitch

Two-part doc. Part 1 is a tight ~90-second spoken script. Part 2 is the
feature inventory + sample-query cheat sheet you fall back to when a judge
asks "what else does it do?"

---

## Part 1 — 90-second script

Target: ~225 spoken words, 6 chat commands. Practice once; do not improvise
beyond the lines below.

### Cold open (0:00–0:10)
Server already running. Player spawned on the lit quartz platform. Chat clear.

> "This is Minecraft. But the world I'm about to load isn't procedural — it's
> a real dataset. Every block is a data point. Every beacon is a cluster
> mean. The glowing block in the middle is the global centroid. I'm going to
> fly through my data."

### /upload (0:10–0:22)
```
/upload C:\Users\Nikil PS\blocks_space\samples\iris.csv csv
```
~150 colored blocks appear across the void; a soft beacon-activate chimes.

> "150 rows of the iris dataset. Python ingests it, UMAP projects to three
> dimensions, KMeans picks k by silhouette, IsolationForest flags outliers.
> The plugin places one block per row. Colors are clusters; glass blocks are
> outliers."

### /center + /axes + hover (0:22–0:50)
```
/center
/axes
```
TP straight above the centroid. The red/green/blue axis cross appears.
Look at a data block.

> "Hover anything for live metrics. A name-tag floats above the block
> showing its offset from the centroid. The sidebar shows cluster id,
> distance to mean, outlier score, and three feature values. The colored
> line points to the cluster mean. Look at a mean — it points to the
> centroid. Look at an axis — you get the axis span and the feature names."

### /overview (0:50–1:00)
```
/overview
```

> "Gemini reads the dataset summary and explains it in plain English in two
> paragraphs."

### /query + /visualize (1:00–1:22)
```
/query which feature best separates the clusters?
/visualize beam cluster 0 for 30 seconds with end_rod
```
Vertical light pillars rise from every cluster-0 block for half a minute.

> "I can also ask questions. The query is bounded — Gemini can only discuss
> this dataset, never emit commands. And I can drive the visualization in
> English — Gemini outputs a strict JSON tool spec; the plugin validates and
> applies it. Highlight, pulse, beam, connect, recolor, hide — for any
> duration up to a minute."

### Close (1:22–1:30)
```
/reset
```
World clears, soft beacon-deactivate. Spawn platform restored.

> "Stack Unknown: data exploration you can literally fly through."

---

## Part 2 — feature inventory

If the demo runs short or a judge digs in, these are the talking points and
the commands to reach for.

### Pipeline (one breath)

CSV → Python (UMAP-3, KMeans++ auto-k, IsolationForest) → Scene Graph
JSON → WebSocket → Paper plugin → voxel render + interactive overlay.

### What's in the world

| Block | Meaning |
|---|---|
| Colored concrete | one row, color = its cluster |
| Glass | outlier (IsolationForest score > 0.70) |
| Beacon | cluster mean |
| Sculk catalyst (animated, dark glow) | global centroid |
| Invisible barrier (hover-targetable) | axis sample, every 4 blocks |
| Red / lime / blue dust ribbon | X / Y / Z axis through the centroid |
| Sea-lantern corners + end-rod accents | spawn platform |

### Hover interactions

| Target | Floating nameplate | Sidebar HUD |
|---|---|---|
| Data point | `Δ +dx +dy +dz` (R/G/B per axis), relative to centroid | dataset, cluster id, distance to mean, outlier vs threshold, cluster size, top 3 feature values (≤ 8 features) |
| Cluster beacon | — | dataset, cluster id, distance to centroid, cluster size |
| Centroid block | — | dataset, cluster count, point count, outlier cutoff |
| Axis (anywhere on the line) | — | axis label (color-coded), offset from centroid, axis span, dataset, feature names |
| Air / unknown | hidden | hidden |

Plus particle lines: orange-or-cluster-colored dust from point to its
beacon; end-rod from the beacon to the centroid; end-rod spokes from the
centroid to every beacon.

### Commands

| Command | Effect |
|---|---|
| `/upload <path> [csv\|json]` | Send dataset to engine; render scene |
| `/loadmock <path>` | Render a Scene Graph JSON straight from disk (skip engine) |
| `/center` | TP straight above the centroid, looking down |
| `/axes [show\|hide\|toggle]` | Toggle the X/Y/Z reference axes |
| `/overview` | Gemini 2-paragraph dataset summary |
| `/query <text>` | Railguarded Q&A about the loaded dataset |
| `/visualize <text>` | Natural language → validated visualization actions |
| `/reset` | Clear bounds in tick-spread chunks; rebuild spawn platform |
| `/ping` | Smoke test |

### `/visualize` action vocabulary

| Action | What it does | Optional `duration_sec` (1–60) |
|---|---|---|
| `recolor` | Re-place matched blocks as a new material | — (permanent) |
| `highlight` | Spawn particles above matched blocks | yes |
| `pulse` | Expanding sparkle ring around each block | yes |
| `beam` | Vertical 12-block particle column above each | yes |
| `connect` | Cluster-colored dust line from point back to its mean | yes |
| `hide` | Set matched blocks to AIR | — |

Selectors: `all`, `cluster_id=N`, `cluster_id!=N`, `outlier_score>X`,
`outlier_score<X`, `outlier_score>=X`, `outlier_score<=X`.

Particles: `flame`, `soul_fire_flame`, `heart`, `happy_villager`,
`end_rod`, `dust`, `crit`, `glow`, `totem`, `witch`.

### Safety / rails (mention if asked)

- Gemini **never** emits raw `/` commands. `/visualize` is constrained to a
  strict JSON tool spec; everything is validated against an allow-list
  server-side AND plugin-side.
- `/query` answers are sanitized in two layers: server strips code fences
  and any line starting with `/`, `!`, `@`; plugin re-sanitizes before
  `broadcastMessage` (which is plain text, never a command path).
- Off-topic or jailbreak attempts ("ignore your rules") get a polite refusal
  via the system prompt.
- Gemini calls are LRU-cached (last 5) so the demo doesn't burn quota.

### Sample queries that work well

**`/query`**
- `which feature best separates the clusters?`
- `what's the typical sepal length in cluster 0?`
- `how spread out are the outliers compared to the rest?`
- `if I had to label these clusters in one word each, what would you pick?`

**`/visualize`**
- `highlight outliers in flame for 30 seconds`
- `beam cluster 0 for 20 seconds with end_rod`
- `pulse outliers for 45 seconds with witch`
- `connect cluster 1 to its centre with dust for 15 seconds`
- `recolor outliers to magenta_glazed_terracotta`
- `hide cluster 2`

---

## Pre-demo checklist

- [ ] `docker compose up -d` — wait for `Done (` in `docker logs -f mc`
- [ ] `.\engine\.venv\Scripts\python.exe -u -m engine.ws_server` running;
      confirm `netstat -ano | findstr :8765` shows LISTENING
- [ ] In-game: `/gamemode spectator`, F1 to hide vanilla HUD, F5 for camera
- [ ] OBS: 1080p+, GUI scale 3 so the sidebar reads on stream
- [ ] Sticky note with the three absolute CSV paths (iris, digits_1k, wine)
- [ ] Pre-warm caches: `/upload` iris, `/overview`, `/query …`, `/visualize …`
      once before the live run so all four hit the LRU on the second call
- [ ] Audio levels: in-game sound around 50%, mic peaks below clipping

## If something breaks

| Failure | Reaction |
|---|---|
| Gemini 429 / outage | Fall back to `/visualize highlight outliers with flame` — heuristic parser still produces a valid action |
| WS disconnect mid-demo | Keep narrating; plugin reconnects on backoff (≤30 s). Cached `/overview` will still hit when WS is back |
| `/upload` slow on stage | While it loads, narrate the pipeline diagram (CSV → engine → scene graph → WS → plugin) |
| Particle lag during `beam` | Stop the action: `/visualize highlight all for 1 seconds` to clear momentum |
| Block-flood TPS drop on `digits_1k` | Cut to iris; mention "for the demo we're bounded to 2000 points per scene" |
| Centroid in a bad spot for the camera | `/center` again; the camera resets each time |
