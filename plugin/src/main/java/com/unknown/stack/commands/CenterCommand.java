package com.unknown.stack.commands;

import com.unknown.stack.render.SceneRegistry;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CenterCommand implements CommandExecutor {

    private static final int OVERHEAD_OFFSET = 25;

    private final SceneRegistry registry;

    public CenterCommand(SceneRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§e/center is for players");
            return true;
        }
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null || snap.globalCentroid == null) {
            sender.sendMessage("§e/upload a dataset first");
            return true;
        }
        World world = player.getWorld() != null ? player.getWorld() : SceneRenderer.defaultWorld();
        if (world == null) {
            sender.sendMessage("§cno world");
            return true;
        }

        Vector c = snap.globalCentroid;
        double cx = c.getBlockX() + 0.5;
        double cy = c.getBlockY() + 0.5;
        double cz = c.getBlockZ() + 0.5;

        Location to = new Location(world, cx, cy + OVERHEAD_OFFSET, cz);
        to.setDirection(new Vector(0.001, -1.0, 0.001));

        if (player.getGameMode() == GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SPECTATOR);
        } else if (player.getGameMode() == GameMode.CREATIVE) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        player.teleport(to);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        sender.sendMessage(String.format(
                "§atp to centroid @ (%d, %d, %d), %d blocks overhead",
                c.getBlockX(), c.getBlockY(), c.getBlockZ(), OVERHEAD_OFFSET));
        return true;
    }
}
