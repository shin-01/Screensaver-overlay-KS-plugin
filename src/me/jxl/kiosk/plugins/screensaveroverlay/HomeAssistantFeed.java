// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.screensaveroverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Home Assistant data feed for the screensaver renderer. Entity states arrive through
 * host.read subscriptions, the hourly forecast through an ha subscription and calendar
 * events through periodic calendar/get_events service calls.
 */
final class HomeAssistantFeed {
    private static final String FORECAST_KEY = "hourly_forecast";
    private final PluginHost host;
    private final OverlayData data;
    private final Runnable publishRequest;
    private final List<String> subscriptions = new ArrayList<>();

    HomeAssistantFeed(PluginHost host, OverlayData data, Runnable publishRequest) {
        this.host = host;
        this.data = data;
        this.publishRequest = publishRequest;
    }

    void configure(Map<String, Object> settings) {
        data.applySettings(settings);
        resubscribe();
        if (data.hourlyForecastEnabled()) subscribeForecast();
        else host.haUnsubscribe(FORECAST_KEY);
        refreshCalendars();
    }

    private synchronized void resubscribe() {
        for (String entity : new ArrayList<>(subscriptions)) {
            host.unsubscribe("ha.entity." + entity);
        }
        subscriptions.clear();
        for (String entity : data.watchedEntities()) {
            subscriptions.add(entity);
            host.subscribe("ha.entity." + entity);
        }
        if (data.weatherEnabled() || !data.statusEntities().isEmpty()
                || !data.valueEntities().isEmpty()) {
            subscriptions.add("sun.sun");
            host.subscribe("ha.entity.sun.sun");
        }
    }

    private void subscribeForecast() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "weather/subscribe_forecast");
        message.put("forecast_type", "hourly");
        message.put("entity_id", data.weatherEntity());
        host.haSubscribe(FORECAST_KEY, message, (ok, result, error) -> {
            if (!ok) {
                host.status("Screensaver Overlay forecast unavailable: " + error, false);
                data.applyForecastJson("[]");
            }
        });
    }

    /** Fetch upcoming calendar events for the next 7 days through a service call. */
    void refreshCalendars() {
        List<String> calendars = data.calendars();
        if (calendars.isEmpty()) {
            data.applyCalendarJson("[]");
            return;
        }
        Map<String, Object> serviceData = new LinkedHashMap<>();
        List<Map<String, Object>> targets = new ArrayList<>();
        for (String calendar : calendars) {
            targets.add(Collections.singletonMap("entity_id", (Object) calendar));
        }
        serviceData.put("target", targets);
        long start = System.currentTimeMillis();
        long end = start + 7L * 24 * 60 * 60 * 1000;
        serviceData.put("start_date_time", iso(start));
        serviceData.put("end_date_time", iso(end));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "call_service");
        message.put("domain", "calendar");
        message.put("service", "get_events");
        message.put("service_data", serviceData);
        message.put("return_response", true);
        host.haCall(message, (ok, result, error) -> {
            if (!ok) {
                host.status("Screensaver Overlay calendars unavailable: " + error, false);
                return;
            }
            data.applyCalendarJson(CalendarEvents.toJson(result, data.infoItemsLimit()));
            publishRequest.run();
        });
    }

    void onEvent(String event, Map<String, Object> payload) {
        if (payload == null) return;
        if (event.startsWith("ks.ha.subscription.")) {
            handleSubscription(event.substring("ks.ha.subscription.".length()), payload);
            return;
        }
        if (event.startsWith("ks.ha.entity.")) {
            String entity = event.substring("ks.ha.entity.".length());
            String status = String.valueOf(payload.get("status"));
            data.applyEntityState(entity, status, payload.get("state"), payload.get("attributes"));
            publishRequest.run();
        }
    }

    private void handleSubscription(String key, Map<String, Object> payload) {
        if (!FORECAST_KEY.equals(key)) return;
        String type = String.valueOf(payload.get("type"));
        if ("event".equals(type)) {
            Map<?, ?> event = payload.get("event") instanceof Map ? (Map<?, ?>) payload.get("event") : Collections.emptyMap();
            Object forecast = event.get("forecast");
            data.applyForecastJson(Forecast.toJson(forecast));
            publishRequest.run();
        } else if ("disconnected".equals(type) || "ended".equals(type) || "too_large".equals(type)) {
            data.applyForecastJson("[]");
        }
    }

    void stop() {
        for (String entity : subscriptions) {
            try {
                host.unsubscribe("ha.entity." + entity);
            } catch (RuntimeException ignored) {
                // KS has already revoked the subscriptions when stopping the session.
            }
        }
        subscriptions.clear();
    }

    private static String iso(long millis) {
        return java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()));
    }
}
