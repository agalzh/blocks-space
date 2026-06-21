package com.unknown.stack.net;

import com.unknown.stack.render.SceneRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class WsClient extends WebSocketClient {

    private static final int BACKOFF_START_SEC = 2;
    private static final int BACKOFF_MAX_SEC = 30;

    private final JavaPlugin plugin;
    private final SceneRenderer renderer;
    private final URI uri;

    private int backoffSec = BACKOFF_START_SEC;
    private volatile boolean shuttingDown = false;

    private final Map<String, CommandSender> pendingByPath = new ConcurrentHashMap<>();

    public WsClient(JavaPlugin plugin, URI uri, SceneRenderer renderer) {
        super(uri);
        this.plugin = plugin;
        this.uri = uri;
        this.renderer = renderer;
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
            case "pong" -> plugin.getLogger().fine("WS pong");
            case "error" -> handleError(msg.optString("message", "(unspecified)"));
            default -> plugin.getLogger().warning("WS unexpected type: " + type);
        }
    }

    private void handleScene(JSONObject scene) {
        // World mutation must run on the main thread.
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
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "ws render failed", e);
                broadcastFeedback("§cupload render failed: " + e.getMessage());
            }
        });
    }

    private void handleError(String detail) {
        plugin.getLogger().warning("WS server error: " + detail);
        broadcastFeedback("§cupload error: " + detail);
    }

    private void broadcastFeedback(String text) {
        // Deliver to whoever asked. If we lost the mapping (multi-pending), broadcast.
        if (pendingByPath.isEmpty()) {
            Bukkit.broadcastMessage(text);
            return;
        }
        for (CommandSender s : pendingByPath.values()) {
            s.sendMessage(text);
        }
        pendingByPath.clear();
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
