package com.unknown.stack;

import com.unknown.stack.commands.LoadMockCommand;
import com.unknown.stack.commands.UploadCommand;
import com.unknown.stack.net.WsClient;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;

public class StackPlugin extends JavaPlugin {

    private static final String DEFAULT_WS_URL = "ws://host.docker.internal:8765";

    private WsClient wsClient;

    @Override
    public void onEnable() {
        getLogger().info("StackUnknown plugin enabled (phase 3).");

        SceneRenderer renderer = new SceneRenderer(getLogger());

        registerExecutor("loadmock", new LoadMockCommand(this));

        String wsUrl = System.getenv().getOrDefault("STACK_WS_URL", DEFAULT_WS_URL);
        try {
            wsClient = new WsClient(this, new URI(wsUrl), renderer);
            registerExecutor("upload", new UploadCommand(wsClient));
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                getLogger().info("WS connecting to " + wsUrl);
                wsClient.connect();
            });
        } catch (URISyntaxException e) {
            getLogger().severe("Invalid WS URL '" + wsUrl + "': " + e.getMessage());
        }
    }

    private void registerExecutor(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("/" + name + " missing from plugin.yml; skipping registration.");
            return;
        }
        c.setExecutor(exec);
    }

    @Override
    public void onDisable() {
        if (wsClient != null) {
            wsClient.shutdown();
        }
        getLogger().info("StackUnknown plugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ping")) {
            sender.sendMessage("§astack-unknown pong (phase 0 — plugin loaded OK)");
            return true;
        }
        return false;
    }
}
