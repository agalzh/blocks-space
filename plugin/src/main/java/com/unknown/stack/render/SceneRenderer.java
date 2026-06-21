package com.unknown.stack.render;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.logging.Logger;

public class SceneRenderer {

    public static final Material CLUSTER_MEAN_MATERIAL = Material.BEACON;

    public static final class Result {
        public final int pointsPlaced;
        public final int means;
        public final int skipped;
        public final String datasetName;

        Result(int pointsPlaced, int means, int skipped, String datasetName) {
            this.pointsPlaced = pointsPlaced;
            this.means = means;
            this.skipped = skipped;
            this.datasetName = datasetName;
        }
    }

    private final Logger log;

    public SceneRenderer(Logger log) {
        this.log = log;
    }

    public Result render(JSONObject scene, World world) {
        if (world == null) {
            throw new IllegalStateException("no world to render into");
        }
        int[][] bounds = readBounds(scene);
        int[] pointStats = renderPoints(world, scene.optJSONArray("points"), bounds);
        int means = renderClusterMeans(world, scene.optJSONArray("clusters"), bounds);

        String name = "?";
        JSONObject ds = scene.optJSONObject("dataset");
        if (ds != null) {
            name = ds.optString("name", "?");
        }
        return new Result(pointStats[0], means, pointStats[1], name);
    }

    private static int[][] readBounds(JSONObject scene) {
        JSONObject b = scene.optJSONObject("bounds");
        if (b == null) {
            return new int[][] { {0, 4, 0}, {128, 60, 128} };
        }
        return new int[][] { readCoord(b.getJSONArray("min")), readCoord(b.getJSONArray("max")) };
    }

    private static int[] readCoord(JSONArray a) {
        return new int[] { a.getInt(0), a.getInt(1), a.getInt(2) };
    }

    private static boolean withinBounds(int[] pos, int[][] bounds) {
        for (int i = 0; i < 3; i++) {
            if (pos[i] < bounds[0][i] || pos[i] > bounds[1][i]) return false;
        }
        return true;
    }

    private int[] renderPoints(World world, JSONArray points, int[][] bounds) {
        if (points == null) return new int[] {0, 0};
        int placed = 0, skipped = 0;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            int[] pos = readCoord(p.getJSONArray("pos"));
            if (!withinBounds(pos, bounds)) { skipped++; continue; }
            Material m = Material.matchMaterial(p.getString("block"));
            if (m == null || !m.isBlock()) { skipped++; continue; }
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(m, false);
            placed++;
        }
        return new int[] {placed, skipped};
    }

    private int renderClusterMeans(World world, JSONArray clusters, int[][] bounds) {
        if (clusters == null) return 0;
        int placed = 0;
        for (int i = 0; i < clusters.length(); i++) {
            int[] pos = readCoord(clusters.getJSONObject(i).getJSONArray("mean"));
            if (!withinBounds(pos, bounds)) continue;
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(CLUSTER_MEAN_MATERIAL, false);
            placed++;
        }
        return placed;
    }

    public static World defaultWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}
