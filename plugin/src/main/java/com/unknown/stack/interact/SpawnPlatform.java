package com.unknown.stack.interact;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class SpawnPlatform {

    private SpawnPlatform() {}

    public static void build(World world) {
        if (world == null) return;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(dx, 63, dz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                Material top = corner ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ;
                world.getBlockAt(dx, 64, dz).setType(top, false);
            }
        }
        int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] c : corners) {
            world.getBlockAt(c[0], 65, c[1]).setType(Material.END_ROD, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(dx, 65, dz).setType(Material.AIR, false);
                world.getBlockAt(dx, 66, dz).setType(Material.AIR, false);
            }
        }

        world.setSpawnLocation(new Location(world, 0.5, 65, 0.5));
    }
}
