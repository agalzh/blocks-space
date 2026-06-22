package com.unknown.stack.commands;

import com.unknown.stack.interact.AxisManager;
import com.unknown.stack.render.SceneRegistry;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ResetCommand implements CommandExecutor {

    private static final int BLOCKS_PER_TICK = 32768;
    private static final int[][] DEFAULT_BOUNDS = { {0, 4, 0}, {128, 60, 128} };

    private final JavaPlugin plugin;
    private final SceneRegistry registry;
    private final AxisManager axes;
    private volatile boolean running = false;

    public ResetCommand(JavaPlugin plugin, SceneRegistry registry, AxisManager axes) {
        this.plugin = plugin;
        this.registry = registry;
        this.axes = axes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (running) {
            sender.sendMessage("§ereset already running");
            return true;
        }
        World world = sender instanceof Player p ? p.getWorld() : SceneRenderer.defaultWorld();
        if (world == null) {
            sender.sendMessage("§cno world to reset");
            return true;
        }

        int[][] bounds;
        SceneRegistry.Snapshot snap = registry.get();
        if (snap != null) {
            bounds = snap.bounds;
        } else {
            bounds = DEFAULT_BOUNDS;
        }

        long total = (long) (bounds[1][0] - bounds[0][0] + 1)
                * (bounds[1][1] - bounds[0][1] + 1)
                * (bounds[1][2] - bounds[0][2] + 1);
        sender.sendMessage(String.format("§7reset: clearing ~%d blocks in %d ticks max...",
                total, (total + BLOCKS_PER_TICK - 1) / BLOCKS_PER_TICK));

        running = true;
        new ResetTask(world, bounds, sender).runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private class ResetTask extends BukkitRunnable {
        private final World world;
        private final int[][] bounds;
        private final CommandSender feedback;
        private int x, y, z;
        private long cleared = 0;

        ResetTask(World world, int[][] bounds, CommandSender feedback) {
            this.world = world;
            this.bounds = bounds;
            this.feedback = feedback;
            this.x = bounds[0][0];
            this.y = bounds[0][1];
            this.z = bounds[0][2];
        }

        @Override
        public void run() {
            int budget = BLOCKS_PER_TICK;
            Material air = Material.AIR;
            while (budget-- > 0) {
                world.getBlockAt(x, y, z).setType(air, false);
                cleared++;
                z++;
                if (z > bounds[1][2]) {
                    z = bounds[0][2];
                    y++;
                    if (y > bounds[1][1]) {
                        y = bounds[0][1];
                        x++;
                        if (x > bounds[1][0]) {
                            done();
                            return;
                        }
                    }
                }
            }
        }

        private void done() {
            cancel();
            running = false;
            registry.clear();
            if (axes != null) axes.clearTracking();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.getBlockAt(dx, 64, dz).setType(Material.GLASS, false);
                }
            }
            feedback.sendMessage("§areset done: cleared " + cleared + " blocks");
            plugin.getLogger().info("reset OK cleared=" + cleared);
            if (feedback instanceof Player p) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.0F);
            }
        }
    }
}
