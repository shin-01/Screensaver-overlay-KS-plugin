// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.screensaveroverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extract upcoming events from a calendar.get_events service response. */
final class CalendarEvents {
    static final String ALERT_SUMMARY = "cg_alert";

    static String toJson(Object result, int limit) {
        if (!(result instanceof Map)) return "[]";
        Object response = ((Map<?, ?>) result).get("response");
        if (!(response instanceof Map)) response = result;
        List<Object[]> events = new ArrayList<>();
        collect(((Map<?, ?>) response).values(), events);
        events.sort((first, second) -> Long.compare(((Long) first[0]).longValue(), ((Long) second[0]).longValue()));
        long now = System.currentTimeMillis();
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        boolean alertActive = false;
        for (Object[] event : events) {
            String summary = (String) event[1];
            long start = (Long) event[0];
            long end = (Long) event[2];
            if (ALERT_SUMMARY.equals(summary)) {
                if (start <= now && now <= end) alertActive = true;
                continue;
            }
            if (count >= limit) continue;
            if (count > 0) json.append(',');
            json.append("{\"summary\":").append(OverlayData.quote(summary))
                    .append(",\"start\":").append(start / 1000.0)
                    .append(",\"end\":").append(end / 1000.0)
                    .append('}');
            count++;
        }
        json.append(']');
        return alertActive ? "{\"alert\":true,\"events\":" + json + "}"
                : "{\"alert\":false,\"events\":" + json + "}";
    }

    private static void collect(Iterable<?> values, List<Object[]> events) {
        for (Object value : values) {
            if (value instanceof Map) {
                collectMap((Map<?, ?>) value, events);
            } else if (value instanceof List) {
                collect((List<?>) value, events);
            }
        }
    }

    private static void collectMap(Map<?, ?> map, List<Object[]> events) {
        Object list = map.get("events");
        if (list instanceof List) {
            for (Object entry : (List<?>) list) {
                if (entry instanceof Map) {
                    Map<?, ?> event = (Map<?, ?>) entry;
                    String summary = text(event.get("summary"));
                    long start = time(event.get("start"));
                    long end = time(event.get("end"));
                    if (start == Long.MIN_VALUE || end == Long.MIN_VALUE) continue;
                    events.add(new Object[] {start, summary, end});
                }
            }
        }
        for (Object value : map.values()) {
            if (value instanceof Map) {
                Map<?, ?> nested = (Map<?, ?>) value;
                if (nested.containsKey("events")) collectMap(nested, events);
            }
        }
    }

    private static long time(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object dateTime = map.get("dateTime");
            if (dateTime != null) return parse(dateTime);
            Object date = map.get("date");
            if (date != null) return parse(date);
            return Long.MIN_VALUE;
        }
        return value == null ? Long.MIN_VALUE : parse(value);
    }

    private static long parse(Object value) {
        try {
            String text = String.valueOf(value);
            if (text.length() == 10) {
                return java.time.LocalDate.parse(text)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return java.time.Instant.parse(text).toEpochMilli();
        } catch (RuntimeException error) {
            return Long.MIN_VALUE;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
