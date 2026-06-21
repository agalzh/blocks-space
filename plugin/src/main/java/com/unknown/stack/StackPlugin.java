package com.unknown.stack;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class StackPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("StackUnknown plugin enabled (phase 0).");
    }

    @Override
    public void onDisable() {
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
