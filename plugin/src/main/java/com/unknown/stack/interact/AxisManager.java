package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class AxisManager {

    public enum Axis {
        X(Material.RED_CONCRETE, "X"),
        Y(Material.LIME_CONCRETE, "Y"),
        Z(Material.BLUE_CONCRETE, "Z");

        public final Material block;
        public final String label;
        Axis(Material block, String label) { this.block = block; this.label = label; }
    }

    private final SceneRegistry registry;
    private final Map<BlockVector, Axis> placed = new HashMap<>();
    private volatile boolean visible = false;

    public AxisManager(SceneRegistry registry) {
        this.registry = registry;
    }

    public boolean isVisible() { return visible; }

    public Axis axisAt(int x, int y, int z) {
        return placed.get(new BlockVector(x, y, z));
    }

    public boolean show(World world) {
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null || snap.globalCentroid == null) return false;
        if (visible) return true;

        int[][] bounds = snap.bounds;
        int cx = snap.globalCentroid.getBlockX();
        int cy = snap.globalCentroid.getBlockY();
        int cz = snap.globalCentroid.getBlockZ();

        for (int x = bounds[0][0]; x <= bounds[1][0]; x++) {
            tryPlace(world, x, cy, cz, Axis.X, cx, cy, cz);
        }
        for (int y = bounds[0][1]; y <= bounds[1][1]; y++) {
            tryPlace(world, cx, y, cz, Axis.Y, cx, cy, cz);
        }
        for (int z = bounds[0][2]; z <= bounds[1][2]; z++) {
            tryPlace(world, cx, cy, z, Axis.Z, cx, cy, cz);
        }
        visible = true;
        return true;
    }

    private void tryPlace(World world, int x, int y, int z, Axis axis,
                          int cx, int cy, int cz) {
        if (x == cx && y == cy && z == cz) return;
        world.getBlockAt(x, y, z).setType(axis.block, false);
        placed.put(new BlockVector(x, y, z), axis);
    }

    public void hide(World world) {
        for (Map.Entry<BlockVector, Axis> e : placed.entrySet()) {
            BlockVector v = e.getKey();
            world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ()).setType(Material.AIR, false);
        }
        placed.clear();
        visible = false;
    }

    public void clearTracking() {
        placed.clear();
        visible = false;
    }

    public Vector axisCenter(World world) {
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null || snap.globalCentroid == null) return null;
        return snap.globalCentroid;
    }
}
