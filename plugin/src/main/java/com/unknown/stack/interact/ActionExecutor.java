package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ActionExecutor {

    private static final long PARTICLE_PERIOD = 5L;
    private static final int DEFAULT_DURATION_SEC = 5;
    private static final int MIN_DURATION_SEC = 1;
    private static final int MAX_DURATION_SEC = 60;

    private final JavaPlugin plugin;
    private final SceneRegistry registry;

    public ActionExecutor(JavaPlugin plugin, SceneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void apply(JSONArray actions, String query, CommandSender feedback) {
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null) {
            feedback.sendMessage(ChatColor.RED + "no scene loaded; /upload first");
            return;
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            feedback.sendMessage(ChatColor.RED + "no world");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.GRAY + "applying " + actions.length()
                + " action(s) for: " + ChatColor.WHITE + query);
        int totalAffected = 0;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject a = actions.optJSONObject(i);
            if (a == null) continue;
            totalAffected += applyOne(a, snap, world, feedback);
        }
        plugin.getLogger().info("visualize done: " + totalAffected + " points affected, query="
                + query);
        Bukkit.broadcastMessage(ChatColor.GREEN + "visualize done: " + totalAffected + " points affected");
    }

    private int applyOne(JSONObject a, SceneRegistry.Snapshot snap, World world, CommandSender feedback) {
        String action = a.optString("action");
        String selector = a.optString("selector");
        Predicate<Map.Entry<BlockVector, Integer>> match = parseSelector(selector, snap);
        if (match == null) {
            feedback.sendMessage(ChatColor.YELLOW + "skipped: bad selector " + selector);
            return 0;
        }

        List<BlockVector> targets = new ArrayList<>();
        for (Map.Entry<BlockVector, Integer> e : snap.blockToCluster.entrySet()) {
            if (match.test(e)) targets.add(e.getKey());
        }
        if (targets.isEmpty()) return 0;

        int durationSec = clampDuration(a.optInt("duration_sec", DEFAULT_DURATION_SEC));
        long durationTicks = durationSec * 20L;

        switch (action) {
            case "recolor" -> {
                String blockId = a.optString("block");
                Material m = Material.matchMaterial(blockId);
                if (m == null || !m.isBlock()) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped recolor: bad block " + blockId);
                    return 0;
                }
                for (BlockVector v : targets) {
                    world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ()).setType(m, false);
                }
                return targets.size();
            }
            case "hide" -> {
                for (BlockVector v : targets) {
                    world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ()).setType(Material.AIR, false);
                }
                return targets.size();
            }
            case "highlight" -> {
                Particle p = mapParticle(a.optString("particle"));
                if (p == null) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped highlight: bad particle " + a.optString("particle"));
                    return 0;
                }
                startHighlight(world, targets, p, durationTicks);
                return targets.size();
            }
            case "pulse" -> {
                Particle p = mapParticle(a.optString("particle"));
                if (p == null) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped pulse: bad particle " + a.optString("particle"));
                    return 0;
                }
                startPulse(world, targets, p, durationTicks);
                return targets.size();
            }
            case "beam" -> {
                Particle p = mapParticle(a.optString("particle"));
                if (p == null) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped beam: bad particle " + a.optString("particle"));
                    return 0;
                }
                startBeam(world, targets, p, durationTicks);
                return targets.size();
            }
            case "connect" -> {
                Particle p = mapParticle(a.optString("particle"));
                if (p == null) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped connect: bad particle " + a.optString("particle"));
                    return 0;
                }
                startConnect(world, snap, targets, p, durationTicks);
                return targets.size();
            }
            default -> {
                feedback.sendMessage(ChatColor.YELLOW + "skipped: unknown action " + action);
                return 0;
            }
        }
    }

    private static int clampDuration(int sec) {
        return Math.max(MIN_DURATION_SEC, Math.min(MAX_DURATION_SEC, sec));
    }

    private Predicate<Map.Entry<BlockVector, Integer>> parseSelector(String selector, SceneRegistry.Snapshot snap) {
        if (selector == null) return null;
        if (selector.equals("all")) {
            return e -> true;
        }
        if (selector.startsWith("cluster_id!=")) {
            try {
                int target = Integer.parseInt(selector.substring("cluster_id!=".length()));
                return e -> e.getValue() != target;
            } catch (NumberFormatException ex) { return null; }
        }
        if (selector.startsWith("cluster_id=")) {
            try {
                int target = Integer.parseInt(selector.substring("cluster_id=".length()));
                return e -> e.getValue() == target;
            } catch (NumberFormatException ex) { return null; }
        }
        if (selector.startsWith("outlier_score>=")) {
            double thr = parseDouble(selector.substring("outlier_score>=".length()));
            if (Double.isNaN(thr)) return null;
            return e -> {
                Double os = snap.blockOutlier.get(e.getKey());
                return os != null && os >= thr;
            };
        }
        if (selector.startsWith("outlier_score<=")) {
            double thr = parseDouble(selector.substring("outlier_score<=".length()));
            if (Double.isNaN(thr)) return null;
            return e -> {
                Double os = snap.blockOutlier.get(e.getKey());
                return os != null && os <= thr;
            };
        }
        if (selector.startsWith("outlier_score>")) {
            double thr = parseDouble(selector.substring("outlier_score>".length()));
            if (Double.isNaN(thr)) return null;
            return e -> {
                Double os = snap.blockOutlier.get(e.getKey());
                return os != null && os > thr;
            };
        }
        if (selector.startsWith("outlier_score<")) {
            double thr = parseDouble(selector.substring("outlier_score<".length()));
            if (Double.isNaN(thr)) return null;
            return e -> {
                Double os = snap.blockOutlier.get(e.getKey());
                return os != null && os < thr;
            };
        }
        return null;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    private static Particle mapParticle(String id) {
        if (id == null) return null;
        return switch (id) {
            case "flame" -> Particle.FLAME;
            case "soul_fire_flame" -> Particle.SOUL_FIRE_FLAME;
            case "heart" -> Particle.HEART;
            case "happy_villager" -> Particle.VILLAGER_HAPPY;
            case "end_rod" -> Particle.END_ROD;
            case "dust" -> Particle.REDSTONE;
            case "crit" -> Particle.CRIT;
            case "glow" -> Particle.GLOW;
            case "totem" -> Particle.TOTEM;
            case "witch" -> Particle.SPELL_WITCH;
            default -> null;
        };
    }

    private void spawnAt(World world, Vector center, Particle particle, int count, double spread) {
        if (particle == Particle.REDSTONE) {
            world.spawnParticle(particle, center.getX(), center.getY(), center.getZ(),
                    count, spread, spread, spread, 0,
                    new Particle.DustOptions(Color.fromRGB(0xFFAA00), 1.2F));
        } else {
            world.spawnParticle(particle, center.getX(), center.getY(), center.getZ(),
                    count, spread, spread, spread, 0.01);
        }
    }

    private void startHighlight(World world, List<BlockVector> targets, Particle particle, long durationTicks) {
        new BukkitRunnable() {
            long ticksLeft = durationTicks;
            @Override
            public void run() {
                if (ticksLeft <= 0) { cancel(); return; }
                ticksLeft -= PARTICLE_PERIOD;
                for (BlockVector v : targets) {
                    Vector center = new Vector(v.getBlockX() + 0.5, v.getBlockY() + 1.2, v.getBlockZ() + 0.5);
                    spawnAt(world, center, particle, 4, 0.15);
                }
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_PERIOD);
    }

    private void startPulse(World world, List<BlockVector> targets, Particle particle, long durationTicks) {
        new BukkitRunnable() {
            long elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { cancel(); return; }
                double phase = (elapsed % 40) / 40.0;
                double radius = 0.4 + 1.0 * Math.sin(phase * Math.PI);
                int ring = 12;
                for (BlockVector v : targets) {
                    double cx = v.getBlockX() + 0.5;
                    double cy = v.getBlockY() + 1.1;
                    double cz = v.getBlockZ() + 0.5;
                    for (int i = 0; i < ring; i++) {
                        double a = (Math.PI * 2 * i) / ring;
                        double px = cx + Math.cos(a) * radius;
                        double pz = cz + Math.sin(a) * radius;
                        spawnAt(world, new Vector(px, cy, pz), particle, 1, 0.0);
                    }
                }
                elapsed += PARTICLE_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_PERIOD);
    }

    private void startBeam(World world, List<BlockVector> targets, Particle particle, long durationTicks) {
        new BukkitRunnable() {
            long elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { cancel(); return; }
                for (BlockVector v : targets) {
                    double cx = v.getBlockX() + 0.5;
                    double cz = v.getBlockZ() + 0.5;
                    int yBase = v.getBlockY() + 1;
                    for (int dy = 0; dy < 12; dy++) {
                        spawnAt(world, new Vector(cx, yBase + dy, cz), particle, 1, 0.05);
                    }
                }
                elapsed += PARTICLE_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_PERIOD);
    }

    private void startConnect(World world, SceneRegistry.Snapshot snap, List<BlockVector> targets,
                              Particle particle, long durationTicks) {
        new BukkitRunnable() {
            long elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { cancel(); return; }
                for (BlockVector v : targets) {
                    Integer cid = snap.blockToCluster.get(v);
                    if (cid == null) continue;
                    Vector mean = snap.clusterMean.get(cid);
                    if (mean == null) continue;
                    Vector a = new Vector(v.getBlockX() + 0.5, v.getBlockY() + 0.5, v.getBlockZ() + 0.5);
                    Vector b = new Vector(mean.getBlockX() + 0.5, mean.getBlockY() + 0.5, mean.getBlockZ() + 0.5);
                    Color color = snap.clusterColor.get(cid);
                    drawConnectLine(world, a, b, particle, color);
                }
                elapsed += PARTICLE_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_PERIOD);
    }

    private void drawConnectLine(World world, Vector a, Vector b, Particle particle, Color color) {
        Vector d = b.clone().subtract(a);
        double dist = d.length();
        if (dist < 0.001) return;
        int steps = Math.max(1, (int) Math.ceil(dist / 0.6));
        Vector unit = d.multiply(1.0 / dist);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps * dist;
            double px = a.getX() + unit.getX() * t;
            double py = a.getY() + unit.getY() * t;
            double pz = a.getZ() + unit.getZ() * t;
            if (particle == Particle.REDSTONE) {
                Color c = color != null ? color : Color.fromRGB(0xFFAA00);
                world.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(c, 1.0F));
            } else {
                world.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0.01);
            }
        }
    }
}
