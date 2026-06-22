# Stack Unknown

**Minecraft as a 3D data viewer.** Drop a CSV in, fly through it as a voxel
cloud, ask questions in plain English. Cluster means are beacons, outliers
are glass, the global centroid is a sculk catalyst. Hover anything for
live metrics.

Built in 24 hours, solo, for a hackathon.

---

## What you actually see

You connect to a Minecraft server. The world is empty void with a small lit
platform. You run `/upload path/to/data.csv` and within ~5 seconds a cloud
of colored blocks fills the world — one block per row, color per cluster,
glass per outlier. A glowing sculk catalyst marks the global centroid.
Beacons mark each cluster mean.

You fly around. Looking at any block:
- A floating nameplate above it shows its offset from the centroid as
  `Δ +dx +dy +dz` (color-coded per axis).
- A sidebar on the right shows the cluster id, distance to its cluster
  mean, outlier score vs the threshold, cluster size, and (for small
  datasets) the top three feature values.
- A dust line traces from the block to its cluster's beacon. The color
  of that line matches the cluster's color.

Look at a beacon → end-rod line shoots to the centroid. Look at the
centroid → all beacons light up at once. Toggle `/axes` to overlay a
red/green/blue cross through the centroid.

Then ask the data anything:
- `/overview` — Gemini summarises the dataset in two paragraphs of plain
  English, chunked into chat.
- `/query <text>` — railguarded Q&A. Gemini only discusses the loaded
  dataset and never emits commands.
- `/visualize <text>` — translates English into validated visualization
  actions: highlight, pulse, beam, connect, recolor, hide. Any duration
  up to 60 seconds.

## Architecture

```
CSV / JSON
        │
        ▼
Python engine (ingest → UMAP-3 → KMeans++ → IsolationForest → palette)
        │
        ▼
Scene Graph JSON  (≤ 2000 points)
        │
        ▼   WebSocket on :8765
        ▼
Paper plugin 1.20.4
        │
        ▼   world.setBlockData (no physics, no commands)
        ▼
Voxel world + hover/HUD/nameplate + Gemini-driven visualization
```

The pipeline is one-way for upload; Gemini calls go back through the same
WebSocket as separate request/response pairs.

## Commands

| Command | Effect |
|---|---|
| `/upload <path> [csv\|json]` | Send a dataset to the engine and render the scene |
| `/loadmock <path>` | Render a Scene Graph JSON straight from disk (skip the engine) |
| `/center` | Teleport 25 blocks above the centroid, camera pointing down |
| `/axes [show\|hide\|toggle]` | Toggle X (red) / Y (lime) / Z (blue) reference axes |
| `/overview` | Gemini two-paragraph dataset summary |
| `/query <text>` | Bounded Q&A about the loaded dataset |
| `/visualize <text>` | English → validated visualization actions |
| `/reset` | Clear the bounds and restore the spawn platform |
| `/ping` | Smoke test |

All commands except `/ping` are op-only.

## Hover interactions

| Target | Floating nameplate | Sidebar HUD |
|---|---|---|
| Data point | `Δ +dx +dy +dz` relative to centroid, R/G/B per axis | dataset · cluster id · distance to mean · outlier vs threshold · cluster size · top 3 feature values (≤ 8 features) |
| Cluster beacon | — | dataset · cluster id · distance to centroid · cluster size |
| Centroid catalyst | — | dataset · cluster count · point count · outlier cutoff |
| Axis (any spot) | — | axis label (color-coded) · offset from centroid · axis span · dataset · first three feature names |

Particle lines drawn in parallel:
- Point → cluster mean: dust line in the cluster's own color.
- Cluster mean → centroid: end-rod sparkle line.
- Centroid → every cluster mean: end-rod spokes.

## `/visualize` vocabulary

**Actions** (and whether they take a `duration_sec`, 1–60):

| Action | Behavior | Duration |
|---|---|---|
| `recolor` | Replace matched blocks with a new material | — |
| `highlight` | Spawn particles above matched blocks | yes |
| `pulse` | Expanding sparkle ring around each block | yes |
| `beam` | 12-block vertical particle column above each | yes |
| `connect` | Cluster-colored dust line from point back to its mean | yes |
| `hide` | Replace matched blocks with AIR | — |

