## 1. METADATA
- **Timestamp:** 2026-06-21 (end of session, ~22:35 local)
- **Project root:** `C:\Users\Nikil PS\blocks_space`
- **Repo branch:** `master` (main branch on remote is `main`, but no remote configured yet)
- **Active tag:** `phase-0-done` at commit `6614f22`
- **Recent commits (newest first):**
  - `6614f22` phase 0: switch server to docker (workaround Win 24H2 AF_UNIX bug); /ping verified
  - `e9d81d0` docs: phase 0 completion notes + AF_UNIX blocker carry-forward
  - `893abc4` Phase 0: setup complete (engine venv, plugin scaffold, server config)
- **Platform:** Windows 11 24H2, build 26100. PowerShell 5.1 + Git Bash both available.
- **User:** SastraISM <sastraism.edge@gmail.com>. Hackathon context (24h solo build); prefers pragmatic workarounds over README purism.

## 2. OVERALL GOAL
Build "Stack Unknown" — a 24-hour hackathon project that uses Minecraft as a 3D data viewer. Pipeline:
`CSV/JSON → Python math engine (NumPy/sklearn/UMAP/HDBSCAN) → Scene Graph JSON → WebSocket → Paper 1.20.4 plugin → voxel rendering with hover-particle lines → Gemini for /overview and /visualize NL queries`.
README at project root has the full 8-phase plan (Phase 0–7). This session completed **Phase 0 only**.

