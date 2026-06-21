"""Standardize + project to 3D. UMAP-3 primary, PCA-3 fallback.

Centroids are projected jointly with data (concat-fit-split) so they share
the same embedding manifold, avoiding inconsistencies between fits.
"""
from __future__ import annotations

import warnings

import numpy as np
from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler

UMAP_MIN_ROWS = 50


def _pad_to_3d(arr: np.ndarray) -> np.ndarray:
    if arr.shape[1] >= 3:
        return arr[:, :3]
    pad = np.zeros((arr.shape[0], 3 - arr.shape[1]))
    return np.column_stack([arr, pad])


def project_3d(
    X: np.ndarray,
    centroids_nd: np.ndarray,
    random_state: int = 42,
) -> tuple[np.ndarray, np.ndarray, str]:
    """Return (X_3d, centroids_3d, method). Joint embedding via concat-fit-split."""
    scaler = StandardScaler().fit(X)
    Xs = scaler.transform(X)
    Cs = scaler.transform(centroids_nd)
    combined = np.vstack([Xs, Cs])
    n_data = X.shape[0]

    if n_data >= UMAP_MIN_ROWS:
        try:
            import umap.umap_ as umap  # noqa: WPS433

            with warnings.catch_warnings():
                warnings.simplefilter("ignore")
                reducer = umap.UMAP(
                    n_components=3,
                    random_state=random_state,
                    init="random",
                    n_jobs=1,
                )
                proj = reducer.fit_transform(combined)
            return proj[:n_data], proj[n_data:], "umap"
        except Exception as e:
            print(f"[layout] UMAP unavailable ({e!r}); falling back to PCA")

    n_comp = min(3, combined.shape[1], combined.shape[0] - 1)
    n_comp = max(n_comp, 1)
    proj = PCA(n_components=n_comp, random_state=random_state).fit_transform(combined)
    proj = _pad_to_3d(proj)
    return proj[:n_data], proj[n_data:], "pca"
