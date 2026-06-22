package com.unknown.stack.commands;

import com.unknown.stack.net.WsClient;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class UploadCommand implements CommandExecutor {

    private final WsClient ws;

    public UploadCommand(WsClient ws) {
        this.ws = ws;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /upload <absolute-path> [csv|json]");
            return true;
        }

        String fmt = "auto";
        int pathEnd = args.length;
        String last = args[args.length - 1].toLowerCase();
        if (args.length >= 2 && (last.equals("csv") || last.equals("json"))) {
            fmt = last;
            pathEnd = args.length - 1;
        }
        String path = String.join(" ", Arrays.copyOfRange(args, 0, pathEnd));

        ws.sendUpload(path, fmt, sender);
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.2F);
        }
        return true;
    }
}
