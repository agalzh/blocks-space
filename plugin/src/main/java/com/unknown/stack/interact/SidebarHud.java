package com.unknown.stack.interact;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SidebarHud {

    private static final int MAX_LINES = 8;
    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Stack Unknown";

    private final Map<UUID, PlayerHud> huds = new HashMap<>();

    public void update(Player p, List<String> lines) {
        PlayerHud h = huds.computeIfAbsent(p.getUniqueId(), id -> new PlayerHud(p));
        h.show(lines);
    }

    public void hide(Player p) {
        PlayerHud h = huds.get(p.getUniqueId());
        if (h != null) h.hide();
    }

    public void removePlayer(UUID id) {
        PlayerHud h = huds.remove(id);
        if (h != null) h.cleanup();
    }

    public void shutdown() {
        for (PlayerHud h : huds.values()) h.cleanup();
        huds.clear();
    }

    private static final class PlayerHud {
        private final Player owner;
        private final Scoreboard board;
        private final Objective obj;
        private final Team[] teams = new Team[MAX_LINES];
        private final String[] entries = new String[MAX_LINES];
        private boolean shown = false;

        PlayerHud(Player owner) {
            this.owner = owner;
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            this.board = mgr.getNewScoreboard();
            this.obj = board.registerNewObjective("hud", "dummy", TITLE);
            this.obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            char[] codes = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
            for (int i = 0; i < MAX_LINES; i++) {
                entries[i] = ChatColor.COLOR_CHAR + "" + codes[i] + ChatColor.COLOR_CHAR + "r";
                Team t = board.registerNewTeam("t" + i);
                t.addEntry(entries[i]);
                teams[i] = t;
                Score s = obj.getScore(entries[i]);
                s.setScore(MAX_LINES - i);
                try {
                    s.numberFormat(NumberFormat.blank());
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
                    // older API; side numbers stay visible
                }
            }
        }

        void show(List<String> lines) {
            for (int i = 0; i < MAX_LINES; i++) {
                String text = i < lines.size() ? truncate(lines.get(i)) : "";
                if (!text.equals(teams[i].getPrefix())) {
                    teams[i].setPrefix(text);
                }
            }
            if (!shown) {
                owner.setScoreboard(board);
                shown = true;
            }
        }

        void hide() {
            if (shown) {
                owner.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                shown = false;
            }
        }

        void cleanup() {
            hide();
            for (Team t : teams) {
                if (t == null) continue;
                try { t.unregister(); } catch (RuntimeException ignored) {}
            }
            try { obj.unregister(); } catch (RuntimeException ignored) {}
        }

        private static String truncate(String s) {
            return s.length() <= 40 ? s : s.substring(0, 40);
        }
    }
}
