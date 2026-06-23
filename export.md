# Running Stack Unknown on another machine

This is the operational setup guide. Treat it like a runbook — every step
matters and the order matters. The default platform is Windows 10/11; cross-
platform notes are at the bottom.

---

## 1. Prerequisites

You need all of these installed and on `PATH` (or available at the paths
this guide assumes).

| Tool | Version | Why |
|---|---|---|
| **Docker Desktop** | latest | Runs the Minecraft server. Must be **running** before any docker command |
| **Python** | 3.11 (NOT 3.12 / 3.13) | `umap-learn`, `numba`, `hdbscan` wheels lag behind newer Pythons |
| **Java JDK** | 17 | Plugin build target (Paper 1.20.4 requires Java 17) |
| **Maven** | 3.9+ | Plugin build |
| **Git** | any recent | clone the repo |
| **Minecraft Java Edition** | **1.20.4** exactly | Plugin is built against this API version |
| **Gemini API key** | from [Google AI Studio](https://aistudio.google.com/apikey) | Powers `/overview`, `/query`, `/visualize` (heuristic fallback works without it) |

The repo ships a bundled `tools/` directory with JDK 17 and Maven 3.9.16
for Windows; you can use those instead of system installs (see step 5).

## 2. Clone and lay out the project

```powershell
git clone <your-fork-url> stack-unknown
cd stack-unknown
```

Expected top-level layout:
```
stack-unknown/
  engine/           Python pipeline + ws_server + Gemini
  plugin/           Maven plugin source
  server/           Plugin jar drop target + docker volume
  samples/          Demo CSVs + iris_mock.json
  docs/             Scene graph JSON schema
  tools/            Bundled Windows JDK 17 + Maven (optional)
  docker-compose.yml
  setenv.cmd        Sets JAVA_HOME / MAVEN_HOME from bundled tools/
  .env              You create this; see step 3
```

## 3. Create `.env`

```
GEMINI_API_KEY=your_key_here
```

Just one line. `engine/gemini.py` reads it via `python-dotenv`. If the key
is missing or invalid, `/overview` / `/query` / `/visualize` fall back to
deterministic heuristics — you can demo without Gemini, just less
impressively.

Smoke-test the key:
```powershell
.\engine\.venv\Scripts\python.exe engine\smoke_gemini.py
# expect: Gemini reply: STACK_UNKNOWN_OK
```
(Only works after step 4.)

## 4. Python environment

```powershell
py -3.11 -m venv engine\.venv
.\engine\.venv\Scripts\python.exe -m pip install --upgrade pip
.\engine\.venv\Scripts\python.exe -m pip install -r engine\requirements.txt
```

The venv lives at `engine/.venv` and is gitignored.

If `umap-learn` install fails: it depends on `numba`, which needs a
matching `llvmlite` wheel. On Python 3.11 the wheels exist; on 3.12+ they
often don't. **Use Python 3.11.** If you absolutely must use newer
Python, the engine falls back to PCA-3 when UMAP isn't importable — but
you have to delete the `umap-learn` line from `engine/requirements.txt`
first.

Verify:
```powershell
.\engine\.venv\Scripts\python.exe -c "import numpy, sklearn, umap, websockets, google.generativeai, dotenv; print('ok')"
```

## 5. Java toolchain

**Option A — use bundled tools:**
```powershell
.\setenv.cmd
java -version       # expect 17.x
mvn -version        # expect 3.9.x
```
`setenv.cmd` is local to the current shell only; rerun in every new
terminal that needs to build the plugin.

**Option B — system Java + Maven:**
Install JDK 17 from Adoptium/Temurin and Maven 3.9+. Make sure
`java -version` reports 17.x.

## 6. Build the plugin

```powershell
.\setenv.cmd                                                          # if using bundled
.\tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml clean package
Copy-Item -Force plugin\target\stack-unknown-0.1.0.jar server\plugins\
```

`clean package` is important — incremental builds have skipped recompiles
in this project before.

The shaded jar `server/plugins/stack-unknown-0.1.0.jar` is what the
docker container loads on the next restart.

## 7. Start the Minecraft server

```powershell
docker info                  # must NOT error out
docker compose up -d
docker compose logs -f mc    # tail until you see  Done (<seconds>)!
                             # Ctrl+C to detach
```

Expected log lines after startup:
```
[StackUnknown] StackUnknown plugin enabled (phase 6+).
[StackUnknown] World 'world' frozen at night
```

If the plugin fails to load, see Troubleshooting.

## 8. Start the Python WS engine

In a separate terminal:
```powershell
.\engine\.venv\Scripts\python.exe -u -m engine.ws_server
```
Expected:
```
[ws_server] listening on ws://0.0.0.0:8765
[ws_server] client connected ('172.x.x.x', ...)
```

The `-u` flag makes prints flush live so you can see Gemini calls as they
happen.

The plugin retries on a 2 → 4 → 8 → 16 → 30 s backoff, so the engine can
start before or after the server with no manual coordination.

## 9. Connect from Minecraft

1. Launch Minecraft Java Edition **1.20.4**.
2. Multiplayer → Direct Connection → `localhost:25565`.
3. First time: op yourself via rcon so the privileged commands work:
   ```powershell
   docker exec stack-unknown-mc rcon-cli "op <your-minecraft-username>"
   ```
4. Recommended in-game:
   ```
   /gamemode spectator
   ```
   F1 hides the vanilla HUD, F5 cycles to third-person if you want a
   camera-style view.

## 10. First run

```
/upload C:\Users\Nikil PS\stack-unknown\samples\iris.csv csv
/center
/overview
/visualize highlight outliers with flame for 30 seconds
```

Use **absolute** Windows paths for `/upload`; the plugin tokenizer
re-joins arguments so paths with spaces work.

---

## Configuration knobs

| Knob | Where | Default | Notes |
|---|---|---|---|
| Gemini API key | `.env` `GEMINI_API_KEY` | — | required for Gemini features |
| Gemini model | `engine/gemini.py` `MODEL_NAME` | `gemini-2.5-flash-lite` | |
| Cache size | `engine/gemini.py` `CACHE_SIZE` | 5 | LRU eviction |
| Outlier threshold | `engine/scene.py` `OUTLIER_THRESHOLD` | 0.7 | also surfaced via SceneRegistry |
| Point cap per scene | `engine/scene.py` `POINT_CAP` | 2000 | random-samples above this |
| MC bounds | `engine/scene.py` `BOUNDS_MIN/MAX` | X/Z 0–128, Y 4–60 | scene coordinate box |
| WS URL the plugin connects to | env `STACK_WS_URL` | `ws://host.docker.internal:8765` | set on the docker container if you need to point elsewhere |
| WS server bind / port | `engine/ws_server.py` `HOST`, `PORT` | `0.0.0.0:8765` | `0.0.0.0` is required for the docker container to reach the host |
| Payload size cap | `engine/ws_server.py` `MAX_PAYLOAD_BYTES` | 5 MB | rejects with an error if exceeded |
| MC server port | `docker-compose.yml` `25565:25565` | 25565 | change if it clashes |
| Highlight/pulse/beam max | `engine/gemini.py` `DURATION_MAX` + plugin `MAX_DURATION_SEC` | 60 s | clamped on both sides |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker info` errors | Docker Desktop not running | Launch Docker Desktop, wait for the tray icon to stop animating |
| Plugin logs loop `Connection refused` | `ws_server` not started | Start step 8; the plugin reconnects on its own |
| `/upload` says "WS not connected" | engine isn't up | Same as above |
| `/upload` returns "scene payload > 5 MB" | dataset too big | Sample down before upload, or bump `MAX_PAYLOAD_BYTES` |
| `/overview` and `/query` return shorter / less interesting text | Gemini fallback mode active | Check `.env`, run `engine/smoke_gemini.py` |
| Plugin shows old behavior after a rebuild | `package` skipped a recompile | Always use `clean package`, not just `package` |
| MC client says "Outdated server / client" | wrong MC version | Use 1.20.4 exactly |
| `ModuleNotFoundError: sklearn` | running via system Python, not the venv | The shim in `engine/__main__.py` and `engine/ws_server.py` re-exec under `engine/.venv` if it exists; check the venv was created with Python 3.11 |
| Particle / TPS lag on `digits_1k.csv` | 1000 setBlock calls in one tick | Acceptable spike; if persistent, narrow scene bounds in `engine/scene.py` |
| `/center` says "/upload a dataset first" | empty registry | Run `/upload` first |
| `/axes show` says the same | requires a centroid | Same |
| Native Paper boot crash on Windows 11 24H2 | OS-level AF_UNIX regression in `afunix.sys` for JDK 17+ | **Don't run Paper natively on 24H2.** Docker is the canonical runtime. See "Platform notes" |

---

## Platform notes

### Windows 11 24H2

Native PaperMC 1.20.4 **does not boot** on Windows 11 24H2 with any JDK 17+
because of a regression in `afunix.sys` that breaks Java NIO selectors on
startup. The Docker workaround is the supported path here.

If you need to verify the bug exists, run `paper.jar` directly with
`java -jar paper.jar` and look for the `EINVAL` on `Selector.open()`.
Don't waste time debugging it; use Docker.

### Linux / macOS

Should work but is untested. Likely tweaks:

- `setenv.cmd` is Windows-only. Set `JAVA_HOME` and `MAVEN_HOME` manually
  (`export JAVA_HOME=...; export PATH=$JAVA_HOME/bin:$PATH`).
- `docker compose` paths are forward-slash already.
- `host.docker.internal` works on Docker Desktop for both macOS and
  Windows. On Linux, set `extra_hosts: ["host.docker.internal:host-gateway"]`
  in `docker-compose.yml` (already present) and confirm it resolves from
  inside the container with `docker exec stack-unknown-mc getent hosts host.docker.internal`.
- The bundled `tools/jdk-17.0.19+10` is a Windows binary. Install JDK 17
  from your package manager.

### Shell

Default is **PowerShell** on Windows. Where commands differ:

| PowerShell | cmd.exe | bash |
|---|---|---|
| `Copy-Item -Force a b` | `copy /Y a b` | `cp -f a b` |
| `Get-Process python` | `tasklist /FI "IMAGENAME eq python.exe"` | `pgrep -fa python` |
| `$env:VAR = "x"` | `set VAR=x` | `export VAR=x` |

---

## Daily lifecycle

```powershell
# Start
docker compose up -d
.\engine\.venv\Scripts\python.exe -u -m engine.ws_server   # leave running

# Stop (keeps world)
docker compose down

# Stop AND wipe world (destructive)
docker compose down -v

# Just bounce the server (keeps world, picks up new plugin jar)
docker compose restart mc

# Rebuild plugin after .java edits
.\setenv.cmd
.\tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml clean package
Copy-Item -Force plugin\target\stack-unknown-0.1.0.jar server\plugins\
docker compose restart mc
```

`ws_server` survives plugin restarts; the plugin auto-reconnects.

---

## Smoke procedure for a new install

After step 9, run all of these and confirm each works before declaring the
machine ready:

```
/ping                                              # plugin loaded
/upload <path>\samples\iris.csv csv                # scene renders, sound plays
/center                                            # TP works, looking down
/axes                                              # red/green/blue ribbon visible
look at a colored block                            # nameplate above + sidebar populated
look at a beacon                                   # white end-rod line to centroid
/overview                                          # two-paragraph chat broadcast
/query how many clusters are there                 # short chat answer
/visualize highlight outliers with flame for 5s    # flames for 5s
/reset                                             # bounds clear, spawn platform rebuilt
```

If any of these fail, the corresponding section above will tell you why.
