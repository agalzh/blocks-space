"""CLI: python -m engine <path> [--demo] [--out out/scene.json] [--seed 42]."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

from . import cluster, ingest, layout, outliers, scene


def _make_demo() -> tuple[np.ndarray, list[str], str]:
    """Synthetic blobs: 6 clusters, 15 dims, 1000 samples — mirrors the reference engine."""
    from sklearn.datasets import make_blobs

    X, _ = make_blobs(n_samples=1000, n_features=15, centers=6, random_state=42)
    feats = [f"f{i}" for i in range(15)]
    return X, feats, "make_blobs_demo"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="engine", description="Scene Graph emitter")
    ap.add_argument("path", nargs="?", help="CSV or JSON dataset (omit with --demo)")
    ap.add_argument("--demo", action="store_true", help="Use synthetic make_blobs data")
    ap.add_argument("--out", default="out/scene.json", help="Output Scene Graph path")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args(argv)

    if args.demo:
        X, feats, name = _make_demo()
    else:
        if not args.path:
            ap.error("provide a dataset path or pass --demo")
        try:
            X, feats, name = ingest.load(args.path)
        except (FileNotFoundError, ValueError) as e:
            print(f"ingest error: {e}", file=sys.stderr)
            return 2

    print(f"[engine] dataset={name} shape={X.shape}")

    labels, centroids_nd, k = cluster.auto_kmeans(X, random_state=args.seed)
    print(f"[engine] clusters k={k}")

    coords_3d, centroids_3d, method = layout.project_3d(
        X, centroids_nd, random_state=args.seed
    )
    print(f"[engine] projection={method}")

    outl = outliers.score(X, random_state=args.seed)
    print(f"[engine] outliers count(>0.7)={int((outl > 0.7).sum())}")

    sg = scene.build_scene(name, feats, X, coords_3d, labels, centroids_3d, outl)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(sg, indent=2))

    last_scene = Path("out/last_scene.json")
    last_scene.parent.mkdir(parents=True, exist_ok=True)
    last_scene.write_text(json.dumps(sg, indent=2))

    print(f"[engine] wrote {out_path} ({len(sg['points'])} points, {len(sg['clusters'])} clusters)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
