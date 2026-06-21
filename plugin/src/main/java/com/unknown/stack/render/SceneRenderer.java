package com.unknown.stack.render;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SceneRenderer {

    public static final Material CLUSTER_MEAN_MATERIAL = Material.BEACON;
    public static final Material CENTROID_MATERIAL = Material.SCULK_CATALYST;

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
    private final SceneRegistry registry;

    public SceneRenderer(Logger log, SceneRegistry registry) {
        this.log = log;
        this.registry = registry;
    }

    public Result render(JSONObject scene, World world) {
        if (world == null) {
            throw new IllegalStateException("no world to render into");
        }
        int[][] bounds = readBounds(scene);

        Map<BlockVector, Integer> blockToCluster = new HashMap<>();
        Map<BlockVector, Double> blockOutlier = new HashMap<>();
        int[] pointStats = renderPoints(world, scene.optJSONArray("points"), bounds,
                blockToCluster, blockOutlier);

        Map<Integer, Vector> clusterMean = new HashMap<>();
        Map<Integer, Integer> clusterSize = new HashMap<>();
        int means = renderClusterMeans(world, scene.optJSONArray("clusters"), bounds,
                clusterMean, clusterSize, blockToCluster);

        Vector centroid = readCentroid(scene);
        placeCentroidMarker(world, centroid, bounds);

        String name = "?";
        int totalPoints = 0;
        JSONObject ds = scene.optJSONObject("dataset");
        if (ds != null) {
            name = ds.optString("name", "?");
            totalPoints = ds.optInt("rows", pointStats[0] + pointStats[1]);
        } else {
            totalPoints = pointStats[0] + pointStats[1];
        }

        registry.set(new SceneRegistry.Snapshot(bounds, blockToCluster, blockOutlier,
                clusterMean, clusterSize, centroid, name, totalPoints,
                SceneRegistry.OUTLIER_THRESHOLD));
        return new Result(pointStats[0], means, pointStats[1], name);
    }

    private static int[][] readBounds(JSONObject scene) {
        JSONObject b = scene.optJSONObject("bounds");
        if (b == null) {
            return new int[][] { {0, 4, 0}, {128, 60, 128} };
        }
        return new int[][] { readCoord(b.getJSONArray("min")), readCoord(b.getJSONArray("max")) };
    }

    private static Vector readCentroid(JSONObject scene) {
        JSONArray a = scene.optJSONArray("global_centroid");
        if (a == null) return null;
        int[] p = readCoord(a);
        return new Vector(p[0], p[1], p[2]);
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

    private int[] renderPoints(World world, JSONArray points, int[][] bounds,
                               Map<BlockVector, Integer> blockToCluster,
                               Map<BlockVector, Double> blockOutlier) {
        if (points == null) return new int[] {0, 0};
        int placed = 0, skipped = 0;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            int[] pos = readCoord(p.getJSONArray("pos"));
            if (!withinBounds(pos, bounds)) { skipped++; continue; }
            Material m = Material.matchMaterial(p.getString("block"));
            if (m == null || !m.isBlock()) { skipped++; continue; }
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(m, false);
            BlockVector key = new BlockVector(pos[0], pos[1], pos[2]);
            blockToCluster.put(key, p.getInt("cluster_id"));
            double os = p.optDouble("outlier_score", Double.NaN);
            if (!Double.isNaN(os)) {
                blockOutlier.put(key, os);
            }
            placed++;
        }
        return new int[] {placed, skipped};
    }

    private int renderClusterMeans(World world, JSONArray clusters, int[][] bounds,
                                   Map<Integer, Vector> clusterMean,
                                   Map<Integer, Integer> clusterSize,
                                   Map<BlockVector, Integer> blockToCluster) {
        if (clusters == null) return 0;
        int placed = 0;
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject c = clusters.getJSONObject(i);
            int id = c.getInt("id");

            JSONArray pts = c.optJSONArray("point_ids");
            if (pts != null) {
                clusterSize.put(id, pts.length());
            } else {
                int count = 0;
                for (Integer v : blockToCluster.values()) if (v == id) count++;
                clusterSize.put(id, count);
            }

            int[] pos = readCoord(c.getJSONArray("mean"));
            if (!withinBounds(pos, bounds)) continue;
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(CLUSTER_MEAN_MATERIAL, false);
            clusterMean.put(id, new Vector(pos[0], pos[1], pos[2]));
            placed++;
        }
        return placed;
    }

    private void placeCentroidMarker(World world, Vector centroid, int[][] bounds) {
        if (centroid == null) return;
        int x = centroid.getBlockX(), y = centroid.getBlockY(), z = centroid.getBlockZ();
        if (!withinBounds(new int[] {x, y, z}, bounds)) {
            log.info("centroid out of bounds: " + x + "," + y + "," + z);
            return;
        }
        world.getBlockAt(x, y, z).setType(CENTROID_MATERIAL, false);
        log.info("centroid placed at " + x + "," + y + "," + z);
    }

    public static World defaultWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}
