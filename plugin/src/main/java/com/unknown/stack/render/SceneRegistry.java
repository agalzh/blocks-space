package com.unknown.stack.render;

import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Map;

public class SceneRegistry {

    public static final class Snapshot {
        public final int[][] bounds;
        public final Map<BlockVector, Integer> blockToCluster;
        public final Map<Integer, Vector> clusterMean;
        public final Vector globalCentroid;
        public final String datasetName;

        public Snapshot(int[][] bounds,
                        Map<BlockVector, Integer> blockToCluster,
                        Map<Integer, Vector> clusterMean,
                        Vector globalCentroid,
                        String datasetName) {
            this.bounds = bounds;
            this.blockToCluster = Collections.unmodifiableMap(blockToCluster);
            this.clusterMean = Collections.unmodifiableMap(clusterMean);
            this.globalCentroid = globalCentroid;
            this.datasetName = datasetName;
        }
    }

    private volatile Snapshot current;

    public Snapshot get() { return current; }
    public void set(Snapshot s) { this.current = s; }
    public void clear() { this.current = null; }
}
