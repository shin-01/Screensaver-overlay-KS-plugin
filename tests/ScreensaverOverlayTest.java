// SPDX-License-Identifier: Apache-2.0
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;
import me.jxl.kiosk.plugins.screensaveroverlay.ScreensaverOverlayPlugin;

public final class ScreensaverOverlayTest {
    static final class Host implements PluginHost {
        Map<String, Object> screensaver;
        int publications;
        final List<String> subscriptions = new ArrayList<>();
        final List<String> unsubscribed = new ArrayList<>();
        final Map<String, Map<String, Object>> haSubscriptions = new LinkedHashMap<>();
        final List<Map<String, Object>> haCalls = new ArrayList<>();
        String status;
        boolean statusError;
        public void publishScreensaverAsset(String key, String title, String entry, Map<String, Object> data) {
            assert "screensaver_overlay".equals(key);
            assert "Screensaver Overlay".equals(title);
            assert "overlay/index.html".equals(entry);
            assert data.keySet().stream().allMatch(k -> k.matches("[a-zA-Z][a-zA-Z0-9_]{0,39}"));
            assert data.values().stream().allMatch(v -> v == null || v instanceof String
                    || v instanceof Boolean || (v instanceof Number && Double.isFinite(((Number) v).doubleValue())));
            screensaver = new HashMap<>(data);
            publications++;
        }
        public void subscribe(String event) { subscriptions.add(event); }
        public void unsubscribe(String event) { unsubscribed.add(event); }
        public void haSubscribe(String key, Map<String, Object> message, CommandCallback callback) {
            haSubscriptions.put(key, message);
            callback.onResult(true, Collections.emptyMap(), null);
        }
        public void haUnsubscribe(String key) { haSubscriptions.remove(key); }
        public void haCall(Map<String, Object> message, CommandCallback callback) {
            haCalls.add(new HashMap<>(message));
            callback.onResult(true, sampleCalendarResponse(), null);
        }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) { status = message; statusError = error; }