**Selectors**: `all`, `cluster_id=N`, `cluster_id!=N`,
`outlier_score>X`, `outlier_score<X`, `outlier_score>=X`,
`outlier_score<=X`.

**Particles**: `flame`, `soul_fire_flame`, `heart`, `happy_villager`,
`end_rod`, `dust`, `crit`, `glow`, `totem`, `witch`.

Sample queries that work:
```
/visualize highlight outliers in flame for 30 seconds
/visualize beam cluster 0 for 20 seconds with end_rod
/visualize pulse outliers for 45 seconds with witch
/visualize connect cluster 1 to its centre with dust for 15 seconds
/visualize recolor outliers to magenta_glazed_terracotta
/visualize hide cluster 2
```

Sample `/query` prompts:
```
/query which feature best separates the clusters?
/query what's the typical sepal length in cluster 0?
/query how spread out are the outliers compared to the rest?
```

## Safety rails

Anything that comes back from Gemini is treated as untrusted text.

- `/visualize`: server-side validates every action against an allow-list
  (action name, selector regex, block id pattern, particle whitelist,
  duration range). The plugin re-validates before touching the world.
  Gemini is never asked to emit raw `/` commands.
- `/query`: system prompt bounds the assistant to the loaded dataset and
  forbids commands / code / markdown. Server strips code fences and any
  line beginning with `/`, `!`, `@`. Plugin re-sanitizes before
  `broadcastMessage` (which is a plain chat path, never a command).
- Off-topic / jailbreak attempts are refused by the system prompt.

All Gemini responses are LRU-cached (5 entries) so demos don't burn quota.

## Sample datasets

In `samples/`:

| File | Rows × cols | Notes |
|---|---|---|
| `iris.csv` | 150 × 4 | Clean baseline (sklearn iris, features-only) |
| `digits_1k.csv` | 1000 × 64 | Dense (sklearn digits, first 1000) |
| `wine.csv` | 178 × 13 | Mid-size chemistry features (sklearn wine) |
| `iris_mock.json` | 10 × 4 | Hand-authored Scene Graph JSON; load with `/loadmock` |

Generate the digits / wine CSVs from scratch with:
```
python -m engine.make_samples
```

## How to extend

- **Custom dataset**: any CSV with at least 2 numeric columns. The engine
  strips non-numeric columns and drops NaN rows. Pass an absolute path to
  `/upload`. Capped at 2000 points (random-sampled if larger).
- **Custom actions**: edit `gemini.ALLOWED_ACTIONS`, the visualize prompt,
  `_clean_action`, and add a switch arm in `plugin/.../ActionExecutor.java`.
- **Different LLM**: replace `engine/gemini.py`. The plugin only sees the
  WS message shape `{type: "overview" | "actions" | "query_answer", ...}`.

## Limits

- 2000 points per scene (chunk-load safety; downsamples if exceeded).
- Bounds are a `129 × 57 × 129` box in MC world space.
- `digits_1k.csv` takes ~5 seconds to render end-to-end; iris and wine
  finish in under a second.
- `/reset` clears the whole bounds in ~30 ticks (1.5 s).
- Highlight / pulse / beam / connect cap at 60 seconds.

## Tech stack

- Python 3.11, NumPy, pandas, scikit-learn, umap-learn, IsolationForest
- google-generativeai (`gemini-2.5-flash-lite`)
- WebSocket: `websockets` (Python) ↔ `Java-WebSocket` (plugin)
- PaperMC 1.20.4 plugin, Java 17, Maven shade
- Docker (itzg/minecraft-server) for the server runtime

## Setup

See [export.md](export.md) for the full setup procedure on a fresh
machine — prerequisites, build, configuration, troubleshooting.

## License / credits

Hackathon project. Solo build, 24 hours.

The Anthropic / Google APIs and the PaperMC project are credited in their
respective places. Sample datasets are from `sklearn.datasets`.
