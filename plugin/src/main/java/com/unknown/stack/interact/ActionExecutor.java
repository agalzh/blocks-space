package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private static final int HIGHLIGHT_TICKS = 100;
    private static final long HIGHLIGHT_PERIOD = 5L;

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
                String particleId = a.optString("particle");
                Particle p = mapParticle(particleId);
                if (p == null) {
                    feedback.sendMessage(ChatColor.YELLOW + "skipped highlight: bad particle " + particleId);
                    return 0;
                }
                startHighlight(world, targets, p);
                return targets.size();
            }
            default -> {
                feedback.sendMessage(ChatColor.YELLOW + "skipped: unknown action " + action);
                return 0;
            }
        }
    }

    private Predicate<Map.Entry<BlockVector, Integer>> parseSelector(String selector, SceneRegistry.Snapshot snap) {
        if (selector == null) return null;
        if (selector.equals("all")) {
            return e -> true;
        }
        if (selector.startsWith("cluster_id=")) {
            try {
                int target = Integer.parseInt(selector.substring("cluster_id=".length()));
                return e -> e.getValue() == target;
            } catch (NumberFormatException ex) {
                return null;
            }
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
            default -> null;
        };
    }

    private void startHighlight(World world, List<BlockVector> targets, Particle particle) {
        new BukkitRunnable() {
            int ticksLeft = HIGHLIGHT_TICKS;
            @Override
            public void run() {
                if (ticksLeft <= 0) { cancel(); return; }
                ticksLeft -= HIGHLIGHT_PERIOD;
                for (BlockVector v : targets) {
                    Vector center = new Vector(v.getBlockX() + 0.5, v.getBlockY() + 1.2, v.getBlockZ() + 0.5);
                    if (particle == Particle.REDSTONE) {
                        world.spawnParticle(particle, center.getX(), center.getY(), center.getZ(),
                                4, 0.15, 0.15, 0.15, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFAA00), 1.2F));
                    } else {
                        world.spawnParticle(particle, center.getX(), center.getY(), center.getZ(),
                                4, 0.15, 0.15, 0.15, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, HIGHLIGHT_PERIOD);
    }
}
