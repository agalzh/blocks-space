package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GeminiCommand implements CommandExecutor {

    private final WsClient ws;

    public GeminiCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ws.sendGeminiInfo(sender);
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8F, 1.6F);
        }
        return true;
    }
}
