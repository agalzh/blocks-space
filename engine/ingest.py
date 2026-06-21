"""CSV / JSON loader. Drops non-numeric columns, drops rows with NaN."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd


def load(path: str | Path) -> tuple[np.ndarray, list[str], str]:
    """Return (X, feature_names, dataset_name).

    - CSV: pandas.read_csv with header inference.
    - JSON: list of records OR {"data": [...]}.
    Non-numeric columns are dropped; rows with NaN after coercion are dropped.
    """
    p = Path(path)
    if not p.is_file():
        raise FileNotFoundError(f"dataset not found: {p}")

    suffix = p.suffix.lower()
    if suffix == ".csv":
        df = pd.read_csv(p)
    elif suffix == ".json":
        raw = json.loads(p.read_text())
        if isinstance(raw, dict) and "data" in raw:
            raw = raw["data"]
        if not isinstance(raw, list):
            raise ValueError(f"JSON must be a list of records or {{'data': [...]}}: {p}")
        df = pd.DataFrame(raw)
    else:
        raise ValueError(f"unsupported file type: {suffix} (use .csv or .json)")

    numeric = df.apply(pd.to_numeric, errors="coerce")
    numeric = numeric.dropna(axis=1, how="all")
    numeric = numeric.dropna(axis=0, how="any")

    if numeric.shape[0] == 0 or numeric.shape[1] == 0:
        raise ValueError(f"no usable numeric data in {p} (shape={df.shape})")

    return numeric.to_numpy(dtype=float), list(numeric.columns), p.stem
