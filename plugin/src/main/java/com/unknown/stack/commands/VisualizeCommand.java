package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class VisualizeCommand implements CommandExecutor {

    private final WsClient ws;

    public VisualizeCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /visualize <natural language query>");
            return true;
        }
        String query = String.join(" ", args).trim();
        ws.sendVisualize(query, sender);
        return true;
    }
}
