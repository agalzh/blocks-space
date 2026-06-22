package com.unknown.stack.interact;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;

public final class SpawnPlatform {

    public static final int SPAWN_CX = 64;
    public static final int SPAWN_CZ = -25;
    public static final int STAND_Y = 64;

    private SpawnPlatform() {}

    private static final Material[] FLOWER_MIX = {
            Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID,
            Material.ALLIUM, Material.OXEYE_DAISY, Material.AZURE_BLUET,
            Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY,
            Material.PINK_TULIP, Material.WHITE_TULIP, Material.ORANGE_TULIP,
            Material.RED_TULIP, Material.FERN, Material.PINK_PETALS
    };

    public static void build(World w) {
        if (w == null) return;

        clearArea(w, -6, 62, -6, 6, 70, 6);
        clearArea(w, SPAWN_CX - 6, 61, SPAWN_CZ - 6, SPAWN_CX + 6, 72, SPAWN_CZ + 6);

        int flowerIdx = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 5.3) continue;

                int wx = SPAWN_CX + dx;
                int wz = SPAWN_CZ + dz;

                w.getBlockAt(wx, 62, wz).setType(Material.DIRT, false);

                boolean isInnerDeck = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                Material top = isInnerDeck ? Material.OAK_PLANKS : Material.GRASS_BLOCK;
                w.getBlockAt(wx, 63, wz).setType(top, false);

                if (!isInnerDeck && r >= 3.2 && r <= 5.3) {
                    if (((dx + dz + 10) % 2) == 0 && !isReserved(dx, dz)) {
                        w.getBlockAt(wx, 64, wz).setType(FLOWER_MIX[flowerIdx % FLOWER_MIX.length], false);
                        flowerIdx++;
                    }
                }
            }
        }

        int[][] pond = {{4, -1}, {4, -2}, {5, -1}};
        for (int[] p : pond) {
            int wx = SPAWN_CX + p[0];
            int wz = SPAWN_CZ + p[1];
            w.getBlockAt(wx, 63, wz).setType(Material.WATER, false);
            w.getBlockAt(wx, 64, wz).setType(Material.AIR, false);
        }

        placeRealisticOak(w, SPAWN_CX + 3, 64, SPAWN_CZ - 3);
        placeRealisticCherry(w, SPAWN_CX - 3, 64, SPAWN_CZ - 3);

        int[][] lampSpots = {{-5, 0}, {5, 0}, {0, -5}, {0, 5}};
        for (int[] s : lampSpots) {
            int wx = SPAWN_CX + s[0];
            int wz = SPAWN_CZ + s[1];
            w.getBlockAt(wx, 63, wz).setType(Material.MOSS_BLOCK, false);
            w.getBlockAt(wx, 64, wz).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(wx, 65, wz).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(wx, 66, wz).setType(Material.SOUL_LANTERN, false);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int wx = SPAWN_CX + dx;
                int wz = SPAWN_CZ + dz;
                w.getBlockAt(wx, 64, wz).setType(Material.AIR, false);
                w.getBlockAt(wx, 65, wz).setType(Material.AIR, false);
            }
        }

        placeLight(w, SPAWN_CX, 67, SPAWN_CZ);

        w.setSpawnLocation(new Location(w, SPAWN_CX + 0.5, STAND_Y, SPAWN_CZ + 0.5));
    }

    private static boolean isReserved(int dx, int dz) {
        if ((dx == -5 || dx == 5) && dz == 0) return true;
        if (dx == 0 && (dz == -5 || dz == 5)) return true;
        if (dx == 3 && dz == -3) return true;
        if (dx == -3 && dz == -3) return true;
        if (dx == 4 && dz == -1) return true;
        if (dx == 4 && dz == -2) return true;
        if (dx == 5 && dz == -1) return true;
        return false;
    }

    private static void clearArea(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
    }

    private static void placeRealisticOak(World w, int x, int y, int z) {
        Material log = Material.OAK_LOG;
        Material leaf = Material.OAK_LEAVES;

        for (int i = 0; i < 5; i++) {
            w.getBlockAt(x, y + i, z).setType(log, false);
        }

        int[][] diamond = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : diamond) {
            safeLeaf(w, x + d[0], y + 2, z + d[1], leaf);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                safeLeaf(w, x + dx, y + 3, z + dz, leaf);
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                safeLeaf(w, x + dx, y + 4, z + dz, leaf);
            }
        }

        safeLeaf(w, x, y + 5, z, leaf);

        w.getBlockAt(x - 1, y + 1, z).setType(log, false);
        safeLeaf(w, x - 2, y + 1, z, leaf);
        safeLeaf(w, x - 2, y + 2, z, leaf);
        safeLeaf(w, x - 1, y + 2, z, leaf);
    }

    private static void placeRealisticCherry(World w, int x, int y, int z) {
        Material log = Material.CHERRY_LOG;
        Material leaf = Material.CHERRY_LEAVES;

        for (int i = 0; i < 5; i++) {
            w.getBlockAt(x, y + i, z).setType(log, false);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                safeLeaf(w, x + dx, y + 3, z + dz, leaf);
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                safeLeaf(w, x + dx, y + 4, z + dz, leaf);
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                safeLeaf(w, x + dx, y + 5, z + dz, leaf);
            }
        }

        safeLeaf(w, x, y + 5, z, leaf);
        safeLeaf(w, x, y + 6, z, leaf);

        w.getBlockAt(x + 1, y + 2, z).setType(log, false);
        safeLeaf(w, x + 2, y + 2, z, leaf);
        safeLeaf(w, x + 2, y + 3, z, leaf);
        safeLeaf(w, x + 1, y + 3, z, leaf);
    }

    private static void safeLeaf(World w, int x, int y, int z, Material leaf) {
        if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
            w.getBlockAt(x, y, z).setType(leaf, false);
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
}
