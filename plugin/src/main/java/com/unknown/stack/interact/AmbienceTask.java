package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class AmbienceTask extends BukkitRunnable {

    private static final long PERIOD_TICKS = 30L;
    private final Random rng = new Random();

    public static AmbienceTask start(JavaPlugin plugin) {
        AmbienceTask t = new AmbienceTask();
        t.runTaskTimer(plugin, 60L, PERIOD_TICKS);
        return t;
    }

    @Override
    public void run() {
        World w = SceneRenderer.defaultWorld();
        if (w == null) return;

        int sx = SpawnPlatform.SPAWN_CX;
        int sz = SpawnPlatform.SPAWN_CZ;
        for (int i = 0; i < 4; i++) {
            double x = sx - 4.5 + rng.nextDouble() * 9.0;
            double z = sz - 4.5 + rng.nextDouble() * 9.0;
            double y = 64.5 + rng.nextDouble() * 4.0;
            w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.005);
        }

        int[][] cornerXZ = {{-2, -2}, {130, -2}, {-2, 130}, {130, 130}};
        for (int[] c : cornerXZ) {
            double x = c[0] + 0.5 + (rng.nextDouble() - 0.5) * 1.2;
            double z = c[1] + 0.5 + (rng.nextDouble() - 0.5) * 1.2;
            double y = 65 + rng.nextDouble() * 5;
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, 1, 0.05, 0.05, 0.05, 0.005);
        }

        // Gentle fireflies drifting over a couple of the floating islands each tick.
        java.util.List<int[]> islands = WorldDecor.ISLAND_CENTERS;
        if (!islands.isEmpty()) {
            for (int k = 0; k < 2; k++) {
                int[] c = islands.get(rng.nextInt(islands.size()));
                for (int i = 0; i < 3; i++) {
                    double x = c[0] - 3.5 + rng.nextDouble() * 7.0;
                    double z = c[2] - 3.5 + rng.nextDouble() * 7.0;
                    double y = c[1] + 1.0 + rng.nextDouble() * 4.0;
                    w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.004);
                }
            }
        }
    }
}
