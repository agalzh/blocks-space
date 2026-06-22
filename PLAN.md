# Stack Unknown — Minecraft as a 3D Data Viewer

> A 3D tensor & spatial data visualization tool. Ingests CSV/JSON, projects it into a Minecraft void world as voxel clusters, and lets the user literally fly through their data. Gemini acts as the natural-language layer that translates queries into in-game commands.

**Hackathon constraint: 24 hours, solo build.**

---

## 1. Architecture (single source of truth)

```
CSV / JSON dataset
        │
        ▼
Python Ingest + Math Engine ──┬── Deterministic: NumPy / sklearn / UMAP / KMeans++ / HDBSCAN / IsolationForest
                              └── Gemini API: semantic grouping, labels, layout advice, /overview + /visualize NL→commands
        │
        ▼
Scene Graph JSON  (points, clusters, centroids, colors, labels, edges)
        │
        ▼   ZeroMQ PUB  OR  WebSocket batch stream  (pick ONE — see §2)
        ▼
Paper Java Plugin (1.20.4)  ── consumes scene graph, issues /setblock, /particle, /summon
        │
        ▼
Minecraft Void World Renderer (spectator-mode player flies through the data)
```

In-game UX surface:
- `/upload <absolute path> <json|csv>` — ingest a dataset
- `/visualize <natural language query>` — Gemini → MC commands
- `/overview` — Gemini summary of the loaded dataset
- Hover a data block → particle line to **cluster mean (conduit)**
- Hover the conduit → particle line to the **global centroid**

---

## 2. Tech stack (locked, with fallbacks)

| Layer | Primary | Fallback if it breaks |
|---|---|---|
| Math engine | Python 3.11, NumPy, pandas, scikit-learn, umap-learn, hdbscan | Skip UMAP → PCA only. Skip HDBSCAN → KMeans++ only. |
| LLM | Gemini 2.0 Flash (`google-generativeai`) | Hard-coded command templates if API fails |
| Transport | **WebSocket** (`websockets` py ↔ Java-WebSocket) | ZeroMQ (`jeromq`) if WS handshake fights Paper |
| Server | PaperMC 1.20.4 + Java 17 | Spigot 1.20.4 |
| Plugin build | Maven shade plugin | Gradle if Maven misbehaves |
| Renderer commands | `/setblock`, `/fill`, `/particle`, `/summon marker`, conduit blocks | Armor stands with name tags for labels |

**Why WebSocket over ZeroMQ for 24h:** one less native dependency on Windows, easier to debug from a browser, fewer "DLL not found" foot-guns inside the JVM. ZeroMQ stays as the documented fallback only.

---

## 3. Scene Graph JSON contract (freeze this EARLY — hour 2)

Everything downstream depends on this. Lock it before writing the renderer.

```json
{
  "session_id": "uuid",
  "dataset": { "name": "iris.csv", "rows": 150, "dims": 4 },
  "bounds": { "min": [0,0,0], "max": [128, 64, 128] },
  "global_centroid": [64, 32, 64],
  "clusters": [
    {
      "id": 0,
      "label": "setosa-like",
      "color": "minecraft:red_concrete",
      "mean": [20, 30, 40],
      "point_ids": [0, 1, 2, 17]
    }
  ],
  "points": [
    {
      "id": 0,
      "pos": [20, 30, 41],
      "cluster_id": 0,
      "outlier_score": 0.12,
      "block": "minecraft:red_concrete",
      "meta": { "original_row": { "sepal_len": 5.1 } }
    }
  ],
  "commands": []
}
```

Rules:
- Coordinates are **integers** in MC world space, clamped to bounds.
- Colors are **block IDs**, not hex — the renderer cannot translate.
- Batch size cap: **2000 points per scene** for the demo (chunk-load safety).

---

## 4. Phase plan — 24 hours

Times are budgets, not promises. If a phase slips, cut scope inside it before stealing from the next.

### Phase 0 — Setup (0:00 → 1:00) — **1h**
**Goal:** every tool installed, "hello world" passes through every layer.

- [ ] Install Python 3.11, create venv, `pip install numpy pandas scikit-learn umap-learn hdbscan google-generativeai websockets python-dotenv`
- [ ] Install JDK 17 + Maven, download PaperMC 1.20.4 jar
- [ ] First-run the server, accept EULA, set `gamemode=spectator`, `level-type=minecraft:flat` with void preset, `online-mode=false` for local dev
- [ ] Scaffold Maven plugin (`groupId: com.unknown.stack`), shade in `Java-WebSocket`
- [ ] `.env` with `GEMINI_API_KEY`, smoke-test one Gemini call from Python
- [ ] Commit. Tag `phase-0-done`.

