# Executables — every command you need to run Stack Unknown

Default shell: **PowerShell**. Where cmd.exe differs, the cmd form follows on the next line.

---

## 1. First-time setup (one-off, already done if you're past Phase 0)

```powershell
# Python venv + deps (Python 3.11 required, not 3.13)
py -3.11 -m venv engine\.venv
.\engine\.venv\Scripts\python.exe -m pip install --upgrade pip
.\engine\.venv\Scripts\python.exe -m pip install -r engine\requirements.txt

# Toolchain (already in tools\, gitignored)
.\setenv.cmd                       # sets JAVA_HOME + MAVEN_HOME for the shell

# Docker Desktop must be running before any docker command
docker info                        # should print server info, not an error
```

---

## 2. Server lifecycle (daily)

```powershell
docker compose up -d               # start (idempotent)
docker compose logs -f mc          # tail logs (Ctrl+C to detach)
docker compose restart mc          # bounce server only (keeps world data)
docker compose down                # stop (keeps world via mc-data volume)
docker compose down -v             # stop AND wipe world data — destructive
```

Helper scripts that do the same:

```powershell
.\server\run.cmd                   # = docker compose up -d
.\server\stop.cmd                  # = docker compose down
```

Connect from Minecraft Java **1.20.4** client → `localhost:25565`.

---

## 3. Run the engine (Phase 2 — produce a Scene Graph JSON)

```powershell
# Real dataset
.\engine\.venv\Scripts\python.exe -m engine samples\iris.csv --out out\scene.json

# Synthetic 1000-point demo (6 well-separated clusters)
.\engine\.venv\Scripts\python.exe -m engine --demo --out out\scene.json

# Custom seed (reproducible)
.\engine\.venv\Scripts\python.exe -m engine samples\iris.csv --seed 7 --out out\scene.json

# Bare `python` also works — the shim re-execs under the venv automatically
python -m engine samples\iris.csv
```

Every run also mirrors output to `out\last_scene.json`.

---

## 4. Render into Minecraft

The plugin reads from `/data/samples/` **inside the container**, which is the bind-mount of `.\samples\` on the host. So: write → copy → loadmock.

```powershell
Copy-Item -Force out\scene.json samples\iris_scene.json
docker exec stack-unknown-mc rcon-cli loadmock /data/samples/iris_scene.json
```

cmd.exe form of the copy: `copy /Y out\scene.json samples\iris_scene.json`

Run loadmock from in-game chat instead of rcon (op-only):

```
/loadmock /data/samples/iris_scene.json
```

---

## 5. Verification & diagnostics

```powershell
# Confirm /ping (Phase 0 smoke)
docker exec stack-unknown-mc rcon-cli ping

# Validate any scene file against the locked schema
.\engine\.venv\Scripts\python.exe -c "import json,jsonschema; jsonschema.validate(json.load(open('out/scene.json')), json.load(open('docs/scene_graph.schema.json'))); print('OK')"

# Spot-check a coordinate has the expected block
docker exec stack-unknown-mc rcon-cli "data get block 30 30 30"

# Probe with /execute (silent unless match)
docker exec stack-unknown-mc rcon-cli "execute if block 30 30 30 minecraft:beacon run say HIT"

# Server-side: list installed plugins
docker exec stack-unknown-mc rcon-cli "plugins"

# Gemini smoke test (Phase 0)
.\engine\.venv\Scripts\python.exe engine\smoke_gemini.py
```

In-game (chat) — handy:

```
/tp @s 64 50 64           teleport above the data area
/gamemode spectator       fly through blocks
/gamemode creative        normal flight
F3                        coords + chunk overlay
```

---

## 6. Reset the world (clean slate between renders)

`/loadmock` only adds blocks — it never clears. Wipe before re-rendering:

```powershell
# Clear the entire scene-graph bounds (X 0-128, Y 4-60, Z 0-128)
docker exec stack-unknown-mc rcon-cli "fill 0 4 0 128 60 128 minecraft:air"

# Re-place the glass spawn platform (if you wipe everything)
docker exec stack-unknown-mc rcon-cli "fill -3 64 -3 3 64 3 minecraft:glass"
docker exec stack-unknown-mc rcon-cli "setworldspawn 0 65 0"
```

Op a new player at runtime (without recreating the container):

```powershell
docker exec stack-unknown-mc rcon-cli "op <minecraft-username>"
```

---

## 7. Build the plugin (when you change Java code)

```powershell
.\setenv.cmd
.\tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml clean package
Copy-Item -Force plugin\target\stack-unknown-0.1.0.jar server\plugins\
docker compose restart mc
```

Always use `clean package`, not `package` alone — incremental builds have skipped recompiles in this project once already.

---

## 8. Git workflow (per phase)

```powershell
git status
git diff
git add <files>
git commit -m "phase N: <one-line summary>"
git tag phase-N-done                # only after exit criteria pass
git log --oneline -10
git tag --list "phase-*"
```

Tags so far: `phase-0-done` → `phase-1-done` → `phase-2-done`.

---

## 9. Troubleshooting one-liners

```powershell
# Docker daemon offline → start Docker Desktop manually from Start menu
docker info

# Container won't boot → check last 80 lines of server log
docker logs --tail 80 stack-unknown-mc

# Plugin loaded the OLD code → confirm jar contents
& "$env:JAVA_HOME\bin\jar.exe" tf plugin\target\stack-unknown-0.1.0.jar | Select-String "com/unknown/stack/"

# Verify the deployed jar matches the build
docker exec stack-unknown-mc sh -c "ls -la /data/plugins/*.jar"

# Python module not found → likely using system Python 3.13 not venv 3.11
where.exe python
.\engine\.venv\Scripts\python.exe --version

# Scene JSON looks wrong → re-validate against schema (see §5)

# Native Paper boot crash → DO NOT debug; use Docker (Win 24H2 AF_UNIX bug)
```

---

## 10. Quick "I want to see something now" recipe

```powershell
docker compose up -d
.\engine\.venv\Scripts\python.exe -m engine --demo --out out\scene.json
Copy-Item -Force out\scene.json samples\demo_scene.json
docker exec stack-unknown-mc rcon-cli "fill 0 4 0 128 60 128 minecraft:air"
docker exec stack-unknown-mc rcon-cli loadmock /data/samples/demo_scene.json
```

Then join `localhost:25565` and `/tp @s 64 50 64`.
