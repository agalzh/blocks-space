# Phase 0 — Completion notes

**Tag:** `phase-0-done` (`git show phase-0-done`)
**Date:** 2026-06-21

## Exit criteria ✅
1. **Server boots** — Paper 1.20.4 running in Docker container `stack-unknown-mc` on `localhost:25565`, "Done (6.8s)!" reached.
2. **Plugin loads with `/ping`** — `StackUnknown v0.1.0` enabled cleanly; `rcon-cli ping` returns `stack-unknown pong`.
3. **Python prints a Gemini reply** — `engine/smoke_gemini.py` returns `STACK_UNKNOWN_OK` via `gemini-2.5-flash-lite`.

## How to run / stop
```cmd
:: from project root
docker compose up -d         REM start server (or server\run.cmd)
docker compose logs -f mc    REM tail logs
docker compose down          REM stop (or server\stop.cmd)
```
Connect from Minecraft Java 1.20.4 client to `localhost:25565`.

## Why Docker (the workaround we adopted)
**Native Windows boot is blocked by a Windows 11 24H2 (build 26100) regression in `afunix.sys`.** Since JDK 17, the JVM's `Selector.open()` uses an unnamed AF_UNIX socket pair internally; `connect()` on that pair returns `EINVAL`. Reproduces in 10 lines (`tools/UdsTest.java`). TCP loopback works (`tools/TcpTest.java`). Tried JDK 17 / 21 / 25, `-Djava.net.preferIPv4Stack=true`, `-Djdk.nio.usePollSelector=true`, custom `java.io.tmpdir`, Defender exclusions, Tamper Protection off — none worked.

Docker bypasses this entirely: Linux containers use the `epoll` channel type (visible in our logs: `Using epoll channel type`), which goes through `socketpair()` syscalls rather than `afunix.sys`. Windows networking just port-forwards 25565 in/out of WSL2.

## Stack as of phase-0-done
- `engine/.venv/` — Python 3.11 + numpy, pandas, sklearn, umap-learn, hdbscan, google-generativeai, websockets, dotenv
- `plugin/` — Maven build → `stack-unknown-0.1.0.jar` (210 KB, Java-WebSocket shaded under `com.unknown.stack.shaded.javawebsocket`)
- `server/` — `paper.jar` (kept for reference; runtime uses container), `server.properties`, `eula.txt`, `plugins/` mounted into container
- `docker-compose.yml` — itzg/minecraft-server:java17 with PAPER 1.20.4, void flat world, creative, offline, RCON for plugin testing
- `tools/` — local Maven 3.9.16, JDK 17, JDK 21 (gitignored)

## Quickstart for next session
```cmd
call setenv.cmd
engine\.venv\Scripts\python.exe engine\smoke_gemini.py
tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml package
copy /Y plugin\target\stack-unknown-0.1.0.jar server\plugins\
docker compose up -d
docker exec stack-unknown-mc rcon-cli ping
```
