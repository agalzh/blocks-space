package com.unknown.stack;

import com.unknown.stack.commands.AxesCommand;
import com.unknown.stack.commands.CenterCommand;
import com.unknown.stack.commands.LoadMockCommand;
import com.unknown.stack.commands.OverviewCommand;
import com.unknown.stack.commands.ResetCommand;
import com.unknown.stack.commands.UploadCommand;
import com.unknown.stack.commands.VisualizeCommand;
import com.unknown.stack.interact.ActionExecutor;
import com.unknown.stack.interact.AxisLineTask;
import com.unknown.stack.interact.AxisManager;
import com.unknown.stack.interact.HoverLineTask;
import com.unknown.stack.interact.NameplateManager;
import com.unknown.stack.interact.SidebarHud;
import com.unknown.stack.interact.SpawnPlatform;
import com.unknown.stack.net.WsClient;
import com.unknown.stack.render.SceneRegistry;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;

public class StackPlugin extends JavaPlugin implements Listener {

    private static final String DEFAULT_WS_URL = "ws://host.docker.internal:8765";
    private static final long NOON_TICKS = 6000L;

    private WsClient wsClient;
    private SidebarHud hud;
    private NameplateManager nameplate;

    @Override
    public void onEnable() {
        getLogger().info("StackUnknown plugin enabled (phase 6+).");

        SceneRegistry registry = new SceneRegistry();
        SceneRenderer renderer = new SceneRenderer(getLogger(), registry);
        AxisManager axes = new AxisManager(registry);
        renderer.setAxisManager(axes);
        hud = new SidebarHud();
        nameplate = new NameplateManager(this);

        registerExecutor("loadmock", new LoadMockCommand(this, renderer));
        registerExecutor("reset", new ResetCommand(this, registry, axes));
        registerExecutor("center", new CenterCommand(registry));
        registerExecutor("axes", new AxesCommand(axes));

        HoverLineTask.start(this, registry, hud, nameplate);
        AxisLineTask.start(this, registry, axes);

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            freezeNoon();
            SpawnPlatform.build(SceneRenderer.defaultWorld());
        }, 20L);

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
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            p.sendTitle(" ", "§6Stack Unknown §7· try §e/upload <path>", 5, 60, 15);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.9F);
            p.sendMessage("§7Commands: §e/upload §7·§e center §7·§e overview §7·§e visualize §7·§e axes §7·§e reset");
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        java.util.UUID id = e.getPlayer().getUniqueId();
        if (hud != null) hud.removePlayer(id);
        if (nameplate != null) nameplate.removePlayer(id);
    }

    @Override
    public void onDisable() {
        if (nameplate != null) nameplate.shutdown();
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
