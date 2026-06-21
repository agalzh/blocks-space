package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class OverviewCommand implements CommandExecutor {

    private final WsClient ws;

    public OverviewCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ws.sendOverview(sender);
        return true;
    }
}
