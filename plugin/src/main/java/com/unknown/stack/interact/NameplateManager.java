package com.unknown.stack.interact;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NameplateManager {

    private final JavaPlugin plugin;
    private final Map<UUID, TextDisplay> plates = new HashMap<>();
    private final Map<UUID, Boolean> shownTo = new HashMap<>();

    public NameplateManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player viewer, Location at, String legacyText) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(legacyText);
        TextDisplay td = plates.get(viewer.getUniqueId());
        if (td == null || td.isDead()) {
            td = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.text(component);
                d.setBillboard(Display.Billboard.CENTER);
                d.setVisibleByDefault(false);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(2.0F);
            });
            plates.put(viewer.getUniqueId(), td);
        } else {
            td.text(component);
            td.teleport(at);
        }
        if (!Boolean.TRUE.equals(shownTo.get(viewer.getUniqueId()))) {
            viewer.showEntity(plugin, td);
            shownTo.put(viewer.getUniqueId(), true);
        }
    }

    public void hide(Player viewer) {
        TextDisplay td = plates.get(viewer.getUniqueId());
        if (td == null) return;
        if (Boolean.TRUE.equals(shownTo.get(viewer.getUniqueId()))) {
            viewer.hideEntity(plugin, td);
            shownTo.put(viewer.getUniqueId(), false);
        }
    }

    public void removePlayer(UUID id) {
        TextDisplay td = plates.remove(id);
        shownTo.remove(id);
        if (td != null && !td.isDead()) td.remove();
    }

    public void shutdown() {
        for (TextDisplay td : plates.values()) {
            if (td != null && !td.isDead()) td.remove();
        }
        plates.clear();
        shownTo.clear();
    }
}
