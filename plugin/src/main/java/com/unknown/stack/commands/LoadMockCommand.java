package com.unknown.stack.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LoadMockCommand implements CommandExecutor {

    private static final String DEFAULT_DIR = "/data/samples/";
    private static final Material CLUSTER_MEAN_MATERIAL = Material.BEACON;

    private final JavaPlugin plugin;

    public LoadMockCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /loadmock <path>  (relative paths resolve under " + DEFAULT_DIR + ")");
            return true;
        }

        Path file = resolvePath(args[0]);
        if (!Files.isRegularFile(file)) {
            sender.sendMessage("§cFile not found: " + file);
            return true;
        }

        JSONObject scene;
        try {
            String raw = Files.readString(file);
            scene = new JSONObject(raw);
        } catch (IOException e) {
            sender.sendMessage("§cFailed to read " + file + ": " + e.getMessage());
            return true;
        } catch (RuntimeException e) {
            sender.sendMessage("§cInvalid JSON in " + file + ": " + e.getMessage());
            return true;
        }

        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage("§cNo world available to render into.");
            return true;
        }

        int[][] bounds = readBounds(scene);
        int placed = renderPoints(world, scene.optJSONArray("points"), bounds, sender);
        int means  = renderClusterMeans(world, scene.optJSONArray("clusters"), bounds, sender);

        String dsName = scene.optJSONObject("dataset") != null
                ? scene.optJSONObject("dataset").optString("name", "?")
                : "?";
        sender.sendMessage(String.format(
                "§aloadmock: dataset=%s, placed %d point blocks + %d cluster means from %s",
                dsName, placed, means, file.getFileName()));
        plugin.getLogger().info(String.format(
                "loadmock OK file=%s points=%d means=%d", file, placed, means));
        return true;
    }

    private Path resolvePath(String raw) {
        if (raw.startsWith("/") || (raw.length() > 1 && raw.charAt(1) == ':')) {
            return Paths.get(raw);
        }
        return Paths.get(DEFAULT_DIR, raw);
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getWorld();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private int[][] readBounds(JSONObject scene) {
        JSONObject b = scene.optJSONObject("bounds");
        if (b == null) {
            return new int[][] { {0, 4, 0}, {128, 60, 128} };
        }
        return new int[][] { readCoord(b.getJSONArray("min")), readCoord(b.getJSONArray("max")) };
    }

    private int[] readCoord(JSONArray a) {
        return new int[] { a.getInt(0), a.getInt(1), a.getInt(2) };
    }

    private boolean withinBounds(int[] pos, int[][] bounds) {
        for (int i = 0; i < 3; i++) {
            if (pos[i] < bounds[0][i] || pos[i] > bounds[1][i]) return false;
        }
        return true;
    }

    private int renderPoints(World world, JSONArray points, int[][] bounds, CommandSender sender) {
        if (points == null) return 0;
        int placed = 0, skipped = 0;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            int[] pos = readCoord(p.getJSONArray("pos"));
            if (!withinBounds(pos, bounds)) { skipped++; continue; }
            Material m = Material.matchMaterial(p.getString("block"));
            if (m == null || !m.isBlock()) { skipped++; continue; }
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            block.setType(m, false);
            placed++;
        }
        if (skipped > 0) {
            sender.sendMessage("§7  (" + skipped + " points skipped: out of bounds or unknown block)");
        }
        return placed;
    }

    private int renderClusterMeans(World world, JSONArray clusters, int[][] bounds, CommandSender sender) {
        if (clusters == null) return 0;
        int placed = 0, skipped = 0;
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject c = clusters.getJSONObject(i);
            int[] pos = readCoord(c.getJSONArray("mean"));
            if (!withinBounds(pos, bounds)) { skipped++; continue; }
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(CLUSTER_MEAN_MATERIAL, false);
            placed++;
        }
        if (skipped > 0) {
            sender.sendMessage("§7  (" + skipped + " cluster means skipped: out of bounds)");
        }
        return placed;
    }
}
