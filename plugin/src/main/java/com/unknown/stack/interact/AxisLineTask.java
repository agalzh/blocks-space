package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class AxisLineTask extends BukkitRunnable {

    private static final int STEP = 2;
    private static final long PERIOD_TICKS = 10L;

    private final SceneRegistry registry;
    private final AxisManager axes;

    private final Particle.DustOptions xDust = new Particle.DustOptions(Color.fromRGB(0xFF5A55), 0.6F);
    private final Particle.DustOptions yDust = new Particle.DustOptions(Color.fromRGB(0x7CE055), 0.6F);
    private final Particle.DustOptions zDust = new Particle.DustOptions(Color.fromRGB(0x6AA0FF), 0.6F);

    public AxisLineTask(SceneRegistry registry, AxisManager axes) {
        this.registry = registry;
        this.axes = axes;
    }

    public static AxisLineTask start(JavaPlugin plugin, SceneRegistry registry, AxisManager axes) {
        AxisLineTask t = new AxisLineTask(registry, axes);
        t.runTaskTimer(plugin, 20L, PERIOD_TICKS);
        return t;
    }

    @Override
    public void run() {
        if (!axes.isVisible()) return;
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null || snap.globalCentroid == null) return;
        World world = SceneRenderer.defaultWorld();
        if (world == null) return;

        int cx = snap.globalCentroid.getBlockX();
        int cy = snap.globalCentroid.getBlockY();
        int cz = snap.globalCentroid.getBlockZ();
        int[][] b = snap.bounds;

        for (int x = b[0][0]; x <= b[1][0]; x += STEP) {
            world.spawnParticle(Particle.REDSTONE, x + 0.5, cy + 0.5, cz + 0.5,
                    1, 0, 0, 0, 0, xDust);
        }
        for (int y = b[0][1]; y <= b[1][1]; y += STEP) {
            world.spawnParticle(Particle.REDSTONE, cx + 0.5, y + 0.5, cz + 0.5,
                    1, 0, 0, 0, 0, yDust);
        }
        for (int z = b[0][2]; z <= b[1][2]; z += STEP) {
            world.spawnParticle(Particle.REDSTONE, cx + 0.5, cy + 0.5, z + 0.5,
                    1, 0, 0, 0, 0, zDust);
        }
    }
}
