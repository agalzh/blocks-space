package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HoverLineTask extends BukkitRunnable {

    private static final int TARGET_RANGE = 50;
    private static final double STEP = 0.4;
    private static final long PERIOD_TICKS = 5L;
    private static final int FEATURE_LINES = 3;
    private static final int FEATURE_HUD_LIMIT = 8;

    private final JavaPlugin plugin;
    private final SceneRegistry registry;
    private final SidebarHud hud;
    private final AxisManager axes;

    private final Particle.DustOptions clusterDust =
            new Particle.DustOptions(Color.fromRGB(0xFFAA00), 1.0F);

    public HoverLineTask(JavaPlugin plugin, SceneRegistry registry, SidebarHud hud, AxisManager axes) {
        this.plugin = plugin;
        this.registry = registry;
        this.hud = hud;
        this.axes = axes;
    }

    public static HoverLineTask start(JavaPlugin plugin, SceneRegistry registry,
                                       SidebarHud hud, AxisManager axes) {
        HoverLineTask t = new HoverLineTask(plugin, registry, hud, axes);
        t.runTaskTimer(plugin, 20L, PERIOD_TICKS);
        return t;
    }

    @Override
    public void run() {
        SceneRegistry.Snapshot snap = registry.get();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (snap == null) { hud.hide(p); continue; }
                tickPlayer(p, snap);
            } catch (RuntimeException e) {
                plugin.getLogger().fine("hover tick failed for " + p.getName() + ": " + e.getMessage());
            }
        }
    }

    private void tickPlayer(Player player, SceneRegistry.Snapshot snap) {
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        if (target == null) { hud.hide(player); return; }
        int tx = target.getX(), ty = target.getY(), tz = target.getZ();
        Vector targetCenter = new Vector(tx + 0.5, ty + 0.5, tz + 0.5);

        if (isCentroidBlock(snap, tx, ty, tz)) {
            showCentroidHud(player, snap);
            drawCentroidSpokes(player, targetCenter, snap);
            return;
        }

        for (Map.Entry<Integer, Vector> e : snap.clusterMean.entrySet()) {
            Vector mean = e.getValue();
            if (mean.getBlockX() == tx && mean.getBlockY() == ty && mean.getBlockZ() == tz) {
                if (snap.globalCentroid == null) { hud.hide(player); return; }
                Vector centroidCenter = blockCenter(snap.globalCentroid);
                double dist = targetCenter.distance(centroidCenter);
                showMeanHud(player, snap, e.getKey(), dist);
                drawEndRodLine(player, targetCenter, centroidCenter);
                return;
            }
        }

        AxisManager.Axis axis = axes != null ? axes.axisAt(tx, ty, tz) : null;
        if (axis != null) {
            showAxisHud(player, snap, axis, tx, ty, tz);
            return;
        }

        BlockVector key = new BlockVector(tx, ty, tz);
        Integer clusterId = snap.blockToCluster.get(key);
        if (clusterId != null) {
            Vector mean = snap.clusterMean.get(clusterId);
            if (mean == null) { hud.hide(player); return; }
            Vector meanCenter = blockCenter(mean);
            double dist = targetCenter.distance(meanCenter);
            showPointHud(player, snap, clusterId, key, dist);
            drawDustLine(player, targetCenter, meanCenter);
            return;
        }

        hud.hide(player);
    }

    private void showPointHud(Player p, SceneRegistry.Snapshot snap, int clusterId,
                              BlockVector pos, double dist) {
        List<String> L = new ArrayList<>();
        L.add(ChatColor.YELLOW + "Dataset: " + ChatColor.WHITE + snap.datasetName);
        L.add(ChatColor.YELLOW + "Cluster: " + ChatColor.WHITE + clusterId);
        L.add(ChatColor.YELLOW + "Dist→mean: " + ChatColor.WHITE + fmt(dist) + " b");
        Double o = snap.blockOutlier.get(pos);
        if (o != null) {
            String c = (o > snap.outlierThreshold) ? ChatColor.RED.toString() : ChatColor.GREEN.toString();
            L.add(ChatColor.YELLOW + "Outlier: " + c + fmt(o)
                    + ChatColor.GRAY + " / " + fmt(snap.outlierThreshold));
        }
        Integer cs = snap.clusterSize.get(clusterId);
        if (cs != null) L.add(ChatColor.YELLOW + "Cluster size: " + ChatColor.WHITE + cs);

        Map<String, Double> feats = snap.blockFeatures.get(pos);
        if (feats != null && snap.featureNames.size() <= FEATURE_HUD_LIMIT) {
            int shown = 0;
            for (String name : snap.featureNames) {
                if (shown >= FEATURE_LINES) break;
                Double v = feats.get(name);
                if (v == null || Double.isNaN(v)) continue;
                L.add(ChatColor.AQUA + abbreviate(name, 14) + ": " + ChatColor.WHITE + fmt(v));
                shown++;
            }
        }
        hud.update(p, L);
    }

    private void showMeanHud(Player p, SceneRegistry.Snapshot snap, int clusterId, double dist) {
        List<String> L = new ArrayList<>();
        L.add(ChatColor.YELLOW + "Dataset: " + ChatColor.WHITE + snap.datasetName);
        L.add(ChatColor.GOLD + "Cluster mean " + ChatColor.WHITE + "#" + clusterId);
        L.add(ChatColor.YELLOW + "Dist→centroid: " + ChatColor.WHITE + fmt(dist) + " b");
        Integer cs = snap.clusterSize.get(clusterId);
        if (cs != null) L.add(ChatColor.YELLOW + "Cluster size: " + ChatColor.WHITE + cs);
        hud.update(p, L);
    }

    private void showCentroidHud(Player p, SceneRegistry.Snapshot snap) {
        List<String> L = new ArrayList<>();
        L.add(ChatColor.YELLOW + "Dataset: " + ChatColor.WHITE + snap.datasetName);
        L.add(ChatColor.GOLD + "Global centroid");
        L.add(ChatColor.YELLOW + "Clusters: " + ChatColor.WHITE + snap.clusterMean.size());
        L.add(ChatColor.YELLOW + "Points: " + ChatColor.WHITE + snap.totalPoints);
        L.add(ChatColor.YELLOW + "Outlier cutoff: " + ChatColor.WHITE + fmt(snap.outlierThreshold));
        if (!snap.featureNames.isEmpty()) {
            L.add(ChatColor.AQUA + "Features: " + ChatColor.WHITE + snap.featureNames.size());
        }
        hud.update(p, L);
    }

    private void showAxisHud(Player p, SceneRegistry.Snapshot snap, AxisManager.Axis axis,
                              int x, int y, int z) {
        List<String> L = new ArrayList<>();
        L.add(ChatColor.GOLD + "Axis " + axis.label
                + ChatColor.GRAY + " (projection)");
        Vector c = snap.globalCentroid;
        if (c != null) {
            int delta = switch (axis) {
                case X -> x - c.getBlockX();
                case Y -> y - c.getBlockY();
                case Z -> z - c.getBlockZ();
            };
            L.add(ChatColor.YELLOW + "Offset: " + ChatColor.WHITE + (delta >= 0 ? "+" : "") + delta + " b");
        }
        L.add(ChatColor.YELLOW + "Bounds: " + ChatColor.WHITE
                + snap.bounds[0][axis.ordinal()] + " → " + snap.bounds[1][axis.ordinal()]);
        L.add(ChatColor.YELLOW + "Dataset: " + ChatColor.WHITE + snap.datasetName);
        L.add(ChatColor.YELLOW + "Dims: " + ChatColor.WHITE + snap.featureNames.size());
        int shown = 0;
        for (String name : snap.featureNames) {
            if (shown >= 3) break;
            L.add(ChatColor.AQUA + "· " + abbreviate(name, 18));
            shown++;
        }
        if (snap.featureNames.size() > shown) {
            L.add(ChatColor.GRAY + "+ " + (snap.featureNames.size() - shown) + " more");
        }
        hud.update(p, L);
    }

    private void drawCentroidSpokes(Player p, Vector centroidCenter, SceneRegistry.Snapshot snap) {
        for (Vector mean : snap.clusterMean.values()) {
            drawEndRodLine(p, centroidCenter, blockCenter(mean));
        }
    }

    private static Vector blockCenter(Vector v) {
        return new Vector(v.getBlockX() + 0.5, v.getBlockY() + 0.5, v.getBlockZ() + 0.5);
    }

    private static boolean isCentroidBlock(SceneRegistry.Snapshot snap, int x, int y, int z) {
        Vector c = snap.globalCentroid;
        return c != null && c.getBlockX() == x && c.getBlockY() == y && c.getBlockZ() == z;
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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
