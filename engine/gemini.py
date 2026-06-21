"""Gemini layer for Stack Unknown.

Two entry points:
- overview(scene)            -> str  (2-paragraph natural-language summary)
- visualize(scene, query)    -> list[dict]  (validated action list)

If the Gemini call fails or returns malformed output, both functions fall back
to deterministic heuristics so the demo never wedges on a quota or 5xx.
"""
from __future__ import annotations

import json
import os
import re
from collections import OrderedDict
from pathlib import Path
from typing import Any

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

MODEL_NAME = "gemini-2.5-flash-lite"
CACHE_SIZE = 5
OUTLIER_THRESHOLD = 0.7

ALLOWED_ACTIONS = {"recolor", "highlight", "hide"}
ALLOWED_PARTICLES = {"flame", "soul_fire_flame", "heart", "happy_villager",
                     "end_rod", "dust", "crit"}
SELECTOR_RE = re.compile(
    r"^(all|cluster_id=-?\d+|outlier_score[<>]0?\.\d+)$"
)
BLOCK_ID_RE = re.compile(r"^minecraft:[a-z0-9_]+$")

_cache: "OrderedDict[tuple, Any]" = OrderedDict()
_model = None
_model_failed = False


def _get_model():
    global _model, _model_failed
    if _model is not None or _model_failed:
        return _model
    key = os.getenv("GEMINI_API_KEY")
    if not key:
        _model_failed = True
        print("[gemini] GEMINI_API_KEY missing; fallback mode")
        return None
    try:
        import google.generativeai as genai
        genai.configure(api_key=key)
        _model = genai.GenerativeModel(MODEL_NAME)
        return _model
    except Exception as e:
        _model_failed = True
        print(f"[gemini] init failed: {e}; fallback mode")
        return None


def _cache_get(key):
    if key in _cache:
        _cache.move_to_end(key)
        return _cache[key]
    return None


def _cache_put(key, value):
    _cache[key] = value
    _cache.move_to_end(key)
    while len(_cache) > CACHE_SIZE:
        _cache.popitem(last=False)


def _summarize(scene: dict) -> dict:
    ds = scene.get("dataset", {})
    clusters = scene.get("clusters", [])
    points = scene.get("points", [])
    outliers = [p for p in points if p.get("outlier_score", 0.0) > OUTLIER_THRESHOLD]
    cluster_summaries = []
    feature_names: list[str] = []
    if points and "meta" in points[0] and "original_row" in points[0]["meta"]:
        feature_names = list(points[0]["meta"]["original_row"].keys())

    for c in clusters:
        cid = c["id"]
        member_pts = [p for p in points if p["cluster_id"] == cid]
        size = len(member_pts)
        feat_means = {}
        if member_pts and feature_names:
            for fn in feature_names:
                vals = [p["meta"]["original_row"].get(fn, 0.0) for p in member_pts]
                feat_means[fn] = round(sum(vals) / len(vals), 3) if vals else 0.0
        cluster_summaries.append({
            "id": cid,
            "label": c.get("label", f"cluster_{cid}"),
            "color": c.get("color", "?"),
            "size": size,
            "mean_xyz": c.get("mean", [0, 0, 0]),
            "feature_means": feat_means,
        })

    return {
        "dataset_name": ds.get("name", "?"),
        "rows": ds.get("rows", len(points)),
        "dims": ds.get("dims", 0),
        "features": feature_names,
        "n_clusters": len(clusters),
        "outlier_count": len(outliers),
        "outlier_threshold": OUTLIER_THRESHOLD,
        "clusters": cluster_summaries,
        "global_centroid": scene.get("global_centroid", [0, 0, 0]),
    }


# ----- overview -----

_OVERVIEW_PROMPT = """You are explaining a dataset to a user who is exploring it as voxels inside Minecraft.

Write exactly two paragraphs of plain prose. No lists, no headings, no markdown.
- Paragraph 1: what the dataset contains and how many clusters are visible.
- Paragraph 2: anything that stands out (cluster shapes, dominant features, outliers).

Keep it under 600 characters total. Be concrete; refer to feature names if useful.

Dataset summary (JSON):
{summary}
"""


def overview(scene: dict) -> str:
    summary = _summarize(scene)
    key = ("overview", summary["dataset_name"], summary["rows"], summary["n_clusters"])
    cached = _cache_get(key)
    if cached is not None:
        return cached

    model = _get_model()
    if model is None:
        text = _fallback_overview(summary)
        _cache_put(key, text)
        return text

    prompt = _OVERVIEW_PROMPT.format(summary=json.dumps(summary, indent=2))
    try:
        resp = model.generate_content(prompt)
        text = (resp.text or "").strip()
        if not text:
            raise ValueError("empty response")
    except Exception as e:
        print(f"[gemini] overview call failed: {e}; fallback")
        text = _fallback_overview(summary)
    _cache_put(key, text)
    return text


