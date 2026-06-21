"""IsolationForest outlier scores normalized to [0, 1]."""
from __future__ import annotations

import numpy as np
from sklearn.ensemble import IsolationForest


def score(X: np.ndarray, random_state: int = 42) -> np.ndarray:
    """Higher = more anomalous. Linearly rescaled to [0, 1] over the batch."""
    n = X.shape[0]
    if n < 8:
        return np.zeros(n, dtype=float)

    iso = IsolationForest(
        n_estimators=100,
        contamination="auto",
        random_state=random_state,
    )
    iso.fit(X)
    raw = -iso.score_samples(X)
    lo, hi = float(raw.min()), float(raw.max())
    if hi - lo < 1e-12:
        return np.zeros(n, dtype=float)
    return ((raw - lo) / (hi - lo)).astype(float)