        static Map<String, Object> sampleCalendarResponse() {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("summary", "Dinner");
            event.put("start", Collections.singletonMap("dateTime", "2026-01-01T19:00:00Z"));
            event.put("end", Collections.singletonMap("dateTime", "2026-01-01T21:00:00Z"));
            Map<String, Object> calendar = new LinkedHashMap<>();
            calendar.put("events", Collections.singletonList(event));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("calendar.home", calendar);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response", response);
            return result;
        }
    }

    static Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("weatherEntity", "weather.home");
        settings.put("weatherDescriptionEntity", "sensor.weather_text");
        settings.put("externalTemperatureEntity", "");
        settings.put("internalTemperatureEntity", "sensor.inside");
        settings.put("rainSensorEntity", "binary_sensor.rain");
        settings.put("calendarEntities", Arrays.asList(
                Collections.singletonMap("entity", (Object) "calendar.home")));
        settings.put("valueEntities", Arrays.asList(
                Collections.singletonMap("entity", (Object) "sensor.humidity")));
        settings.put("statusIconEntities", Arrays.asList(
                Collections.singletonMap("entity", (Object) "light.kitchen")));
        settings.put("showWeatherIcon", true);
        settings.put("showHourlyForecast", true);
        settings.put("showTemperatures", true);
        settings.put("showInfo", true);
        settings.put("showClock", true);
        settings.put("showStatusIcons", true);
        settings.put("withAtAGlance", false);
        settings.put("withCornerWidgets", false);
        settings.put("weatherIconSize", 27.0);
        settings.put("hourlyForecastBackgroundOpacity", 70.0);
        settings.put("infoTextSize", 2.0);
        settings.put("infoItemsLimit", 5.0);
        settings.put("backgroundMode", "Default images carousel");
        settings.put("backgroundCarouselInterval", 60.0);
        return settings;
    }

    static void entityEvent(ScreensaverOverlayPlugin plugin, String entity, String state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "available");
        payload.put("state", state);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("temperature", 21.5);
        attributes.put("temperature_unit", "\u00b0C");
        attributes.put("friendly_name", "Test");
        attributes.put("unit_of_measurement", "%");
        payload.put("attributes", attributes);
        plugin.onEvent("ks.ha.entity." + entity, payload);
    }

    public static void main(String[] arguments) {
        Host host = new Host();
        ScreensaverOverlayPlugin plugin = new ScreensaverOverlayPlugin();
        plugin.start(host, settings());
        assert host.publications == 1 : "expected one initial publication";
        assert host.subscriptions.contains("ha.entity.weather.home");
        assert host.subscriptions.contains("ha.entity.sensor.inside");
        assert host.subscriptions.contains("ha.entity.binary_sensor.rain");
        assert host.subscriptions.contains("ha.entity.sensor.humidity");
        assert host.subscriptions.contains("ha.entity.light.kitchen");
        assert host.haSubscriptions.containsKey("hourly_forecast");
        assert host.haCalls.stream().anyMatch(message ->
                "calendar".equals(message.get("domain")) && "get_events".equals(message.get("service")));

        entityEvent(plugin, "weather.home", "partlycloudy");
        entityEvent(plugin, "sensor.weather_text", "Light rain");
        entityEvent(plugin, "sensor.inside", "22.3");
        entityEvent(plugin, "binary_sensor.rain", "raining");
        entityEvent(plugin, "sensor.humidity", "58");
        entityEvent(plugin, "light.kitchen", "on");
        entityEvent(plugin, "sun.sun", "above_horizon");
        plugin.onEvent("ks.ha.subscription.hourly_forecast", forecastEvent());

        // The plugin rate limits publications, so wait for the deferred one.
        await(host, screensaver -> "partlycloudy".equals(screensaver.get("weatherCondition")));
        assert "Light rain".equals(host.screensaver.get("weatherDescription"));
        assert "22.3".equals(host.screensaver.get("internalTemperature"));
        assert "raining".equals(host.screensaver.get("rainState"));
        assert String.valueOf(host.screensaver.get("hourlyForecast")).contains("\"temperature\"");
        assert String.valueOf(host.screensaver.get("calendarEvents")).contains("Dinner");
        assert String.valueOf(host.screensaver.get("valueEntities")).contains("Test");
        assert String.valueOf(host.screensaver.get("statusIcons")).contains("\"icon\"");
        assert Boolean.FALSE.equals(host.screensaver.get("withAtAGlance"));
        assert Boolean.FALSE.equals(host.screensaver.get("withCornerWidgets"));

        Map<String, Object> glance = settings();
        glance.put("withAtAGlance", true);
        glance.put("withCornerWidgets", true);
        int beforeGlance = host.publications;
        plugin.configure(glance);
        await(host, screensaver -> Boolean.TRUE.equals(screensaver.get("withAtAGlance"))
                && Boolean.TRUE.equals(screensaver.get("withCornerWidgets")));
        assert host.publications > beforeGlance : "glance layout change must republish";

        Map<String, Object> published = new HashMap<>(host.screensaver);
        Map<String, Object> next = settings();
        next.put("backgroundMode", "Black");
        plugin.configure(next);
        await(host, screensaver -> "black".equals(screensaver.get("backgroundMode")));
        assert !published.equals(host.screensaver) : "changed settings must republish";

        Map<String, Object> same = settings();
        same.put("backgroundMode", "Black");
        int before = host.publications;
        plugin.configure(same);
        assert host.publications == before : "unchanged data must not republish";

        plugin.stop();
        System.out.println("ScreensaverOverlayTest passed");
    }

    interface Condition { boolean satisfied(Map<String, Object> screensaver); }

    static void await(Host host, Condition condition) {
        long deadline = System.currentTimeMillis() + 15000;
        while (!condition.satisfied(host.screensaver)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for a publication: " + host.screensaver);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for a publication");
            }
        }
    }

    static Map<String, Object> forecastEvent() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("condition", "cloudy");
        entry.put("datetime", "2026-01-01T12:00:00Z");
        entry.put("temperature", 14.5);
        entry.put("precipitation", 0.4);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("forecast", Collections.singletonList(entry));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "event");
        payload.put("event", event);
        return payload;
    }
}
