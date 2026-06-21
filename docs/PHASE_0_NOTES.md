# Phase 0 — Completion notes

**Tag:** `phase-0-done` (`git show phase-0-done`)
**Date:** 2026-06-21

## Verified
- Python 3.11 venv at `engine/.venv/` — all deps installed (`engine/requirements.txt`).
- Gemini API key validated end-to-end. Model: **`gemini-2.5-flash-lite`** (gemini-2.0-flash and -lite both hit daily free-tier quota for this key).
- Local toolchain at `tools/` — Maven 3.9.16, Temurin JDK 17.0.19+10, Temurin JDK 21.0.11+10.
- Plugin compiles cleanly to `server/plugins/stack-unknown-0.1.0.jar` (210 KB, Java-WebSocket shaded under `com.unknown.stack.shaded.javawebsocket`).
- `/ping` command wired in `StackPlugin.java` and registered in `plugin.yml`.
- Paper jar in place, EULA accepted, `server.properties` set to spectator + flat void + offline mode.

## Known blocker (carried into Phase 1)
**Paper server cannot boot on this Windows install.**

Root cause: Windows 11 24H2 (build 26100) regression in `afunix.sys`. The JVM's `Selector.open()` uses an unnamed AF_UNIX socket pair internally since JDK 17; `connect()` on that pair returns `EINVAL`. Reproduces in 10 lines (`tools/UdsTest.java`). TCP loopback works (`tools/TcpTest.java`). Affects every Java NIO server, not just Paper. Tried JDK 17 / 21 / 25, `-Djava.net.preferIPv4Stack=true`, `-Djdk.nio.usePollSelector=true`, custom `java.io.tmpdir`, Defender process+folder exclusions — none worked.

## Workaround options for Phase 1
Pick one before starting Phase 1:
1. **Run Windows Update + reboot.** Microsoft has shipped multiple post-24H2 cumulative updates touching Winsock/AF_UNIX. Cheapest, often fixes it.
2. **Run Paper in WSL2 or Docker.** Linux uses `socketpair()` syscall, not `afunix.sys`. Plugin jar is platform-agnostic. Minecraft Java client connects to `localhost:25565` (WSL2 auto-forwards). Docker Desktop appears to be installed (docker-desktop WSL distro present).
3. **Disable Defender Tamper Protection + reboot.** Less likely to help given Network Protection is already off, but cheap to try.

## Quickstart for next session
```cmd
call setenv.cmd
engine\.venv\Scripts\python.exe engine\smoke_gemini.py
tools\apache-maven-3.9.16\bin\mvn.cmd -f plugin\pom.xml package
copy plugin\target\stack-unknown-0.1.0.jar server\plugins\
server\run.cmd   :: will crash until AF_UNIX blocker resolved
```
