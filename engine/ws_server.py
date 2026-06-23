"""WebSocket bridge: plugin sends {cmd: upload, path, fmt}, we send back the scene.

Run with: python -m engine.ws_server  (auto-bootstraps under .venv if needed).
"""
from __future__ import annotations

import asyncio
import json
import os
import subprocess
import sys
import traceback
from pathlib import Path


def _reexec_under_venv_if_needed() -> None:
    venv_py = Path(__file__).resolve().parent / ".venv" / "Scripts" / "python.exe"
    if not venv_py.exists():
        return
    try:
        if Path(sys.executable).resolve() == venv_py.resolve():
            return
    except OSError:
        return
    try:
        import websockets  # noqa: F401
        import sklearn  # noqa: F401
    except ImportError:
        argv = [str(venv_py), "-m", "engine.ws_server", *sys.argv[1:]]
        sys.exit(subprocess.call(argv, env=os.environ.copy()))


_reexec_under_venv_if_needed()

import websockets  # noqa: E402

from . import cluster, gemini, ingest, layout, outliers, scene  # noqa: E402

HOST = "0.0.0.0"
PORT = 8765
MAX_PAYLOAD_BYTES = 5_000_000

_last_scene: dict | None = None


def _build_scene(path: str) -> dict:
    X, feats, name = ingest.load(path)
    labels, centroids_nd, _k = cluster.auto_kmeans(X)
    coords_3d, centroids_3d, _method = layout.project_3d(X, centroids_nd)
    outl = outliers.score(X)
    return scene.build_scene(name, feats, X, coords_3d, labels, centroids_3d, outl)


async def _handle_upload(ws, msg: dict) -> None:
    global _last_scene
    path = msg.get("path")
    fmt = msg.get("fmt", "auto")
    if not path:
        await ws.send(json.dumps({"type": "error", "message": "missing 'path'"}))
        return

    print(f"[ws_server] upload path={path} fmt={fmt}")
    try:
        sg = await asyncio.to_thread(_build_scene, path)
    except FileNotFoundError as e:
        await ws.send(json.dumps({"type": "error", "message": str(e)}))
        return
    except Exception as e:
        traceback.print_exc()
        await ws.send(json.dumps({"type": "error", "message": f"pipeline failed: {e}"}))
        return

    payload = json.dumps({"type": "scene", "scene": sg})
    if len(payload) > MAX_PAYLOAD_BYTES:
        await ws.send(json.dumps({
            "type": "error",
            "message": f"scene payload {len(payload)} > {MAX_PAYLOAD_BYTES} bytes; downsample",
        }))
        return

    _last_scene = sg
    await ws.send(payload)
    print(f"[ws_server] sent scene name={sg['dataset']['name']} "
          f"points={len(sg['points'])} clusters={len(sg['clusters'])} bytes={len(payload)}")


async def _handle_overview(ws) -> None:
    if _last_scene is None:
        await ws.send(json.dumps({"type": "error",
                                  "message": "no scene loaded; /upload first"}))
        return
    try:
        text = await asyncio.to_thread(gemini.overview, _last_scene)
    except Exception as e:
        traceback.print_exc()
        await ws.send(json.dumps({"type": "error",
                                  "message": f"overview failed: {e}"}))
        return
    await ws.send(json.dumps({"type": "overview", "text": text}))
    print(f"[ws_server] overview sent ({len(text)} chars)")


async def _handle_query(ws, msg: dict) -> None:
    if _last_scene is None:
        await ws.send(json.dumps({"type": "error",
                                  "message": "no scene loaded; /upload first"}))
        return
    question = (msg.get("query") or "").strip()
    if not question:
        await ws.send(json.dumps({"type": "error",
                                  "message": "missing query"}))
        return
    try:
        text = await asyncio.to_thread(gemini.query, _last_scene, question)
    except Exception as e:
        traceback.print_exc()
        await ws.send(json.dumps({"type": "error",
                                  "message": f"query failed: {e}"}))
        return
    await ws.send(json.dumps({"type": "query_answer",
                              "query": question,
                              "text": text}))
    print(f"[ws_server] query {question!r} -> {len(text)} chars")


async def _handle_visualize(ws, msg: dict) -> None:
    if _last_scene is None:
        await ws.send(json.dumps({"type": "error",
                                  "message": "no scene loaded; /upload first"}))
        return
    query = (msg.get("query") or "").strip()
    if not query:
        await ws.send(json.dumps({"type": "error",
                                  "message": "missing query"}))
        return
    try:
        result = await asyncio.to_thread(gemini.visualize, _last_scene, query)
    except Exception as e:
        traceback.print_exc()
        await ws.send(json.dumps({"type": "error",
                                  "message": f"visualize failed: {e}"}))
        return
    await ws.send(json.dumps({"type": "actions",
                              "query": query,
                              "actions": result["actions"],
                              "source": result["source"]}))
    print(f"[ws_server] visualize query={query!r} -> {len(result['actions'])} actions "
          f"(source={result['source']})")


async def handle(ws):
    peer = ws.remote_address
    print(f"[ws_server] client connected {peer}")
    try:
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError as e:
                await ws.send(json.dumps({"type": "error", "message": f"invalid JSON: {e}"}))
                continue
            cmd = msg.get("cmd")
            if cmd == "upload":
                await _handle_upload(ws, msg)
            elif cmd == "overview":
                await _handle_overview(ws)
            elif cmd == "visualize":
                await _handle_visualize(ws, msg)
            elif cmd == "query":
                await _handle_query(ws, msg)
            elif cmd == "gemini_info":
                await ws.send(json.dumps({"type": "gemini_info",
                                          "info": gemini.info()}))
            elif cmd == "ping":
                await ws.send(json.dumps({"type": "pong"}))
            else:
                await ws.send(json.dumps({"type": "error", "message": f"unknown cmd: {cmd!r}"}))
    except websockets.ConnectionClosed:
        pass
    finally:
        print(f"[ws_server] client disconnected {peer}")


async def main() -> None:
    print(f"[ws_server] listening on ws://{HOST}:{PORT}")
    async with websockets.serve(handle, HOST, PORT, max_size=10_000_000):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[ws_server] shutdown")
