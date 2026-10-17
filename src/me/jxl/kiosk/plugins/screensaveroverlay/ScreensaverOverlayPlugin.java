// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.screensaveroverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A Kiosk Satellite screensaver plugin styled after the Screensaver Overlay Home Assistant
 * integration. The renderer is packaged HTML that receives a flat scalar data map; this class
 * aggregates Home Assistant data in Java and republishes rendering options when they change.
 */
public final class ScreensaverOverlayPlugin implements KioskPlugin {
    static final String RENDERER_KEY = "screensaver_overlay";
    static final String RENDERER_TITLE = "Screensaver Overlay";
    static final String RENDERER_ENTRY = "overlay/index.html";
    static final long MIN_REPUBLISH_MS = 2500;

    private final OverlayData data = new OverlayData();
    private PluginHost host;
    private HomeAssistantFeed feed;
    private ScheduledExecutorService worker;
    private Map<String, Object> published;
    private long lastPublication;
    private boolean pending;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        feed = new HomeAssistantFeed(host, data, this::requestPublication);
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "screensaver-overlay-plugin");
            thread.setDaemon(true);
            return thread;
        });
        configure(settings);
        worker.scheduleWithFixedDelay(() -> runSafely(() -> {
            feed.refreshCalendars();
            publish();
        }), 300, 300, TimeUnit.SECONDS);
        host.log("Screensaver Overlay started");
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        data.applySettings(settings);
        if (host != null) {
            if (feed != null) feed.configure(settings);
            publish();
        }
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        throw new IllegalArgumentException("Unknown command: " + command);
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if (event.startsWith("ks.ha.") || event.startsWith("ks.ha.subscription.")) {
            if (feed != null) feed.onEvent(event, payload);
        }
    }

    @Override
    public synchronized void stop() {
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        if (feed != null) {
            feed.stop();
            feed = null;
        }
        host = null;
        published = null;
        data.clear();
    }

    /** Called from any thread when the aggregated data or settings changed. */
    void requestPublication() {
        runSafely(this::publish);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            PluginHost current = host;
            if (current != null) current.status("Screensaver Overlay: " + error.getMessage(), true);
        }
    }

    /** Publish the renderer with the current data map, rate limited and deduplicated. */
    synchronized void publish() {
        PluginHost current = host;
        if (current == null) return;
        long now = System.currentTimeMillis();
        Map<String, Object> next = data.renderingData();
        if (next.equals(published)) return;
        long wait = lastPublication + MIN_REPUBLISH_MS - now;
        if (wait > 0) {
            if (pending) return;
            pending = true;
            ScheduledExecutorService executor = worker;
            if (executor == null) return;
            executor.schedule(() -> {
                pending = false;
                runSafely(this::publish);
            }, wait, TimeUnit.MILLISECONDS);
            return;
        }
        published = next;
        lastPublication = now;
        current.publishScreensaverAsset(RENDERER_KEY, RENDERER_TITLE, RENDERER_ENTRY, next);
    }

    static List<String> entityRows(Object setting) {
        List<String> entities = new ArrayList<>();
        if (setting instanceof List) {
            for (Object row : (List<?>) setting) {
                if (row instanceof Map) {
                    Object entity = ((Map<?, ?>) row).get("entity");
                    if (entity instanceof String && !((String) entity).isEmpty()) {
                        entities.add((String) entity);
                    }
                } else if (row instanceof String && !((String) row).isEmpty()) {
                    entities.add((String) row);
                }
            }
        }
        return entities;
    }
}
