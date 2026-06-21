package com.unknown.stack;

import com.unknown.stack.commands.CenterCommand;
import com.unknown.stack.commands.LoadMockCommand;
import com.unknown.stack.commands.OverviewCommand;
import com.unknown.stack.commands.ResetCommand;
import com.unknown.stack.commands.UploadCommand;
import com.unknown.stack.commands.VisualizeCommand;
import com.unknown.stack.interact.ActionExecutor;
import com.unknown.stack.interact.HoverLineTask;
import com.unknown.stack.interact.SidebarHud;
import com.unknown.stack.net.WsClient;
import com.unknown.stack.render.SceneRegistry;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;

public class StackPlugin extends JavaPlugin implements Listener {

    private static final String DEFAULT_WS_URL = "ws://host.docker.internal:8765";
    private static final long NOON_TICKS = 6000L;

    private WsClient wsClient;
    private SidebarHud hud;

    @Override
    public void onEnable() {
        getLogger().info("StackUnknown plugin enabled (phase 4 + hud).");

        SceneRegistry registry = new SceneRegistry();
        SceneRenderer renderer = new SceneRenderer(getLogger(), registry);
        hud = new SidebarHud();

        registerExecutor("loadmock", new LoadMockCommand(this, renderer));
        registerExecutor("reset", new ResetCommand(this, registry));
        registerExecutor("center", new CenterCommand(registry));

        HoverLineTask.start(this, registry, hud);

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskLater(this, this::freezeNoon, 20L);

        ActionExecutor actionExecutor = new ActionExecutor(this, registry);

        String wsUrl = System.getenv().getOrDefault("STACK_WS_URL", DEFAULT_WS_URL);
        try {
            wsClient = new WsClient(this, new URI(wsUrl), renderer, actionExecutor);
            registerExecutor("upload", new UploadCommand(wsClient));
            registerExecutor("overview", new OverviewCommand(wsClient));
            registerExecutor("visualize", new VisualizeCommand(wsClient));
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                getLogger().info("WS connecting to " + wsUrl);
                wsClient.connect();
            });
        } catch (URISyntaxException e) {
            getLogger().severe("Invalid WS URL '" + wsUrl + "': " + e.getMessage());
        }
    }

    private void freezeNoon() {
        for (World w : Bukkit.getWorlds()) {
            try {
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                w.setTime(NOON_TICKS);
                w.setStorm(false);
                w.setThundering(false);
                getLogger().info("World '" + w.getName() + "' frozen at noon");
            } catch (RuntimeException e) {
                getLogger().warning("freezeNoon failed for " + w.getName() + ": " + e.getMessage());
            }
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

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (hud != null) hud.removePlayer(e.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        if (hud != null) hud.shutdown();
        if (wsClient != null) wsClient.shutdown();
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
