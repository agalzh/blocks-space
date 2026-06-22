## 1. METADATA
- **Timestamp:** 2026-06-22 (early morning, ~01:40 local; session continued from 2026-06-21 handover)
- **Project root:** `C:\Users\Nikil PS\blocks_space`
- **Repo branch:** `master` (main branch on remote is `main`, no remote configured)
- **Active tag:** `phase-3-done` at commit `2caeacd`
- **Recent commits (newest first):**
  - `2caeacd` phase 3: WebSocket transport for /upload
  - `f5017e7` ops + engine venv shim
  - `755e960` phase 2: python math engine -> Scene Graph JSON
  - `ffa1c42` phase 1: scene graph schema + /loadmock renderer
  - `30b441a` handover updates
  - `6614f22` phase 0: switch server to docker (workaround Win 24H2 AF_UNIX bug); /ping verified
- **Tags so far:** `phase-0-done`, `phase-1-done`, `phase-2-done`, `phase-3-done`.
- **Platform:** Windows 11 24H2 build 26100. PowerShell 5.1 is the user's primary shell; cmd.exe also used. Watch for shell-specific syntax (`Copy-Item -Force` vs `copy /Y`).
- **Container:** `stack-unknown-mc` running Paper 1.20.4 via `itzg/minecraft-server:java17`. Compose: `./samples:/data/samples:ro` mount + `extra_hosts: host.docker.internal:host-gateway`.
- **User:** SastraISM <sastraism.edge@gmail.com>. MC username in offline mode: `Knightbones8283`. Hackathon (24h solo), pragmatic over purist.

## 2. OVERALL GOAL
Build "Stack Unknown" — Minecraft 1.20.4 as a 3D data viewer. Pipeline:
`CSV/JSON → Python math engine → Scene Graph JSON → WebSocket → Paper plugin → voxel render + hover-particle lines → Gemini for /overview & /visualize`.
README at project root has the locked 8-phase plan. Phases 0–3 complete; Phase 4 (interactivity) is next.

## 3. COMPLETED TASKS

### Phase 1 — Scene Graph contract + dummy renderer (tag `phase-1-done`, commit `ffa1c42`)
- `docs/scene_graph.schema.json` — JSON Schema Draft 2020-12 for the README §3 contract (`session_id`, `dataset`, `bounds`, `global_centroid`, `clusters[]`, `points[]`, optional `commands[]`). Hard cap 2000 points per scene. Block IDs constrained to `minecraft:[a-z0-9_]+`.
- `samples/iris_mock.json` — 10 points / 2 clusters, schema-valid.
- Plugin: added `org.json:json:20240303` to `plugin/pom.xml`, shaded under `com.unknown.stack.shaded.json`. Registered `/loadmock` in `plugin.yml` (op-only). `LoadMockCommand` reads a JSON path (relative resolves under `/data/samples/`, absolute paths supported), places point blocks via `World#setBlockData(material, false)` and a BEACON at each cluster mean (worst-case playbook for conduit).
- `docker-compose.yml`: bind-mount `./samples` read-only into `/data/samples`.

