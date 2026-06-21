"""KMeans++ with silhouette-based auto-k in [2..8]."""
from __future__ import annotations

import numpy as np
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

K_MIN = 2
K_MAX = 8


def auto_kmeans(X: np.ndarray, random_state: int = 42) -> tuple[np.ndarray, np.ndarray, int]:
    """Return (labels, centroids_nd, best_k).

    For very small datasets (< 6 rows) clamp k=2. Above that, sweep k in [2..8]
    capped at n_samples-1 and pick the highest silhouette score.
    """
    n = X.shape[0]
    if n < 6:
        k = min(2, max(1, n))
        km = KMeans(n_clusters=k, init="k-means++", n_init=10, random_state=random_state)
        labels = km.fit_predict(X)
        return labels, km.cluster_centers_, k

    k_hi = min(K_MAX, n - 1)
    best_k, best_score, best_model = K_MIN, -1.0, None
    for k in range(K_MIN, k_hi + 1):
        km = KMeans(n_clusters=k, init="k-means++", n_init=10, random_state=random_state)
        labels = km.fit_predict(X)
        if len(set(labels)) < 2:
            continue
        score = silhouette_score(X, labels)
        if score > best_score:
            best_k, best_score, best_model = k, score, km

    assert best_model is not None, "silhouette sweep failed to fit any k"
    return best_model.labels_, best_model.cluster_centers_, best_k
