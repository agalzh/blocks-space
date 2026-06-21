package com.unknown.stack.commands;

import com.unknown.stack.render.SceneRenderer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LoadMockCommand implements CommandExecutor {

    private static final String DEFAULT_DIR = "/data/samples/";

    private final JavaPlugin plugin;
    private final SceneRenderer renderer;

    public LoadMockCommand(JavaPlugin plugin, SceneRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
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
            scene = new JSONObject(Files.readString(file));
        } catch (IOException e) {
            sender.sendMessage("§cFailed to read " + file + ": " + e.getMessage());
            return true;
        } catch (RuntimeException e) {
            sender.sendMessage("§cInvalid JSON in " + file + ": " + e.getMessage());
            return true;
        }

        World world = sender instanceof Player p ? p.getWorld() : SceneRenderer.defaultWorld();
        SceneRenderer.Result r = renderer.render(scene, world);

        sender.sendMessage(String.format(
                "§aloadmock: dataset=%s, placed %d point blocks + %d cluster means from %s",
                r.datasetName, r.pointsPlaced, r.means, file.getFileName()));
        if (r.skipped > 0) {
            sender.sendMessage("§7  (" + r.skipped + " points skipped: out of bounds or unknown block)");
        }
        plugin.getLogger().info(String.format(
                "loadmock OK file=%s points=%d means=%d skipped=%d",
                file, r.pointsPlaced, r.means, r.skipped));
        return true;
    }

    private Path resolvePath(String raw) {
        if (raw.startsWith("/") || (raw.length() > 1 && raw.charAt(1) == ':')) {
            return Paths.get(raw);
        }
        return Paths.get(DEFAULT_DIR, raw);
    }
}
