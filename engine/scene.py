"""Affine rescale 3D coords into MC bounds, assign block palette, emit Scene Graph."""
from __future__ import annotations

import uuid

import numpy as np

BOUNDS_MIN = np.array([0,  4, 0],   dtype=int)
BOUNDS_MAX = np.array([128, 60, 128], dtype=int)

CLUSTER_PALETTE = [
    "minecraft:red_concrete",
    "minecraft:blue_concrete",
    "minecraft:lime_concrete",
    "minecraft:yellow_concrete",
    "minecraft:magenta_concrete",
    "minecraft:orange_concrete",
    "minecraft:cyan_concrete",
    "minecraft:purple_concrete",
]
OUTLIER_BLOCK = "minecraft:glass"
OUTLIER_THRESHOLD = 0.7
POINT_CAP = 2000


def _rescale(coords_3d: np.ndarray) -> np.ndarray:
    """Min-max normalize each axis to [0,1], then map to MC bounds, round, clamp."""
    lo = coords_3d.min(axis=0)
    hi = coords_3d.max(axis=0)
    span = np.where(hi - lo < 1e-12, 1.0, hi - lo)
    norm = (coords_3d - lo) / span
    target_span = (BOUNDS_MAX - BOUNDS_MIN).astype(float)
    mapped = norm * target_span + BOUNDS_MIN.astype(float)
    rounded = np.round(mapped).astype(int)
    return np.clip(rounded, BOUNDS_MIN, BOUNDS_MAX)


def _palette(cluster_id: int) -> str:
    return CLUSTER_PALETTE[cluster_id % len(CLUSTER_PALETTE)]


def build_scene(
    dataset_name: str,
    feature_names: list[str],
    raw_X: np.ndarray,
    coords_3d: np.ndarray,
    labels: np.ndarray,
    centroids_3d: np.ndarray,
    outlier_scores: np.ndarray,
) -> dict:
    """Assemble the Scene Graph dict matching docs/scene_graph.schema.json."""
    n_rows, n_dims = raw_X.shape
    if n_rows > POINT_CAP:
        idx = np.random.default_rng(42).choice(n_rows, size=POINT_CAP, replace=False)
        coords_3d = coords_3d[idx]
        labels = labels[idx]
        outlier_scores = outlier_scores[idx]
        raw_X = raw_X[idx]
        n_rows = POINT_CAP

    all_coords = np.vstack([coords_3d, centroids_3d])
    all_mapped = _rescale(all_coords)
    point_pos = all_mapped[:n_rows]
    centroid_pos = all_mapped[n_rows:]

    global_centroid = np.round(point_pos.mean(axis=0)).astype(int)
    global_centroid = np.clip(global_centroid, BOUNDS_MIN, BOUNDS_MAX)

    clusters = []
    for cid in sorted(set(int(c) for c in labels)):
        point_ids = [int(i) for i, lbl in enumerate(labels) if int(lbl) == cid]
        clusters.append({
            "id": cid,
            "label": f"cluster_{cid}",
            "color": _palette(cid),
            "mean": [int(centroid_pos[cid][0]), int(centroid_pos[cid][1]), int(centroid_pos[cid][2])],
            "point_ids": point_ids,
        })

    points = []
    for i in range(n_rows):
        cid = int(labels[i])
        outl = float(outlier_scores[i])
        block = OUTLIER_BLOCK if outl > OUTLIER_THRESHOLD else _palette(cid)
        points.append({
            "id": i,
            "pos": [int(point_pos[i][0]), int(point_pos[i][1]), int(point_pos[i][2])],
            "cluster_id": cid,
            "outlier_score": round(outl, 4),
            "block": block,
            "meta": {"original_row": {k: float(v) for k, v in zip(feature_names, raw_X[i])}},
        })

    return {
        "session_id": str(uuid.uuid4()),
        "dataset": {"name": dataset_name, "rows": n_rows, "dims": int(n_dims)},
        "bounds": {"min": BOUNDS_MIN.tolist(), "max": BOUNDS_MAX.tolist()},
        "global_centroid": global_centroid.tolist(),
        "clusters": clusters,
        "points": points,
        "commands": [],
    }
