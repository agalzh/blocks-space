package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OverviewCommand implements CommandExecutor {

    private final WsClient ws;

    public OverviewCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ws.sendOverview(sender);
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.95F, 1.4F);
        }
        return true;
    }
}
