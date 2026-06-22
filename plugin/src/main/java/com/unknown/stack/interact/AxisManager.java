package com.unknown.stack.interact;

import com.unknown.stack.render.SceneRegistry;

public class AxisManager {

    private final SceneRegistry registry;
    private volatile boolean visible = false;

    public AxisManager(SceneRegistry registry) {
        this.registry = registry;
    }

    public boolean isVisible() { return visible; }

    public boolean show() {
        SceneRegistry.Snapshot snap = registry.get();
        if (snap == null || snap.globalCentroid == null) return false;
        visible = true;
        return true;
    }

    public void hide() { visible = false; }

    public void clearTracking() { visible = false; }
}
