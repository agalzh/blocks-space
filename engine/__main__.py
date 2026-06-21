"""CLI: python -m engine <path> [--demo] [--out out/scene.json] [--seed 42]."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def _reexec_under_venv_if_needed() -> None:
    """If our deps aren't on this interpreter, re-launch under engine/.venv."""
    venv_py = Path(__file__).resolve().parent / ".venv" / "Scripts" / "python.exe"
    if not venv_py.exists():
        return
    try:
        if Path(sys.executable).resolve() == venv_py.resolve():
            return
    except OSError:
        return
    try:
        import sklearn  # noqa: F401
    except ImportError:
        argv = [str(venv_py), "-m", "engine", *sys.argv[1:]]
        sys.exit(subprocess.call(argv, env=os.environ.copy()))


_reexec_under_venv_if_needed()

import argparse  # noqa: E402
import json  # noqa: E402

import numpy as np  # noqa: E402

from . import cluster, ingest, layout, outliers, scene  # noqa: E402


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
