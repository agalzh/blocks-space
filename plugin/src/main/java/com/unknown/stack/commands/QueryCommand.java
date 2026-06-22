package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QueryCommand implements CommandExecutor {

    private final WsClient ws;

    public QueryCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /query <question about the loaded dataset>");
            return true;
        }
        String question = String.join(" ", args).trim();
        ws.sendQuery(question, sender);
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.95F, 1.4F);
        }
        return true;
    }
}
