package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

public class HoverLineTask extends BukkitRunnable {

    private static final int TARGET_RANGE = 50;
    private static final double STEP = 0.4;
    private static final long PERIOD_TICKS = 5L;

    private final JavaPlugin plugin;
    private final SceneRegistry registry;

    private final Particle.DustOptions clusterDust =
            new Particle.DustOptions(Color.fromRGB(0xFFAA00), 1.0F);

    public HoverLineTask(JavaPlugin plugin, SceneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public static HoverLineTask start(JavaPlugin plugin, SceneRegistry registry) {
        HoverLineTask t = new HoverLineTask(plugin, registry);
        t.runTaskTimer(plugin, 20L, PERIOD_TICKS);
        return t;
    }

    @Override
    public void run() {
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(p, snap);
            } catch (RuntimeException e) {
                plugin.getLogger().fine("hover tick failed for " + p.getName() + ": " + e.getMessage());
            }
        }
    }

    private void tickPlayer(Player player, SceneRegistry.Snapshot snap) {
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        if (target == null) return;
        int tx = target.getX(), ty = target.getY(), tz = target.getZ();
        Vector targetCenter = new Vector(tx + 0.5, ty + 0.5, tz + 0.5);

        Integer clusterId = snap.blockToCluster.get(new BlockVector(tx, ty, tz));
        if (clusterId != null) {
            Vector mean = snap.clusterMean.get(clusterId);
            if (mean == null) return;
            Vector dst = new Vector(mean.getBlockX() + 0.5, mean.getBlockY() + 0.5, mean.getBlockZ() + 0.5);
            drawDustLine(player, targetCenter, dst);
            return;
        }

        for (Vector mean : snap.clusterMean.values()) {
            if (mean.getBlockX() == tx && mean.getBlockY() == ty && mean.getBlockZ() == tz) {
                if (snap.globalCentroid == null) return;
                Vector centroid = snap.globalCentroid;
                Vector dst = new Vector(centroid.getBlockX() + 0.5,
                        centroid.getBlockY() + 0.5, centroid.getBlockZ() + 0.5);
                drawEndRodLine(player, targetCenter, dst);
                return;
            }
        }
    }

    private void drawDustLine(Player viewer, Vector a, Vector b) {
        Vector d = b.clone().subtract(a);
        double dist = d.length();
        if (dist < 0.001) return;
        int steps = Math.max(1, (int) Math.ceil(dist / STEP));
        Vector unit = d.multiply(1.0 / dist);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps * dist;
            double px = a.getX() + unit.getX() * t;
            double py = a.getY() + unit.getY() * t;
            double pz = a.getZ() + unit.getZ() * t;
            viewer.spawnParticle(Particle.REDSTONE, px, py, pz, 1, 0, 0, 0, 0, clusterDust);
        }
    }

    private void drawEndRodLine(Player viewer, Vector a, Vector b) {
        Vector d = b.clone().subtract(a);
        double dist = d.length();
        if (dist < 0.001) return;
        int steps = Math.max(1, (int) Math.ceil(dist / STEP));
        Vector unit = d.multiply(1.0 / dist);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps * dist;
            double px = a.getX() + unit.getX() * t;
            double py = a.getY() + unit.getY() * t;
            double pz = a.getZ() + unit.getZ() * t;
            viewer.spawnParticle(Particle.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
        }
    }
}
