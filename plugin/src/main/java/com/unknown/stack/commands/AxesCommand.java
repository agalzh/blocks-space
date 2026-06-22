package com.unknown.stack.commands;

import com.unknown.stack.interact.AxisManager;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AxesCommand implements CommandExecutor {

    private final AxisManager axes;

    public AxesCommand(AxisManager axes) {
        this.axes = axes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "toggle";
        boolean nowVisible;
        switch (mode) {
            case "show", "on" -> {
                if (!axes.show()) {
                    sender.sendMessage("§e/upload a dataset first — axes need a centroid");
                    return true;
                }
                nowVisible = true;
            }
            case "hide", "off" -> {
                axes.hide();
                nowVisible = false;
            }
            default -> {
                if (axes.isVisible()) {
                    axes.hide();
                    nowVisible = false;
                } else {
                    if (!axes.show()) {
                        sender.sendMessage("§e/upload a dataset first — axes need a centroid");
                        return true;
                    }
                    nowVisible = true;
                }
            }
        }

        sender.sendMessage(nowVisible
                ? "§aaxes shown: §cX §a/ §aY §a/ §9Z"
                : "§7axes hidden");
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(),
                    nowVisible ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                    0.95F, 1.0F);
        }
        return true;
    }
}
