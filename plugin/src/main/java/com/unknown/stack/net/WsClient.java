package com.unknown.stack.net;

import com.unknown.stack.interact.ActionExecutor;
import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class WsClient extends WebSocketClient {

    private static final int BACKOFF_START_SEC = 2;
    private static final int BACKOFF_MAX_SEC = 30;
    private static final int CHAT_CHUNK_CHARS = 256;

    private final JavaPlugin plugin;
    private final SceneRenderer renderer;
    private final ActionExecutor actionExecutor;
    private final URI uri;

    private int backoffSec = BACKOFF_START_SEC;
    private volatile boolean shuttingDown = false;

    private final Map<String, CommandSender> pendingByPath = new ConcurrentHashMap<>();
    private volatile CommandSender pendingOverview;
    private volatile CommandSender pendingVisualize;
    private volatile CommandSender pendingQuery;
    private volatile CommandSender pendingGeminiInfo;

    public WsClient(JavaPlugin plugin, URI uri, SceneRenderer renderer, ActionExecutor actionExecutor) {
        super(uri);
        this.plugin = plugin;
        this.uri = uri;
        this.renderer = renderer;
        this.actionExecutor = actionExecutor;
        setConnectionLostTimeout(60);
    }

    public void shutdown() {
        shuttingDown = true;
        close();
    }

    public void sendUpload(String path, String fmt, CommandSender feedback) {
        if (!isOpen()) {
            feedback.sendMessage("§cWS not connected — start the engine: python -m engine.ws_server");
            return;
        }
        pendingByPath.put(path, feedback);
        JSONObject req = new JSONObject()
                .put("cmd", "upload")
                .put("path", path)
                .put("fmt", fmt)
                .put("req_id", UUID.randomUUID().toString());
        send(req.toString());
        feedback.sendMessage("§7uploading " + path + " (fmt=" + fmt + ")...");
    }

    public void sendOverview(CommandSender feedback) {
        if (!isOpen()) {
            feedback.sendMessage("§cWS not connected — start the engine: python -m engine.ws_server");
            return;
        }
        pendingOverview = feedback;
        send(new JSONObject().put("cmd", "overview").toString());
        feedback.sendMessage("§7asking Gemini for an overview...");
    }

    public void sendVisualize(String query, CommandSender feedback) {
        if (!isOpen()) {
            feedback.sendMessage("§cWS not connected — start the engine: python -m engine.ws_server");
            return;
        }
        pendingVisualize = feedback;
        send(new JSONObject()
                .put("cmd", "visualize")
                .put("query", query)
                .toString());
        feedback.sendMessage("§7asking Gemini: " + query);
    }

    public void sendGeminiInfo(CommandSender feedback) {
        if (!isOpen()) {
            feedback.sendMessage("§cWS not connected — start the engine: python -m engine.ws_server");
            return;
        }
        pendingGeminiInfo = feedback;
        send(new JSONObject().put("cmd", "gemini_info").toString());
    }

    public void sendQuery(String question, CommandSender feedback) {
        if (!isOpen()) {
            feedback.sendMessage("§cWS not connected — start the engine: python -m engine.ws_server");
            return;
        }
        pendingQuery = feedback;
        send(new JSONObject()
                .put("cmd", "query")
                .put("query", question)
                .toString());
        feedback.sendMessage("§7asking Gemini: §f" + question);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        plugin.getLogger().info("WS connected: " + uri);
        backoffSec = BACKOFF_START_SEC;
    }

    @Override
    public void onMessage(String message) {
        JSONObject msg;
        try {
            msg = new JSONObject(message);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("WS bad JSON from server: " + e.getMessage());
            return;
        }
        String type = msg.optString("type");
        switch (type) {
            case "scene" -> handleScene(msg.getJSONObject("scene"));
            case "overview" -> handleOverview(msg.optString("text", ""));
            case "actions" -> handleActions(msg);
            case "query_answer" -> handleQueryAnswer(msg);
            case "gemini_info" -> handleGeminiInfo(msg.optJSONObject("info"));
            case "pong" -> plugin.getLogger().fine("WS pong");
            case "error" -> handleError(msg.optString("message", "(unspecified)"));
            default -> plugin.getLogger().warning("WS unexpected type: " + type);
        }
    }

    private void handleScene(JSONObject scene) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                SceneRenderer.Result r = renderer.render(scene, SceneRenderer.defaultWorld());
                String dsName = r.datasetName;
                String text = String.format(
                        "§aupload: dataset=%s, placed %d point blocks + %d cluster means",
                        dsName, r.pointsPlaced, r.means);
                plugin.getLogger().info(String.format(
                        "ws scene OK name=%s points=%d means=%d skipped=%d",
                        dsName, r.pointsPlaced, r.means, r.skipped));
                broadcastFeedback(text);
                playSceneReadySound();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "ws render failed", e);
                broadcastFeedback("§cupload render failed: " + e.getMessage());
            }
        });
    }

    private void playSceneReadySound() {
        World world = SceneRenderer.defaultWorld();
        if (world == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.95F, 1.0F);
        }
    }

    private void playOverviewReadySound() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        }
    }

    private void playVisualizeReadySound() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
        }
    }

    private void handleOverview(String text) {
        CommandSender target = pendingOverview;
        pendingOverview = null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage("§6§l[Overview]");
            for (String chunk : chunk(text, CHAT_CHUNK_CHARS)) {
                Bukkit.broadcastMessage("§f" + chunk);
            }
            if (target != null) target.sendMessage("§7(overview broadcast)");
            playOverviewReadySound();
        });
    }

    private void handleActions(JSONObject msg) {
        CommandSender target = pendingVisualize;
        pendingVisualize = null;
        String query = msg.optString("query", "");
        String source = msg.optString("source", "?");
        JSONArray actions = msg.optJSONArray("actions");
        Bukkit.getScheduler().runTask(plugin, () -> {
            CommandSender feedback = target != null ? target : Bukkit.getConsoleSender();
            plugin.getLogger().info("actions source=" + source + " query=" + query
                    + " count=" + (actions == null ? 0 : actions.length()));
            Bukkit.broadcastMessage("§6§l[Visualize] §7" + query
                    + " §8(source=" + source + ")");
            if (actions == null || actions.length() == 0) {
                Bukkit.broadcastMessage("§eno valid actions returned");
                return;
            }
            actionExecutor.apply(actions, query, feedback);
            playVisualizeReadySound();
        });
    }

    private void handleQueryAnswer(JSONObject msg) {
        CommandSender target = pendingQuery;
        pendingQuery = null;
        String question = msg.optString("query", "");
        String text = sanitizeForChat(msg.optString("text", ""));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage("§6§l[Query] §r§f" + truncate(question, 80));
            if (text.isEmpty()) {
                Bukkit.broadcastMessage("§e(no answer)");
            } else {
                for (String chunk : chunk(text, CHAT_CHUNK_CHARS)) {
                    Bukkit.broadcastMessage("§f" + chunk);
                }
            }
            if (target != null) target.sendMessage("§7(query answer broadcast)");
            playOverviewReadySound();
        });
    }

    private static String sanitizeForChat(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (String ln : s.split("\\n")) {
            String t = ln.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("/") || t.startsWith("!") || t.startsWith("@")) continue;
            if (out.length() > 0) out.append(' ');
            out.append(t);
        }
        return out.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void handleGeminiInfo(JSONObject info) {
        CommandSender target = pendingGeminiInfo;
        pendingGeminiInfo = null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            CommandSender s = target != null ? target : Bukkit.getConsoleSender();
            if (info == null) {
                s.sendMessage("§c/gemini: empty info from engine");
                return;
            }
            String mode = info.optString("mode", "?");
            String active = info.optString("active_model", "(none)");
            boolean key = info.optBoolean("key_present", false);
            String lastErr = info.optString("last_error", "");
            JSONArray chain = info.optJSONArray("chain");
            JSONArray avail = info.optJSONArray("available");
            JSONArray unav = info.optJSONArray("unavailable");

            s.sendMessage("§8§m                                                  ");
            s.sendMessage("§b§lGemini status");
            s.sendMessage("§7Mode         §8» " + (mode.equals("gemini") ? "§agemini" : "§efallback heuristics"));
            s.sendMessage("§7Active model §8» §f" + (active.isEmpty() ? "§c(none)" : active));
            s.sendMessage("§7API key      §8» " + (key ? "§apresent" : "§cmissing"));
            s.sendMessage("§7Fallback chain §8» §f" + joinArr(chain));
            s.sendMessage("§7Available    §8» §a" + joinArr(avail));
            if (unav != null && unav.length() > 0) {
                s.sendMessage("§7Unavailable  §8» §c" + joinArr(unav));
            }
            if (!lastErr.isEmpty() && !"null".equals(lastErr)) {
                s.sendMessage("§7Last error   §8» §c" + truncate(lastErr, 120));
            }
            s.sendMessage("§8§m                                                  ");
        });
    }

    private static String joinArr(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) sb.append("§7, §f");
            sb.append(arr.optString(i, "?"));
        }
        return sb.toString();
    }

    private void handleError(String detail) {
        plugin.getLogger().warning("WS server error: " + detail);
        CommandSender o = pendingOverview;
        CommandSender v = pendingVisualize;
        CommandSender q = pendingQuery;
        CommandSender g = pendingGeminiInfo;
        pendingOverview = null;
        pendingVisualize = null;
        pendingQuery = null;
        pendingGeminiInfo = null;
        if (o != null) o.sendMessage("§coverview error: " + detail);
        if (v != null) v.sendMessage("§cvisualize error: " + detail);
        if (q != null) q.sendMessage("§cquery error: " + detail);
        if (g != null) g.sendMessage("§c/gemini error: " + detail);
        if (o == null && v == null && q == null && g == null) broadcastFeedback("§cupload error: " + detail);
    }

    private void broadcastFeedback(String text) {
        if (pendingByPath.isEmpty()) {
            Bukkit.broadcastMessage(text);
            return;
        }
        for (CommandSender s : pendingByPath.values()) {
            s.sendMessage(text);
        }
        pendingByPath.clear();
    }

    private static java.util.List<String> chunk(String s, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String para : s.split("\\n\\s*\\n")) {
            String p = para.trim();
            while (p.length() > max) {
                int cut = p.lastIndexOf(' ', max);
                if (cut < max / 2) cut = max;
                out.add(p.substring(0, cut));
                p = p.substring(cut).trim();
            }
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        plugin.getLogger().info(String.format(
                "WS closed (code=%d remote=%s reason=%s)", code, remote, reason));
        if (shuttingDown) return;
        int delaySec = backoffSec;
        backoffSec = Math.min(backoffSec * 2, BACKOFF_MAX_SEC);
        plugin.getLogger().info("WS reconnect in " + delaySec + "s");
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::tryReconnect, delaySec * 20L);
    }

    @Override
    public void onError(Exception ex) {
        plugin.getLogger().warning("WS error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private void tryReconnect() {
        if (shuttingDown) return;
        try {
            reconnectBlocking();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