**Exit criteria:** server boots, plugin loads with a `/ping` command, Python prints a Gemini reply.

### Phase 1 — Scene Graph contract + dummy renderer (1:00 → 3:30) — **2.5h**
**Goal:** Java can render a hard-coded Scene Graph from disk. No math, no LLM yet.

- [ ] Write the JSON schema above into `docs/scene_graph.schema.json`
- [ ] Hand-author `samples/iris_mock.json` (10 points, 2 clusters)
- [ ] Plugin: `/loadmock` reads file, runs `/setblock` per point + `/summon` conduit at each cluster mean
- [ ] Verify in-game: blocks appear, conduits glow

**Exit criteria:** voxel cloud visible in MC, sourced from JSON on disk.

### Phase 2 — Python math engine (3:30 → 7:00) — **3.5h**
**Goal:** real datasets → real Scene Graph JSON.

- [ ] `ingest.py`: CSV/JSON loader, numeric coercion, NaN drop
- [ ] `layout.py`: standardize → UMAP to 3D (n_components=3); PCA fallback for <50 rows
- [ ] `cluster.py`: KMeans with KMeans++ init; auto-k via silhouette in [2..8]; HDBSCAN as opt-in
- [ ] `outliers.py`: IsolationForest score → mapped to block brightness (e.g., concrete vs glass)
- [ ] `scene.py`: rescale to MC bounds (0–128 X/Z, 4–60 Y), assign block colors per cluster, write Scene Graph
- [ ] CLI: `python -m engine path/to/data.csv` → emits `out/scene.json`

**Exit criteria:** `iris.csv` → 3 visible clusters with sensible separation when loaded via `/loadmock`.

### Phase 3 — Transport: Python ↔ Plugin (7:00 → 10:00) — **3h**
**Goal:** live streaming, not file-passing.

- [ ] Python `ws_server.py`: WebSocket server on `ws://127.0.0.1:8765`, sends Scene Graph as one message OR chunks of ≤500 points
- [ ] Plugin client connects on enable, reconnects with backoff
- [ ] `/upload <path> <json|csv>` in MC → plugin sends `{cmd:"upload", path, fmt}` → Python processes → streams back
- [ ] Renderer batches `world.setBlockData` server-side (NOT command dispatch — 10× faster)
- [ ] Worst-case guard: if message > 5MB, reject and ask user to downsample

**Exit criteria:** in-game `/upload C:\data\iris.csv csv` populates the world live.

### Phase 4 — Interactivity: hover lines + conduits (10:00 → 13:00) — **3h**
**Goal:** the "fly-through debugging" payoff.

- [ ] Plugin tick task: raytrace player look (`getTargetBlockExact`, range 50)
- [ ] If target is a data block → spawn `dust` particle line to its cluster mean (conduit) every 5 ticks
- [ ] If target is a conduit → particle line conduit → global centroid (different color)
- [ ] Throttle: max 1 line redraw per 250ms per player
- [ ] Edge case: target null, target outside loaded scene, conduit removed → silent skip

**Exit criteria:** hovering blocks shows the cluster topology visually.

### Phase 5 — Gemini layer (13:00 → 17:00) — **4h**
**Goal:** `/overview` and `/visualize <query>` work end-to-end.

- [ ] `/overview`: Python sends compressed dataset summary (cluster count, means, top features, outlier %) → Gemini → 2-paragraph reply → plugin shows via `Bukkit.broadcastMessage` chunked at 256 chars
- [ ] `/visualize <query>`: Python prompts Gemini with **a strict tool spec** — output JSON array of allowed actions only:
  ```
  [{"action":"recolor","selector":"cluster_id=0","block":"minecraft:blue_concrete"},
   {"action":"highlight","selector":"distance_from_mean>0.7","particle":"flame"},
   {"action":"hide","selector":"cluster_id=2"}]
  ```
- [ ] **Never** let Gemini emit raw `/` commands — it WILL hallucinate griefing. Validate against an allow-list, then plugin translates.
- [ ] Cache last 5 Gemini responses to dodge rate limits during demo.

**Exit criteria:** `/visualize "color points by distance from their mean, red near, blue far"` works.

### Phase 6 — Polish + demo script (17:00 → 21:00) — **4h**
- [ ] Pick 3 demo datasets: Iris (clean), MNIST-1k subset (dense), a graph CSV (edges) — confirm each renders in <15s
- [ ] Add `/reset` (clear blocks in bounds, kill conduits & markers)
- [ ] Add `/center` to teleport player to global centroid for camera shots
- [ ] Record OBS screen capture of the flythrough
- [ ] Write the 90-second pitch

