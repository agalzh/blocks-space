package com.unknown.stack.interact;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class SpawnPlatform {

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

        int flowerIdx = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 5.3) continue;

                w.getBlockAt(dx, 62, dz).setType(Material.DIRT, false);

                boolean isInnerDeck = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                Material top = isInnerDeck ? Material.OAK_PLANKS : Material.GRASS_BLOCK;
                w.getBlockAt(dx, 63, dz).setType(top, false);

                if (!isInnerDeck && r >= 3.2 && r <= 5.3) {
                    if (((dx + dz + 10) % 2) == 0 && !isReserved(dx, dz)) {
                        w.getBlockAt(dx, 64, dz).setType(FLOWER_MIX[flowerIdx % FLOWER_MIX.length], false);
                        flowerIdx++;
                    }
                }
            }
        }

        int[][] pond = {{-3, -3}, {-3, -4}, {-4, -3}};
        for (int[] p : pond) {
            w.getBlockAt(p[0], 63, p[1]).setType(Material.WATER, false);
            w.getBlockAt(p[0], 64, p[1]).setType(Material.AIR, false);
        }

        placeOakTree(w, 3, 64, 3);
        placeCherryTree(w, -2, 64, 4);

        int[][] lampSpots = {{-5, 0}, {5, 0}, {0, -5}, {0, 5}};
        for (int[] s : lampSpots) {
            w.getBlockAt(s[0], 63, s[1]).setType(Material.MOSS_BLOCK, false);
            w.getBlockAt(s[0], 64, s[1]).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(s[0], 65, s[1]).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(s[0], 66, s[1]).setType(Material.SOUL_LANTERN, false);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(dx, 64, dz).setType(Material.AIR, false);
                w.getBlockAt(dx, 65, dz).setType(Material.AIR, false);
            }
        }

        w.setSpawnLocation(new Location(w, 0.5, 64, 0.5));
    }

    private static boolean isReserved(int dx, int dz) {
        if ((dx == -5 || dx == 5) && dz == 0) return true;
        if (dx == 0 && (dz == -5 || dz == 5)) return true;
        if (dx == 3 && dz == 3) return true;
        if (dx == -2 && dz == 4) return true;
        if (dx == -3 && dz == -3) return true;
        if (dx == -3 && dz == -4) return true;
        if (dx == -4 && dz == -3) return true;
        return false;
    }

    private static void clearArea(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
    }

    private static void placeOakTree(World w, int x, int y, int z) {
        for (int i = 0; i < 4; i++) {
            w.getBlockAt(x, y + i, z).setType(Material.OAK_LOG, false);
        }
        for (int dy = 2; dy <= 4; dy++) {
            int radius = (dy == 4) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 4) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy < 4) continue;
                    if (w.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.AIR) {
                        w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.OAK_LEAVES, false);
                    }
                }
            }
        }
        w.getBlockAt(x, y + 4, z).setType(Material.OAK_LEAVES, false);
    }

    private static void placeCherryTree(World w, int x, int y, int z) {
        for (int i = 0; i < 4; i++) {
            w.getBlockAt(x, y + i, z).setType(Material.CHERRY_LOG, false);
        }
        for (int dy = 2; dy <= 4; dy++) {
            int radius = (dy == 4) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 4) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy < 4) continue;
                    if (w.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.AIR) {
                        w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.CHERRY_LEAVES, false);
                    }
                }
            }
        }
        w.getBlockAt(x, y + 4, z).setType(Material.CHERRY_LEAVES, false);
    }
}
