package com.unknown.stack.interact;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

public final class WorldDecor {

    private WorldDecor() {}

    private static final int ISLAND_Y = 75;
    private static final int ISLAND_RADIUS = 3;
    private static final int PILLAR_Y_BASE = 4;
    private static final int PILLAR_Y_TOP = 65;
    private static final int SKY_Y_MIN = 200;
    private static final int SKY_Y_MAX = 218;
    private static final int SKY_LANTERN_COUNT = 45;
    private static final long SKY_SEED = 42L;

    public static void build(World w) {
        if (w == null) return;
        compassIslands(w);
        boundsPillars(w);
        skyLanterns(w);
    }

    private static void compassIslands(World w) {
        int[][] centers = {
                {64, ISLAND_Y, -80},
                {64, ISLAND_Y, 208},
                {-80, ISLAND_Y, 64},
                {208, ISLAND_Y, 64}
        };
        Material[] trees = {Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.CHERRY_LOG};
        Material[] leaves = {Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES, Material.CHERRY_LEAVES};
        Material[] flowers = {Material.POPPY, Material.DANDELION, Material.AZURE_BLUET,
                              Material.ALLIUM, Material.OXEYE_DAISY, Material.CORNFLOWER,
                              Material.LILY_OF_THE_VALLEY};
        for (int i = 0; i < centers.length; i++) {
            int cx = centers[i][0], cy = centers[i][1], cz = centers[i][2];
            for (int dx = -ISLAND_RADIUS; dx <= ISLAND_RADIUS; dx++) {
                for (int dz = -ISLAND_RADIUS; dz <= ISLAND_RADIUS; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r > ISLAND_RADIUS + 0.3) continue;
                    w.getBlockAt(cx + dx, cy - 1, cz + dz).setType(Material.DIRT, false);
                    w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.GRASS_BLOCK, false);
                }
            }
            // Tree at center
            for (int t = 0; t < 4; t++) {
                w.getBlockAt(cx, cy + 1 + t, cz).setType(trees[i], false);
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dx == 0 && dz == 0) continue;
                    if (w.getBlockAt(cx + dx, cy + 4, cz + dz).getType() == Material.AIR) {
                        w.getBlockAt(cx + dx, cy + 4, cz + dz).setType(leaves[i], false);
                    }
                }
            }
            w.getBlockAt(cx, cy + 5, cz).setType(leaves[i], false);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (w.getBlockAt(cx + dx, cy + 5, cz + dz).getType() == Material.AIR) {
                        w.getBlockAt(cx + dx, cy + 5, cz + dz).setType(leaves[i], false);
                    }
                }
            }
            // Flowers on the grass edge
            int fIdx = 0;
            int[][] flowerSpots = {
                    {ISLAND_RADIUS, 0}, {-ISLAND_RADIUS, 0},
                    {0, ISLAND_RADIUS}, {0, -ISLAND_RADIUS},
                    {2, 2}, {-2, 2}, {2, -2}, {-2, -2}
            };
            for (int[] s : flowerSpots) {
                if (w.getBlockAt(cx + s[0], cy + 1, cz + s[1]).getType() == Material.AIR
                        && w.getBlockAt(cx + s[0], cy, cz + s[1]).getType() == Material.GRASS_BLOCK) {
                    w.getBlockAt(cx + s[0], cy + 1, cz + s[1])
                            .setType(flowers[(fIdx + i) % flowers.length], false);
                    fIdx++;
                }
            }
            // Lantern at one corner of each island
            w.getBlockAt(cx + 2, cy + 1, cz - 2).setType(Material.LANTERN, false);
        }
    }

    private static void boundsPillars(World w) {
        int[][] corners = {{-2, -2}, {130, -2}, {-2, 130}, {130, 130}};
        for (int[] c : corners) {
            int x = c[0], z = c[1];
            w.getBlockAt(x, 3, z).setType(Material.POLISHED_BLACKSTONE, false);
            for (int y = PILLAR_Y_BASE; y <= PILLAR_Y_TOP; y++) {
                Material m = (y % 5 == 0) ? Material.SEA_LANTERN : Material.DEEPSLATE_BRICKS;
                w.getBlockAt(x, y, z).setType(m, false);
            }
            w.getBlockAt(x, PILLAR_Y_TOP + 1, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(x, PILLAR_Y_TOP + 2, z).setType(Material.END_ROD, false);
        }
    }

    private static void skyLanterns(World w) {
        Random rng = new Random(SKY_SEED);
        int xMin = -60, xMax = 200, zMin = -60, zMax = 200;
        for (int i = 0; i < SKY_LANTERN_COUNT; i++) {
            int x = xMin + rng.nextInt(xMax - xMin);
            int z = zMin + rng.nextInt(zMax - zMin);
            int y = SKY_Y_MIN + rng.nextInt(SKY_Y_MAX - SKY_Y_MIN + 1);
            w.getBlockAt(x, y, z).setType(Material.SEA_LANTERN, false);
        }
    }
}
