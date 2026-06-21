"""Generate the three demo CSVs under samples/.

Run: python -m engine.make_samples
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd
from sklearn.datasets import load_digits, load_wine

SAMPLES = Path(__file__).resolve().parent.parent / "samples"


def _digits_1k() -> Path:
    d = load_digits()
    df = pd.DataFrame(d.data[:1000], columns=[f"px_{i}" for i in range(d.data.shape[1])])
    out = SAMPLES / "digits_1k.csv"
    df.to_csv(out, index=False)
    return out


def _wine() -> Path:
    w = load_wine()
    df = pd.DataFrame(w.data, columns=w.feature_names)
    out = SAMPLES / "wine.csv"
    df.to_csv(out, index=False)
    return out


def main() -> None:
    SAMPLES.mkdir(parents=True, exist_ok=True)
    for fn in (_digits_1k, _wine):
        path = fn()
        df = pd.read_csv(path)
        print(f"wrote {path.name}: {df.shape[0]} rows x {df.shape[1]} cols")


if __name__ == "__main__":
    main()
