package com.unknown.stack.render;

import org.bukkit.Color;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SceneRegistry {

    public static final double OUTLIER_THRESHOLD = 0.7;

    public static final class Snapshot {
        public final int[][] bounds;
        public final Map<BlockVector, Integer> blockToCluster;
        public final Map<BlockVector, Double> blockOutlier;
        public final Map<BlockVector, Map<String, Double>> blockFeatures;
        public final Map<BlockVector, String> blockOriginal;
        public final Map<Integer, Vector> clusterMean;
        public final Map<Integer, Integer> clusterSize;
        public final Map<Integer, Color> clusterColor;
        public final Vector globalCentroid;
        public final String datasetName;
        public final int totalPoints;
        public final double outlierThreshold;
        public final List<String> featureNames;

        public Snapshot(int[][] bounds,
                        Map<BlockVector, Integer> blockToCluster,
                        Map<BlockVector, Double> blockOutlier,
                        Map<BlockVector, Map<String, Double>> blockFeatures,
                        Map<BlockVector, String> blockOriginal,
                        Map<Integer, Vector> clusterMean,
                        Map<Integer, Integer> clusterSize,
                        Map<Integer, Color> clusterColor,
                        Vector globalCentroid,
                        String datasetName,
                        int totalPoints,
                        double outlierThreshold,
                        List<String> featureNames) {
            this.bounds = bounds;
            this.blockToCluster = Collections.unmodifiableMap(blockToCluster);
            this.blockOutlier = Collections.unmodifiableMap(blockOutlier);
            this.blockFeatures = Collections.unmodifiableMap(blockFeatures);
            this.blockOriginal = Collections.unmodifiableMap(blockOriginal);
            this.clusterMean = Collections.unmodifiableMap(clusterMean);
            this.clusterSize = Collections.unmodifiableMap(clusterSize);
            this.clusterColor = Collections.unmodifiableMap(clusterColor);
            this.globalCentroid = globalCentroid;
            this.datasetName = datasetName;
            this.totalPoints = totalPoints;
            this.outlierThreshold = outlierThreshold;
            this.featureNames = Collections.unmodifiableList(featureNames);
        }
    }

    private volatile Snapshot current;

    public Snapshot get() { return current; }
    public void set(Snapshot s) { this.current = s; }
    public void clear() { this.current = null; }
}
