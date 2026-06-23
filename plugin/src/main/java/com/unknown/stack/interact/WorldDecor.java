package com.unknown.stack.interact;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WorldDecor {

    private WorldDecor() {}

    /** Centres {x,y,z} of every island placed in the last build — read by AmbienceTask. */
    public static final List<int[]> ISLAND_CENTERS = new ArrayList<>();

    private static final int PILLAR_Y_BASE = 4;
    private static final int PILLAR_Y_TOP = 65;
    private static final int SKY_Y_MIN = 200;
    private static final int SKY_Y_MAX = 218;
    private static final int SKY_LANTERN_COUNT = 45;
    private static final long DECOR_SEED = 42L;

    private static final int ISLAND_COUNT = 10;
    private static final int ISLAND_Y_MIN = 70;
    private static final int ISLAND_Y_MAX = 109;
    private static final int ISLAND_R_MIN = 4;
    private static final int ISLAND_R_MAX = 9;

    private static final Material[] LOGS = {
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
            Material.CHERRY_LOG, Material.DARK_OAK_LOG
    };
    private static final Material[] LEAVES = {
            Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES,
            Material.CHERRY_LEAVES, Material.DARK_OAK_LEAVES
    };
    private static final Material[] FLOWERS = {
            Material.POPPY, Material.DANDELION, Material.AZURE_BLUET,
            Material.ALLIUM, Material.OXEYE_DAISY, Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY, Material.RED_TULIP,
            Material.ORANGE_TULIP, Material.BLUE_ORCHID, Material.FERN
    };

    private enum IslandFeature { NONE, ARCH, RUINED_WALL, EXTRA_TREE, LANTERN_POST }

    public static void build(World w) {
        if (w == null) return;
        randomIslands(w);
        boundsPillars(w);
        skyLanterns(w);
        dataAreaLighting(w);
    }

    private static void dataAreaLighting(World w) {
        int[] xs = {0, 64, 128};
        int[] zs = {0, 64, 128};
        for (int x : xs) {
            for (int z : zs) {
                placeLight(w, x, 62, z);
                placeLight(w, x, 3, z);
            }
        }
    }

    private static void placeLight(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != Material.AIR) return;
        b.setType(Material.LIGHT, false);
        BlockData data = b.getBlockData();
        if (data instanceof Levelled lev) {
            lev.setLevel(15);
            b.setBlockData(lev, false);
        }
    }

    private static void randomIslands(World w) {
        ISLAND_CENTERS.clear();
        Random rng = new Random(DECOR_SEED + 7);
        int placed = 0;
        int attempts = 0;
        while (placed < ISLAND_COUNT && attempts < 200) {
            attempts++;
            int cx = -100 + rng.nextInt(330);
            int cz = -100 + rng.nextInt(330);
            int cy = ISLAND_Y_MIN + rng.nextInt(ISLAND_Y_MAX - ISLAND_Y_MIN + 1);
            int radius = ISLAND_R_MIN + rng.nextInt(ISLAND_R_MAX - ISLAND_R_MIN + 1);

            // Avoid the data box (X/Z 0..128) with a 10 b margin.
            if (cx + radius >= -10 && cx - radius <= 138
                    && cz + radius >= -10 && cz - radius <= 138) continue;
            // Avoid the spawn platform (radius ~6 around its center).
            if (Math.hypot(cx - SpawnPlatform.SPAWN_CX, cz - SpawnPlatform.SPAWN_CZ) < radius + 12) continue;

            placeIsland(w, cx, cy, cz, radius, rng);
            ISLAND_CENTERS.add(new int[]{cx, cy, cz});
            placed++;
        }
    }

    private static void placeIsland(World w, int cx, int cy, int cz, int radius, Random rng) {
        int speciesIdx = rng.nextInt(LOGS.length);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > radius + 0.3) continue;
                int depth = 1 + (int) ((radius - r) / 2.0);
                for (int d = 0; d < depth; d++) {
                    w.getBlockAt(cx + dx, cy - 1 - d, cz + dz).setType(Material.DIRT, false);
                }
                w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.GRASS_BLOCK, false);
            }
        }

        placeSmallTree(w, cx, cy + 1, cz, LOGS[speciesIdx], LEAVES[speciesIdx], rng);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > radius + 0.3 || r < radius - 2.5) continue;
                if (rng.nextInt(3) != 0) continue;
                int wx = cx + dx, wz = cz + dz;
                if (w.getBlockAt(wx, cy + 1, wz).getType() != Material.AIR) continue;
                if (w.getBlockAt(wx, cy, wz).getType() != Material.GRASS_BLOCK) continue;
                w.getBlockAt(wx, cy + 1, wz).setType(FLOWERS[rng.nextInt(FLOWERS.length)], false);
            }
        }

        IslandFeature feature = pickFeature(rng);
        int offsetX = (radius - 2);
        int offsetZ = -(radius - 2);
        switch (feature) {
            case ARCH -> placeArch(w, cx + offsetX, cy + 1, cz + offsetZ, rng);
            case RUINED_WALL -> placeRuinedWall(w, cx - offsetX, cy + 1, cz + offsetZ, rng);
            case EXTRA_TREE -> {
                int ex = cx + offsetX;
                int ez = cz + (rng.nextInt(3) - 1);
                if (w.getBlockAt(ex, cy, ez).getType() == Material.GRASS_BLOCK) {
                    int idx2 = rng.nextInt(LOGS.length);
                    placeSmallTree(w, ex, cy + 1, ez, LOGS[idx2], LEAVES[idx2], rng);
                }
            }
            case LANTERN_POST -> placeLanternPost(w, cx + offsetX, cy + 1, cz + offsetZ);
            case NONE -> {}
        }

        // Strong, even lighting: a grid of invisible light cells just above the
        // surface so the whole island top glows, plus lights through and over the
        // canopy so the foliage itself is lit from within and above.
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                if (Math.hypot(dx, dz) > radius + 0.3) continue;
                placeLight(w, cx + dx, cy + 2, cz + dz);
            }
        }
        placeLight(w, cx, cy + 4, cz);
        placeLight(w, cx, cy + 7, cz);
    }

    private static IslandFeature pickFeature(Random rng) {
        int r = rng.nextInt(100);
        if (r < 25) return IslandFeature.ARCH;
        if (r < 45) return IslandFeature.RUINED_WALL;
        if (r < 65) return IslandFeature.EXTRA_TREE;
        if (r < 80) return IslandFeature.LANTERN_POST;
        return IslandFeature.NONE;
    }

    private static void placeArch(World w, int x, int y, int z, Random rng) {
        Material m = rng.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
        for (int dy = 0; dy <= 2; dy++) {
            safeSet(w, x - 1, y + dy, z, m);
            safeSet(w, x + 1, y + dy, z, m);
        }
        safeSet(w, x - 1, y + 3, z, m);
        safeSet(w, x,     y + 3, z, m);
        safeSet(w, x + 1, y + 3, z, m);
    }

    private static void placeRuinedWall(World w, int x, int y, int z, Random rng) {
        for (int i = 0; i < 4; i++) {
            if (rng.nextInt(5) == 0) continue;
            safeSet(w, x + i, y, z, Material.MOSSY_COBBLESTONE);
            if (rng.nextInt(2) == 0) {
                safeSet(w, x + i, y + 1, z, Material.MOSSY_COBBLESTONE);
            }
        }
    }

    private static void placeLanternPost(World w, int x, int y, int z) {
        safeSet(w, x, y,     z, Material.POLISHED_BLACKSTONE_WALL);
        safeSet(w, x, y + 1, z, Material.POLISHED_BLACKSTONE_WALL);
        safeSet(w, x, y + 2, z, Material.LANTERN);
    }

    private static void placeSmallTree(World w, int x, int y, int z,
                                       Material log, Material leaves, Random rng) {
        int height = 3 + rng.nextInt(2);
        for (int i = 0; i < height; i++) {
            safeSet(w, x, y + i, z, log);
        }
        for (int dy = height - 2; dy <= height; dy++) {
            int r = (dy == height) ? 0 : (1 + (height - dy));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && r > 0) continue;
                    if (dx == 0 && dz == 0 && dy < height) continue;
                    if (w.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.AIR) {
                        w.getBlockAt(x + dx, y + dy, z + dz).setType(leaves, false);
                    }
                }
            }
        }
        safeSet(w, x, y + height, z, leaves);
    }

    private static void safeSet(World w, int x, int y, int z, Material m) {
        if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
            w.getBlockAt(x, y, z).setType(m, false);
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
        Random rng = new Random(DECOR_SEED);
        int xMin = -60, xMax = 200, zMin = -60, zMax = 200;
        for (int i = 0; i < SKY_LANTERN_COUNT; i++) {
            int x = xMin + rng.nextInt(xMax - xMin);
            int z = zMin + rng.nextInt(zMax - zMin);
            int y = SKY_Y_MIN + rng.nextInt(SKY_Y_MAX - SKY_Y_MIN + 1);
            w.getBlockAt(x, y, z).setType(Material.SEA_LANTERN, false);
        }
    }
}
