// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.screensaveroverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated configuration and Home Assistant state for the renderer, kept as a flat scalar
 * map because the screensaver data map only accepts scalar JSON values.
 */
final class OverlayData {
    // Settings
    private String weatherEntity = "";
    private String weatherDescriptionEntity = "";
    private String externalTemperatureEntity = "";
    private String internalTemperatureEntity = "";
    private String rainSensorEntity = "";
    private List<String> calendars = new ArrayList<>();
    private List<String> valueEntities = new ArrayList<>();
    private List<String> statusEntities = new ArrayList<>();
    private List<String> statusIcons = new ArrayList<>();
    private boolean showWeatherIcon = true;
    private boolean showHourlyForecast = true;
    private boolean showTemperatures = true;
    private boolean showInfo = true;
    private boolean showClock = true;
    private boolean showStatusIcons = true;
    private double weatherIconSize = 27;
    private double weatherDescriptionTextSize = 1.8;
    private double hourlyForecastBackgroundOpacity = 70;
    private double infoTextSize = 2.0;
    private int infoItemsLimit = 5;
    private String backgroundMode = "black";
    private double backgroundCarouselInterval = 60;

    // Live state
    private String weatherCondition = "";
    private String weatherTemperatureUnit = "";
    private String weatherTemperature = "";
    private String weatherDescription = "";
    private String externalTemperature = "";
    private String internalTemperature = "";
    private String rainState = "";
    private String sunState = "";
    private final Map<String, String> valueStates = new LinkedHashMap<>();
    private final Map<String, String> valueNames = new LinkedHashMap<>();
    private final Map<String, String> valueUnits = new LinkedHashMap<>();
    private final Map<String, String> statusStates = new LinkedHashMap<>();
    private String calendarJson = "[]";
    private String forecastJson = "[]";

    void applySettings(Map<String, Object> settings) {
        weatherEntity = text(settings.get("weatherEntity"));
        weatherDescriptionEntity = text(settings.get("weatherDescriptionEntity"));
        externalTemperatureEntity = text(settings.get("externalTemperatureEntity"));
        internalTemperatureEntity = text(settings.get("internalTemperatureEntity"));
        rainSensorEntity = text(settings.get("rainSensorEntity"));
        calendars = ScreensaverOverlayPlugin.entityRows(settings.get("calendarEntities"));
        valueEntities = ScreensaverOverlayPlugin.entityRows(settings.get("valueEntities"));
        List<String> statusRows = ScreensaverOverlayPlugin.entityRows(settings.get("statusIconEntities"));
        statusEntities = statusRows;
        statusIcons = iconRows(settings.get("statusIconEntities"));
        showWeatherIcon = Boolean.TRUE.equals(settings.get("showWeatherIcon"));
        showHourlyForecast = Boolean.TRUE.equals(settings.get("showHourlyForecast"));
        showTemperatures = Boolean.TRUE.equals(settings.get("showTemperatures"));
        showInfo = Boolean.TRUE.equals(settings.get("showInfo"));
        showClock = Boolean.TRUE.equals(settings.get("showClock"));
        showStatusIcons = Boolean.TRUE.equals(settings.get("showStatusIcons"));
        weatherIconSize = number(settings.get("weatherIconSize"), 27);
        hourlyForecastBackgroundOpacity = number(settings.get("hourlyForecastBackgroundOpacity"), 70);
        infoTextSize = number(settings.get("infoTextSize"), 2.0);
        infoItemsLimit = (int) number(settings.get("infoItemsLimit"), 5);
        String mode = text(settings.get("backgroundMode"));
        backgroundMode = mode.isEmpty() ? "black" : mode.toLowerCase().replace(' ', '_');
        backgroundCarouselInterval = number(settings.get("backgroundCarouselInterval"), 60);
    }

    boolean weatherEnabled() { return !weatherEntity.isEmpty(); }
    boolean hourlyForecastEnabled() { return showHourlyForecast && !weatherEntity.isEmpty(); }
    String weatherEntity() { return weatherEntity; }
    String weatherDescriptionEntity() { return weatherDescriptionEntity; }
    String externalTemperatureEntity() { return externalTemperatureEntity; }
    String internalTemperatureEntity() { return internalTemperatureEntity; }
    String rainSensorEntity() { return rainSensorEntity; }
    List<String> calendars() { return calendars; }
    List<String> valueEntities() { return valueEntities; }
    List<String> statusEntities() { return statusEntities; }
    int infoItemsLimit() { return infoItemsLimit; }

    /** Entities to watch through host.read subscriptions. */
    List<String> watchedEntities() {
        List<String> entities = new ArrayList<>();
        for (String entity : new String[] {weatherEntity, weatherDescriptionEntity,
                externalTemperatureEntity, internalTemperatureEntity, rainSensorEntity}) {
            if (!entity.isEmpty()) entities.add(entity);
        }
        entities.addAll(valueEntities);
        entities.addAll(statusEntities);
        return entities;
    }

