package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class AxisManager {

    public enum Axis { X, Y, Z }

    private static final int BARRIER_STEP = 4;

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
        if (world == null) return false;

        int[][] b = snap.bounds;
        Vector c = snap.globalCentroid;
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();

        for (int x = b[0][0]; x <= b[1][0]; x += BARRIER_STEP) {
            placeBarrier(world, x, cy, cz, Axis.X, cx, cy, cz);
        }
        for (int y = b[0][1]; y <= b[1][1]; y += BARRIER_STEP) {
            placeBarrier(world, cx, y, cz, Axis.Y, cx, cy, cz);
        }
        for (int z = b[0][2]; z <= b[1][2]; z += BARRIER_STEP) {
            placeBarrier(world, cx, cy, z, Axis.Z, cx, cy, cz);
        }
        visible = true;
        return true;
    }

    private void placeBarrier(World world, int x, int y, int z, Axis axis,
                              int cx, int cy, int cz) {
        if (x == cx && y == cy && z == cz) return;
        world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
        placed.put(new BlockVector(x, y, z), axis);
    }

    public void hide(World world) {
        if (world != null) {
            for (BlockVector v : placed.keySet()) {
                world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ()).setType(Material.AIR, false);
            }
        }
        placed.clear();
        visible = false;
    }

    public void clearTracking() {
        placed.clear();
        visible = false;
    }
}