### Phase 7 — Buffer + worst-case (21:00 → 24:00) — **3h**
**Reserved for the thing that WILL break.** Do not pre-spend.

---

## 5. Worst-case playbook (pre-decided, no panic)

| Failure | Detection | Fallback |
|---|---|---|
| Gemini quota / outage | API 429 or timeout | Local heuristic: cluster names = `cluster_<id>`, `/visualize` parses regex grammar (`color by <field>`) |
| WebSocket fails to bind | Plugin reconnect loop exceeds 5 tries | File-watch on `out/scene.json` — Python writes, plugin polls every 2s |
| `/setblock` floods server (TPS < 5) | `mspt` in console | Use `world.setBlockData(..., false)` (no physics/light updates), chunk into 200/tick |
| UMAP install fails on Windows | `pip install umap-learn` errors on `numba` | Drop to PCA-3 only |
| HDBSCAN wheel missing | Import error | Hard-default to KMeans++ |
| Dataset > 10k rows | Row count at ingest | Random-sample to 2000, warn in chat |
| Player loads chunk before render finishes | Holes in cluster | Pre-load chunks via `world.getChunkAt(...).load()` for bounds before streaming |
| Gemini returns invalid JSON | JSON parse fails | Retry once with "reply with ONLY valid JSON" prefix; on 2nd fail, surface raw text and abort |
| Particle lag with many lines | Client FPS drop | Cap to 1 active line; widen tick interval to 10 |
| Conduit water source quirk | Visual gaps around it | Use `minecraft:beacon` or glowing armor stand instead |

---

## 6. Repository layout

```
stack_unknown_project/
├── README.md
├── docs/
│   └── scene_graph.schema.json
├── samples/
│   ├── iris.csv
│   └── iris_mock.json
├── engine/                     # Python
│   ├── __main__.py
│   ├── ingest.py
│   ├── layout.py
│   ├── cluster.py
│   ├── outliers.py
│   ├── scene.py
│   ├── gemini.py
│   └── ws_server.py
├── plugin/                     # Java / Maven
│   ├── pom.xml
│   └── src/main/java/com/unknown/stack/
│       ├── StackPlugin.java
│       ├── commands/{Upload,Visualize,Overview,Reset,Center}.java
│       ├── net/WsClient.java
│       ├── render/SceneRenderer.java
│       └── interact/HoverLineTask.java
└── server/                     # Paper runtime (gitignored except plugins/)
```

---

## 7. Your checklist — things only you can do

Before coding:
- [ ] Get a **Gemini API key** from Google AI Studio, paste into `.env`
- [ ] Install **JDK 17** and put `java` on PATH (`java -version` → 17.x)
- [ ] Install **Python 3.11**, not 3.12 (`hdbscan`/`numba` wheels lag)
- [ ] Pick a **Minecraft Java account** for testing (or use offline mode)
- [ ] Download **PaperMC 1.20.4** jar manually (auto-update will bite you)
- [ ] Decide: WebSocket (default) or ZeroMQ (only if you've used jeromq before)

During build:
- [ ] After Phase 1, **commit and tag**. After every phase, commit.
- [ ] Keep `out/last_scene.json` written on every run — replay without re-calling Gemini
- [ ] Test Phase 3 transport with the Phase 1 mock JSON before plugging in the math engine
- [ ] Record demo footage *as you go* — don't leave it for hour 23

For the demo:
- [ ] 3 datasets pre-loaded on disk with absolute paths in a sticky note
- [ ] OBS scene with MC fullscreen + chat readable
- [ ] Pre-write the 3 `/visualize` queries you'll show — don't improvise
- [ ] Backup video in case the live demo crashes

---

## 8. Stretch (only if you finish early — do not start before hour 20)
- Edge rendering for graph datasets (particle lines between connected points)
- Save/load named scenes (`/scene save iris-good`)
- Multi-player: a second spectator sees the same world
- Export flythrough as `.mp4` via server-side replay mod

---

## 9. Definition of done (judging table)

1. Empty void world, player in spectator.
2. `/upload C:\demo\iris.csv csv` → cluster cloud appears in < 15s.
3. Fly into a cluster, hover a block → line to its conduit (mean).
4. Hover the conduit → line to global centroid.
5. `/overview` → Gemini explains the dataset in chat.
6. `/visualize "highlight outliers in flame particles"` → it happens.
7. `/reset` → clean world, ready for next dataset.

If all 7 work, you ship. Everything else is gravy.