### Phase 2 — Python math engine (tag `phase-2-done`, commit `755e960`)
- `engine/` is now a package. CLI: `python -m engine <path> [--demo] [--out out/scene.json] [--seed N]`.
- `engine/ingest.py` — CSV / JSON loader. Numeric coercion, NaN drop. Strips columns that aren't numeric.
- `engine/cluster.py` — KMeans++ with silhouette-based auto-k in [2..8]. Falls back to k=2 for <6 rows.
- `engine/layout.py` — StandardScaler + UMAP-3 primary, PCA-3 fallback. Joint embedding via concat-fit-split (centroids share the manifold). PCA caps at `min(3, n_features, n_samples-1)` and pads to 3D.
- `engine/outliers.py` — IsolationForest, raw scores rescaled to [0,1] over the batch.
- `engine/scene.py` — affine rescale into bounds (X/Z 0–128, Y 4–60), palette assignment (8-color concrete cycle), points with `outlier_score > 0.7` get `minecraft:glass`. Builds the schema-valid dict.
- `engine/__main__.py` — CLI entry with `--demo` (synthetic make_blobs 1000×15 / 6 centers, mirroring the user's attached `pipeline_engine.py`). Mirrors output to `out/last_scene.json` every run.
- `samples/iris.csv` — features-only dump of `sklearn.datasets.load_iris` (150×4; no `species` cheat column).
- `engine/requirements.txt` — added `jsonschema`.
- `engine/__main__.py` has a **venv self-bootstrap**: if invoked under a Python without sklearn and `engine/.venv/Scripts/python.exe` exists, it re-execs the same `-m engine ...` invocation under the venv interpreter. Lets bare `python -m engine` work.

### Phase 3 — WebSocket transport (tag `phase-3-done`, commit `2caeacd`)
- `engine/ws_server.py` — `websockets.serve` on `0.0.0.0:8765`. Same pipeline as Phase 2 via `asyncio.to_thread`. 5 MB payload guard. Same venv self-bootstrap.
- `plugin/src/main/java/com/unknown/stack/render/SceneRenderer.java` — extracted shared renderer (parses scene, places point blocks + beacons, returns `Result{pointsPlaced, means, skipped, datasetName}`). `LoadMockCommand` now delegates to it.
- `plugin/.../net/WsClient.java` — Java-WebSocket client. Async connect on enable. Exponential backoff reconnect (2 → 4 → 8 → 16 → 30s cap). Bounces scene render onto the Bukkit main thread via `getScheduler().runTask`. Tracks pending uploaders for chat feedback.
- `plugin/.../commands/UploadCommand.java` — `/upload <abs-path> [csv|json]`. Rejoins args so paths with spaces survive MC's chat tokenizer. Trailing `csv`/`json` token treated as the format.
- `StackPlugin.java` — registers `/loadmock`, `/upload`; starts `WsClient` async on enable, calls `shutdown()` on disable. WS URL = `ws://host.docker.internal:8765` (override via `STACK_WS_URL` env).
- `plugin.yml` — `/upload` op-only.
- `docker-compose.yml` — `extra_hosts: ["host.docker.internal:host-gateway"]` so the container can reach Python on the host.
- `.gitignore` — `samples/*_scene.json` (generated artifacts).
- `executables.md` — full command reference (setup, server lifecycle, engine, render, verification, world reset, plugin build, git, troubleshooting). PowerShell-default with cmd notes.

### Side fixes / housekeeping in this session
- **Spawn platform fix.** User reported void-fall death on join. RCON: `fill -3 64 -3 3 64 3 minecraft:glass`, `setworldspawn 0 65 0`, `gamerule doImmediateRespawn true`. Lives in the `mc-data` docker volume, not in the repo — survives container restart but not `docker compose down -v`.
- **OPS list.** Earlier diff added `Knightbones8283` to `OPS` in compose; user reverted it back to `OPS: "demo"` (uncommitted at time of phase-3 commit, then captured back in `f5017e7` — see commit history). If next session needs to op the user live: `docker exec stack-unknown-mc rcon-cli "op Knightbones8283"`.

## 4. CURRENT STATE & BLOCKERS

### Working tree
- Clean after `phase-3-done` commit `2caeacd`.
- `samples/iris_scene.json` and `samples/demo_scene.json` may still exist locally but are gitignored (`samples/*_scene.json`).

### Runtime state
- **Container is RUNNING** (Phase 3 verified `WS connected: ws://host.docker.internal:8765` and `ws scene OK name=iris points=150 means=2 skipped=0`).
- **Python WS server is NOT running.** It was killed at end of session. To re-enable `/upload`, start it: `.\engine\.venv\Scripts\python.exe -m engine.ws_server`.
- **Stale blocks accumulate.** Both `/loadmock` and `/upload` only place blocks; they never clear. Multiple scenes overlap visually.

### KNOWN BLOCKER (user-flagged) — `/reset` does not work
- The user tried `docker exec stack-unknown-mc rcon-cli "fill 0 4 0 128 60 128 minecraft:air"` and got `Too many blocks in the specified area (maximum 32768, specified 948537)`.
- The scene-graph bounds (`128×56×128 = ~948k blocks`) exceed MC's `/fill` 32768 hard cap.
- No `/reset` plugin command exists yet. README §4 Phase 6 schedules one, but it should be implemented sooner because every render pollutes the world.
- Right fix: add a `ResetCommand` to the plugin that uses `world.setBlockData(...)` to clear bounds in chunks (e.g., 16×16×16 sub-volumes per tick). MC's command-layer `/fill` cap doesn't apply to Bukkit's API.
- Workaround until then: a sequence of small `/fill` commands (each ≤ 32768 blocks). Example for a 16-block-tall slice: `/fill 0 4 0 128 19 128 minecraft:air` repeated four times for Y∈{4..19, 20..35, 36..51, 52..60}.

### No failing tests; no pending unit tests. Phase 4 work has not begun.

## 5. FAILED APPROACHES (do not repeat)

### Build / packaging
- **`mvn package` (incremental) sometimes skips source recompilation** — the shaded jar contained new deps but stale `StackPlugin.class`, and the deployed container kept logging the old "phase 0" message. **Always run `mvn clean package` after editing `.java` files** until proven otherwise.
- **Importing shaded paths in source** (e.g., `com.unknown.stack.shaded.javawebsocket.client.WebSocketClient`) fails at compile time — shading happens in the `package` phase, not `compile`. Import `org.java_websocket.*` and let the shade plugin rewrite.
- **`import java.util.concurrent.Map;`** — `Map` lives in `java.util`. (Compile error during first WsClient build.)

### Python environment
- **System `python` (3.13) doesn't have project deps** — `ModuleNotFoundError: No module named 'sklearn'`. Fixed by the venv-shim at the top of `engine/__main__.py` and `engine/ws_server.py`. Don't undo. If you add another `python -m engine.something` entrypoint, add the same shim to it.

### Shell mismatches
- **PowerShell:** `copy /Y src dst` → `Copy-Item` complains "A positional parameter cannot be found that accepts argument". Use `Copy-Item -Force`.
- **cmd.exe:** `Copy-Item -Force ...` → `'Copy-Item' is not recognized`. Use `copy /Y`.
- Always check the prompt prefix (`PS C:\...>` vs `C:\...>`) before suggesting commands.

### MC / Paper specifics
- **`/fill 0 4 0 128 60 128 minecraft:air`** — exceeds 32768-block cap. See §4 blocker.
- **Iris with `species` column in CSV** — silhouette KMeans cheats on the label. Dump features-only (already fixed in `samples/iris.csv`).
- **Silhouette picks k=2 for Iris, not k=3.** That's the canonical unsupervised result (setosa vs versicolor+virginica). Not a bug; README's "3 clusters" is ground-truth-aware.

### Carry-forward from previous session
- **Native Windows Paper boot** — broken by Windows 11 24H2 `afunix.sys` EINVAL on `Selector.open()` for any JDK 17+. DO NOT retry. Docker is the canonical runtime.

### Networking
- **127.0.0.1-only WS bind** on the host would not be reachable from inside Docker. We bind `0.0.0.0:8765` and require `extra_hosts: host.docker.internal:host-gateway` in compose. Both must stay.

## 6. NEXT SPECIFIC STEPS

### Priority A (small but blocking UX) — add `/reset` and wire it before Phase 4
1. Create `plugin/src/main/java/com/unknown/stack/commands/ResetCommand.java`. Iterate bounds in 16-wide slabs, call `world.getBlockAt(x,y,z).setType(Material.AIR, false)`. To avoid stalling the server thread on ~950k operations, schedule slabs across ticks via `Bukkit.getScheduler().runTaskTimer`. Defaults: bounds = scene bounds last seen by `SceneRenderer` (cache them on render), or hard-default to `[0,4,0]..[128,60,128]`.
2. Register `/reset` in `plugin/src/main/resources/plugin.yml` (op-only, `stackunknown.reset`).
3. Wire executor in `StackPlugin.onEnable`.
4. Also re-place the spawn platform after a reset (optional): `world.getBlockAt(x,64,z).setType(Material.GLASS)` for `x,z ∈ [-3..3]`.
5. Build: `mvn clean package`, copy jar, `docker compose restart mc`.

### Priority B (Phase 4 per README §4) — hover lines + conduits
1. Create `plugin/src/main/java/com/unknown/stack/interact/HoverLineTask.java`. `BukkitRunnable` running every 5 ticks per online player.
2. `Player#getTargetBlockExact(50)` → if the block is a known data block, spawn `Particle.DUST` line from block → its cluster's beacon. Read cluster mapping from a `SceneRegistry` (need to introduce — store `Map<BlockVector, Cluster>` populated when `SceneRenderer.render` runs).
3. If target is a beacon → particle line beacon → global centroid (different colour, e.g., `Particle.END_ROD`).
4. Throttle: max 1 redraw per 250ms per player. Edge cases: target null, target outside scene, scene cleared → silent skip.
5. README exit criterion: "hovering blocks shows the cluster topology visually."
6. After working: commit + tag `phase-4-done`.

### Files to edit FIRST
1. `plugin/src/main/java/com/unknown/stack/render/SceneRenderer.java` — extend `Result` (or add a `SceneRegistry`) to remember the rendered scene so hover lines + /reset can use it.
2. `plugin/src/main/java/com/unknown/stack/commands/ResetCommand.java` (new).
3. `plugin/src/main/java/com/unknown/stack/interact/HoverLineTask.java` (new).
4. `plugin/src/main/resources/plugin.yml` (add `/reset`, declare permission).
5. `plugin/src/main/java/com/unknown/stack/StackPlugin.java` (register `/reset`, start HoverLineTask in onEnable).

### Build + redeploy
```powershell
.\setenv.cmd
.\tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml clean package
Copy-Item -Force plugin\target\stack-unknown-0.1.0.jar server\plugins\
docker compose restart mc
```

### Smoke after restart
```powershell
# Terminal 1
.\engine\.venv\Scripts\python.exe -m engine.ws_server

# Terminal 2
docker exec stack-unknown-mc rcon-cli "upload C:\Users\Nikil PS\blocks_space\samples\iris.csv csv"
docker exec stack-unknown-mc rcon-cli "reset"     # expect bounds cleared in a few ticks
```
Then join `localhost:25565`, fly to a data block, look at it — particle line should ap
s/scene_graph.schema.json` (does not exist yet — create it).
