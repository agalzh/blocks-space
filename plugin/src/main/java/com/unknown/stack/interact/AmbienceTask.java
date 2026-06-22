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

        for (int i = 0; i < 4; i++) {
            double x = -4.5 + rng.nextDouble() * 9.0;
            double z = -4.5 + rng.nextDouble() * 9.0;
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

        if (rng.nextInt(4) == 0) {
            int[][] islands = {{64, 75, -80}, {64, 75, 208}, {-80, 75, 64}, {208, 75, 64}};
            int[] pick = islands[rng.nextInt(islands.length)];
            for (int i = 0; i < 2; i++) {
                double x = pick[0] + (rng.nextDouble() - 0.5) * 6;
                double y = pick[1] + 1 + rng.nextDouble() * 4;
                double z = pick[2] + (rng.nextDouble() - 0.5) * 6;
                w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.003);
            }
        }
    }
}