## 3. COMPLETED TASKS (Phase 0)
- **Project scaffold:** `engine/`, `plugin/`, `server/`, `samples/`, `docs/`, `out/`, `tools/`, `.gitignore`, `.env` (Gemini key).
- **Python 3.11 venv** at `engine/.venv/` with: numpy, pandas, scikit-learn, umap-learn, hdbscan, google-generativeai, websockets, python-dotenv. `engine/requirements.txt` pinned.
- **Gemini smoke test** at `engine/smoke_gemini.py` — returns `STACK_UNKNOWN_OK` using model **`gemini-2.5-flash-lite`** (not gemini-2.0-flash; that hit free-tier daily quota for this key).
- **Local toolchain** in `tools/` (gitignored): Maven 3.9.16, Temurin JDK 17.0.19+10, Temurin JDK 21.0.11+10. `setenv.cmd` at repo root sets PATH/JAVA_HOME for a shell.
- **Maven plugin** scaffold at `plugin/` (groupId `com.unknown.stack`, artifact `stack-unknown`, version `0.1.0`). Builds to `plugin/target/stack-unknown-0.1.0.jar` (210 KB, Java-WebSocket shaded under `com.unknown.stack.shaded.javawebsocket`). Compiled with `--release 17` for Paper 1.20.4. `/ping` command registered in `plugin.yml` and handled in `StackPlugin.java` — returns `§astack-unknown pong (phase 0 — plugin loaded OK)`.
- **Server runtime via Docker** (workaround — see §5). `docker-compose.yml` runs `itzg/minecraft-server:java17` with `TYPE=PAPER`, `VERSION=1.20.4`, void flat world, creative, offline mode, RCON exposed. Plugin bind-mounted from `server/plugins/`. Container name `stack-unknown-mc`, port `25565` published to host. Helper scripts: `server/run.cmd`, `server/stop.cmd`.
- **End-to-end verified:** Paper boots in 6.8s, plugin enables (`[StackUnknown] Enabling StackUnknown v0.1.0`), `docker exec stack-unknown-mc rcon-cli ping` returns the pong message.
- **Git tagged** `phase-0-done` at `6614f22`. README's exit criteria for Phase 0 all green.
- **Memory files updated** at `C:\Users\Nikil PS\.claude\projects\C--Users-Nikil-PS-blocks-space\memory\`:
  - `MEMORY.md` — index.
  - `feedback_dont_linger_on_blockers.md` — after 2 failed attempts on env blocker, pick a workaround.
  - `project_blocks_space.md` — project facts, Gemini model = `gemini-2.5-flash-lite`, **Docker is the canonical server runtime** (do not retry native Paper).

## 4. CURRENT STATE & BLOCKERS
- **Working tree:** clean except `server/server.properties` modified (uncommitted; was edited during Phase 0 server config experiments and is now superseded by Docker — safe to revert OR ignore, container has its own properties).
- **Active container:** `stack-unknown-mc` is currently RUNNING. `docker compose down` to stop. Volume `blocks_space_mc-data` persists world data.
- **Carried-forward blocker (does NOT block Phase 1, but worth knowing):** Native Windows Paper boot is broken due to a **Windows 11 24H2 (build 26100) kernel regression in `afunix.sys`**. Every `Selector.open()` call in JDK 17+ fails with `java.net.SocketException: Invalid argument: connect` in `UnixDomainSockets.connect0`. We adopted Docker as the workaround — do not try to re-fix native boot. Reproducer is at `tools/UdsTest.java`; TCP loopback works (`tools/TcpTest.java`).
- **No tests are failing.** No pending tests.
- **Files modified (uncommitted):** `server/server.properties` (delta from earlier session work — see git diff). Decision left to next session: revert, or commit as-is since the file is still in the gitignore allowlist.

## 5. FAILED APPROACHES (do not repeat)
- **`winget install Apache.Maven`** — package is not in winget. Source = msstore hits a cert error; `winget` source returns no match. Use the Apache archive download already in `tools/` instead.
- **Booting Paper on native Windows with any JDK** (tried 17.0.19, 21.0.11, system Java 25.0.2). All crash with the AF_UNIX `EINVAL` because the JVM's NIO Selector/Pipe uses an unnamed UDS socket pair on Windows since JDK 17. The bug is in `afunix.sys` on this Windows build, not in any JDK or in Paper.
- **JVM flags tried — none worked:** `-Djava.net.preferIPv4Stack=true`, `-Djdk.nio.usePollSelector=true`, `-Djava.io.tmpdir=<project-local>`.
- **Windows Defender tweaks — none worked:** adding `paper.jar` + `server/` + JDK to Defender exclusions, turning off Network Protection, turning off **Tamper Protection**. The AF_UNIX connect still returns EINVAL after each.
- **`docker --version` worked, but daemon was offline.** `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` from PowerShell launched the EXE but Docker Desktop exited silently — no Docker/vpnkit/wsl processes left after a minute. User had to **start Docker Desktop manually from the Start menu** and click through first-run prompts. Once running, `docker compose up -d` worked first try. If next session sees `docker info` fail, ask the user to start Docker Desktop manually rather than spawning the EXE.

## 6. NEXT SPECIFIC STEPS (Phase 1 — Scene Graph contract + dummy renderer, 2.5h budget per README)
Per `README.md` §4 Phase 1, in order:

1. **Decide what to do with `server/server.properties`.** Inspect `git diff server/server.properties`; either `git checkout -- server/server.properties` (recommended — container has its own config) or commit it.
2. **Write the JSON schema** to `docs/scene_graph.schema.json`. Schema shape is locked in README §3 — `session_id`, `dataset`, `bounds`, `global_centroid`, `clusters[]`, `points[]`, `commands[]`. Coordinates are integers in MC world space (X: 0–128, Z: 0–128, Y: 4–60), clamped to bounds. Colors are MC block IDs (e.g., `minecraft:red_concrete`), not hex. Batch cap **2000 points per scene**.
3. **Hand-author `samples/iris_mock.json`** — 10 points, 2 clusters, conforming to the schema above.
4. **Add a `LoadMockCommand`** to the plugin under `plugin/src/main/java/com/unknown/stack/commands/`. Register `/loadmock` in `plugin.yml`. Implementation: read a JSON file path (relative to `/data` inside container, or accept absolute), parse with org.json or Jackson, iterate `points[]` doing `world.getBlockAt(x,y,z).setType(Material.matchMaterial(block))`, and `world.summonEntity` a Conduit (or `world.spawnParticle` + armor stand if Conduit needs water) at each cluster `mean`.
5. **Build + redeploy:**
   ```cmd
   call setenv.cmd
   tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml package
   copy /Y plugin\target\stack-unknown-0.1.0.jar server\plugins\
   docker compose restart mc
   ```
6. **Verify in-game:** join `localhost:25565` with Minecraft Java 1.20.4 client, `/loadmock /data/plugins/iris_mock.json` (after copying the sample into `server/plugins/` so the bind mount exposes it inside the container). Confirm 10 blocks appear, 2 conduits glow.
7. **Commit + tag `phase-1-done`** when in-game render works.

**File you will edit first:** `docs/scene_graph.schema.json` (does not exist yet — create it).