    synchronized void applyEntityState(String entity, String status, Object state, Object attributes) {
        Map<?, ?> attrs = attributes instanceof Map ? (Map<?, ?>) attributes : Collections.emptyMap();
        boolean available = "available".equals(status) && state != null;
        String value = available ? String.valueOf(state) : "";
        if (entity.equals(weatherEntity)) {
            weatherCondition = value;
            Object unit = attrs.get("temperature_unit");
            weatherTemperatureUnit = unit == null ? "" : String.valueOf(unit);
            Object temperature = attrs.get("temperature");
            weatherTemperature = temperature == null ? "" : String.valueOf(temperature);
        } else if (entity.equals(weatherDescriptionEntity)) {
            weatherDescription = available && !value.isEmpty()
                    && !"unknown".equals(value) && !"unavailable".equals(value) ? value : "";
        } else if (entity.equals(externalTemperatureEntity)) {
            externalTemperature = value;
        } else if (entity.equals(internalTemperatureEntity)) {
            internalTemperature = value;
        } else if (entity.equals(rainSensorEntity)) {
            rainState = value;
        } else if (entity.equals("sun.sun")) {
            sunState = value;
        }
        if (valueEntities.contains(entity)) {
            if (available) {
                valueStates.put(entity, value);
                Object name = attrs.get("friendly_name");
                valueNames.put(entity, name == null ? entity : String.valueOf(name));
                Object unit = attrs.get("unit_of_measurement");
                valueUnits.put(entity, unit == null ? "" : String.valueOf(unit));
            } else {
                valueStates.remove(entity);
            }
        }
        if (statusEntities.contains(entity)) {
            if (available) statusStates.put(entity, value);
            else statusStates.remove(entity);
        }
    }

    synchronized void applyForecastJson(String json) { forecastJson = json; }
    synchronized void applyCalendarJson(String json) { calendarJson = json; }

    void clear() {
        weatherCondition = "";
        weatherTemperature = "";
        weatherDescription = "";
        valueStates.clear();
        statusStates.clear();
        calendarJson = "[]";
        forecastJson = "[]";
    }

    /** Build the flat scalar rendering data for the WebView. */
    synchronized Map<String, Object> renderingData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", System.currentTimeMillis());
        data.put("showWeatherIcon", showWeatherIcon);
        data.put("showHourlyForecast", showHourlyForecast);
        data.put("showTemperatures", showTemperatures);
        data.put("showInfo", showInfo);
        data.put("showClock", showClock);
        data.put("showStatusIcons", showStatusIcons);
        data.put("weatherIconSize", weatherIconSize);
        data.put("weatherDescriptionTextSize", weatherDescriptionTextSize);
        data.put("hourlyForecastBackgroundOpacity", hourlyForecastBackgroundOpacity / 100.0);
        data.put("infoTextSize", infoTextSize);
        data.put("infoItemsLimit", (double) infoItemsLimit);
        data.put("backgroundMode", backgroundMode);
        data.put("backgroundCarouselInterval", backgroundCarouselInterval);
        data.put("weatherCondition", weatherCondition);
        data.put("weatherTemperature", weatherTemperature);
        data.put("weatherTemperatureUnit", weatherTemperatureUnit);
        data.put("weatherDescription", weatherDescription);
        data.put("externalTemperature",
                !externalTemperature.isEmpty() ? externalTemperature : weatherTemperature);
        data.put("internalTemperature", internalTemperature);
        data.put("rainState", rainState);
        data.put("sunState", sunState);
        data.put("calendarEvents", calendarJson);
        data.put("hourlyForecast", forecastJson);
        data.put("valueEntities", join(valueEntities, valueStates, valueNames, valueUnits));
        data.put("statusIcons", statusIconsJson());
        return data;
    }

    private String join(List<String> entities, Map<String, String> states,
                        Map<String, String> names, Map<String, String> units) {
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (String entity : entities) {
            String state = states.get(entity);
            if (state == null) continue;
            if (count >= infoItemsLimit) break;
            if (count > 0) json.append(',');
            json.append("{\"name\":").append(quote(names.get(entity)))
                    .append(",\"state\":").append(quote(state))
                    .append(",\"unit\":").append(quote(units.get(entity))).append('}');
            count++;
        }
        return json.append(']').toString();
    }

    private String statusIconsJson() {
        if (!showStatusIcons) return "[]";
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (int i = 0; i < statusEntities.size(); i++) {
            String entity = statusEntities.get(i);
            String state = statusStates.get(entity);
            if (state == null || !isActive(state)) continue;
            String icon = i < statusIcons.size() ? statusIcons.get(i) : "";
            if (count > 0) json.append(',');
            json.append("{\"icon\":").append(quote(icon)).append('}');
            count++;
        }
        return json.append(']').toString();
    }

    static boolean isActive(String state) {
        String value = state.toLowerCase();
        double numeric;
        try {
            numeric = Double.parseDouble(value);
        } catch (NumberFormatException error) {
            numeric = Double.NaN;
        }
        return ACTIVE_STATES.contains(value) || (!Double.isNaN(numeric) && numeric > 0);
    }

    private static final List<String> ACTIVE_STATES = Collections.unmodifiableList(Arrays.asList(
            "on", "open", "opening", "closing", "cleaning", "true", "idle", "home",
            "playing", "paused", "locked", "occupied", "available", "running", "active",
            "connected", "online", "mowing", "starting", "heat", "cool", "dry",
            "heat_cool", "fan_only", "auto", "alarm", "raining"));

    private static List<String> iconRows(Object setting) {
        List<String> icons = new ArrayList<>();
        if (setting instanceof List) {
            for (Object row : (List<?>) setting) {
                if (row instanceof Map) {
                    Object icon = ((Map<?, ?>) row).get("icon");
                    icons.add(icon instanceof String && !((String) icon).isEmpty() ? (String) icon : "");
                } else {
                    icons.add("");
                }
            }
        }
        return icons;
    }

    private static String text(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    static String quote(String value) {
        if (value == null) value = "";
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
            }
        }
        return json.append('"').toString();
    }
}
