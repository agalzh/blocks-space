package com.unknown.stack;

import com.unknown.stack.commands.AxesCommand;
import com.unknown.stack.commands.CenterCommand;
import com.unknown.stack.commands.GeminiCommand;
import com.unknown.stack.commands.LoadMockCommand;
import com.unknown.stack.commands.OverviewCommand;
import com.unknown.stack.commands.QueryCommand;
import com.unknown.stack.commands.ResetCommand;
import com.unknown.stack.commands.UploadCommand;
import com.unknown.stack.commands.VisualizeCommand;
import com.unknown.stack.interact.ActionExecutor;
import com.unknown.stack.interact.AmbienceTask;
import com.unknown.stack.interact.AxisLineTask;
import com.unknown.stack.interact.AxisManager;
import com.unknown.stack.interact.HoverLineTask;
import com.unknown.stack.interact.NameplateManager;
import com.unknown.stack.interact.SidebarHud;
import com.unknown.stack.interact.SpawnPlatform;
import com.unknown.stack.interact.WorldDecor;
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
    private static final long NIGHT_TICKS = 22500L;

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

        HoverLineTask.start(this, registry, hud, nameplate, axes);
        AxisLineTask.start(this, registry, axes);
        AmbienceTask.start(this);

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            freezeNight();
            World world = SceneRenderer.defaultWorld();
            SpawnPlatform.build(world);
            WorldDecor.build(world);
        }, 20L);

        ActionExecutor actionExecutor = new ActionExecutor(this, registry);

        String wsUrl = System.getenv().getOrDefault("STACK_WS_URL", DEFAULT_WS_URL);
        try {
            wsClient = new WsClient(this, new URI(wsUrl), renderer, actionExecutor);
            registerExecutor("upload", new UploadCommand(wsClient));
            registerExecutor("overview", new OverviewCommand(wsClient));
            registerExecutor("visualize", new VisualizeCommand(wsClient));
            registerExecutor("query", new QueryCommand(wsClient));
            registerExecutor("gemini", new GeminiCommand(wsClient));
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                getLogger().info("WS connecting to " + wsUrl);
                wsClient.connect();
            });
        } catch (URISyntaxException e) {
            getLogger().severe("Invalid WS URL '" + wsUrl + "': " + e.getMessage());
        }
    }

    private void freezeNight() {
        for (World w : Bukkit.getWorlds()) {
            try {
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                w.setGameRule(GameRule.DO_INSOMNIA, false);
                w.setTime(NIGHT_TICKS);
                w.setStorm(false);
                w.setThundering(false);
                getLogger().info("World '" + w.getName() + "' frozen at night");
            } catch (RuntimeException e) {
                getLogger().warning("freezeNight failed for " + w.getName() + ": " + e.getMessage());
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
            p.sendTitle("§b§lBlocks Space", "§7Minecraft as a 3D data viewer", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 0.9F);
        }, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            p.sendMessage("§8§m                                                  ");
            p.sendMessage("§b§lBlocks Space §7— turn any dataset into a 3D world you can walk through.");
            p.sendMessage("§7Load    §8» §e/upload <path> [csv|json]");
            p.sendMessage("§7Explore §8» §e/center §7· §e/axes §7· §7hover any block");
            p.sendMessage("§7Ask AI  §8» §d/overview §7· §d/query <q> §7· §d/visualize <q>");
            p.sendMessage("§7Status  §8» §b/gemini §7(active model + fallbacks)");
            p.sendMessage("§7Manage  §8» §e/reset");
            p.sendMessage("§8§m                                                  ");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6F, 1.4F);
        }, 120L);
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