def _fallback_overview(summary: dict) -> str:
    name = summary["dataset_name"]
    rows = summary["rows"]
    k = summary["n_clusters"]
    outl = summary["outlier_count"]
    feats = ", ".join(summary["features"][:4]) or "unnamed features"
    big = max(summary["clusters"], key=lambda c: c["size"]) if summary["clusters"] else None
    para1 = (
        f"Dataset '{name}' has {rows} rows across {summary['dims']} dimensions "
        f"({feats}). The engine grouped them into {k} clusters based on local density."
    )
    if big:
        para2 = (
            f"The largest cluster is #{big['id']} with {big['size']} points. "
            f"{outl} points cross the outlier threshold of "
            f"{summary['outlier_threshold']:.2f} and were rendered as glass blocks."
        )
    else:
        para2 = f"{outl} points were flagged as outliers."
    return para1 + "\n\n" + para2


# ----- visualize -----

_VISUALIZE_PROMPT = """You are a Minecraft data-viz controller. Translate the user's
natural-language query into a JSON array of actions. Return ONLY the JSON array.
No prose, no markdown fences.

Allowed actions:
- {{"action": "recolor",   "selector": "<S>", "block":    "minecraft:<id>"}}
- {{"action": "highlight", "selector": "<S>", "particle": "<P>"}}
- {{"action": "hide",      "selector": "<S>"}}

Allowed selectors (one of):
- "all"
- "cluster_id=<N>"             N must be a known cluster id
- "outlier_score>0.<dd>"       e.g. outlier_score>0.5
- "outlier_score<0.<dd>"

Allowed particles: {particles}
Block ids must look like "minecraft:something_concrete" or similar.

Known cluster ids: {cluster_ids}

Dataset summary:
{summary}

User query: {query}

Reply with the JSON array (1-4 actions). Nothing else.
"""


def visualize(scene: dict, query: str) -> dict:
    summary = _summarize(scene)
    cluster_ids = [c["id"] for c in summary["clusters"]]
    key = ("visualize", query.strip().lower(), summary["dataset_name"])
    cached = _cache_get(key)
    if cached is not None:
        return cached

    model = _get_model()
    actions: list[dict]
    source = "gemini"
    if model is None:
        actions = _fallback_visualize(query, cluster_ids)
        source = "fallback"
    else:
        prompt = _VISUALIZE_PROMPT.format(
            particles=sorted(ALLOWED_PARTICLES),
            cluster_ids=cluster_ids,
            summary=json.dumps(summary, indent=2),
            query=query,
        )
        try:
            resp = model.generate_content(prompt)
            raw = (resp.text or "").strip()
            actions = _parse_actions(raw)
        except Exception as e:
            print(f"[gemini] visualize call failed: {e}; fallback")
            actions = _fallback_visualize(query, cluster_ids)
            source = "fallback"

    validated = [a for a in actions if _validate_action(a, cluster_ids)]
    if not validated:
        # last-resort: highlight everything with flame
        validated = [{"action": "highlight", "selector": "all", "particle": "flame"}]
        source = source + "+lastresort"
    result = {"actions": validated, "source": source}
    _cache_put(key, result)
    return result


def _parse_actions(raw: str) -> list[dict]:
    text = raw.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    obj = json.loads(text)
    if isinstance(obj, dict) and "actions" in obj:
        obj = obj["actions"]
    if not isinstance(obj, list):
        return []
    return [a for a in obj if isinstance(a, dict)]


def _validate_action(a: dict, cluster_ids: list[int]) -> bool:
    action = a.get("action")
    selector = a.get("selector", "")
    if action not in ALLOWED_ACTIONS:
        return False
    if not SELECTOR_RE.match(selector or ""):
        return False
    if selector.startswith("cluster_id="):
        cid = int(selector.split("=", 1)[1])
        if cid not in cluster_ids:
            return False
    if action == "recolor":
        block = a.get("block", "")
        if not BLOCK_ID_RE.match(block):
            return False
    if action == "highlight":
        part = a.get("particle", "")
        if part not in ALLOWED_PARTICLES:
            return False
    return True


def _fallback_visualize(query: str, cluster_ids: list[int]) -> list[dict]:
    q = query.lower()
    actions: list[dict] = []
    if "outlier" in q or "anomal" in q:
        actions.append({"action": "highlight",
                        "selector": "outlier_score>0.7",
                        "particle": "flame"})
    m = re.search(r"cluster\s*(\d+)", q)
    if m and int(m.group(1)) in cluster_ids:
        actions.append({"action": "highlight",
                        "selector": f"cluster_id={m.group(1)}",
                        "particle": "happy_villager"})
    if "hide" in q and m and int(m.group(1)) in cluster_ids:
        actions.append({"action": "hide",
                        "selector": f"cluster_id={m.group(1)}"})
    if not actions:
        actions.append({"action": "highlight",
                        "selector": "all",
                        "particle": "end_rod"})
    return actions
