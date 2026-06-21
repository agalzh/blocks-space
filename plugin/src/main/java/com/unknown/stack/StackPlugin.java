package com.unknown.stack;

import com.unknown.stack.commands.LoadMockCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class StackPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("StackUnknown plugin enabled (phase 1).");
        PluginCommand loadmock = getCommand("loadmock");
        if (loadmock != null) {
            loadmock.setExecutor(new LoadMockCommand(this));
        } else {
            getLogger().warning("/loadmock command missing from plugin.yml; skipping registration.");
        }
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
