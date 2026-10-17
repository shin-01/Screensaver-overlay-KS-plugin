// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.screensaveroverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compact the hourly forecast into the JSON string consumed by the renderer. */
final class Forecast {
    static final int MAX_ITEMS = 16;

    static String toJson(Object forecast) {
        if (!(forecast instanceof List)) return "[]";
        List<?> entries = (List<?>) forecast;
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (Object entry : entries) {
            if (!(entry instanceof Map) || count >= MAX_ITEMS) continue;
            Map<?, ?> item = (Map<?, ?>) entry;
            if (count > 0) json.append(',');
            json.append("{\"condition\":").append(OverlayData.quote(text(item.get("condition"))))
                    .append(",\"datetime\":").append(OverlayData.quote(text(item.get("datetime"))))
                    .append(",\"temperature\":").append(OverlayData.quote(text(item.get("temperature"))))
                    .append(",\"precipitation\":").append(OverlayData.quote(text(item.get("precipitation"))))
                    .append('}');
            count++;
        }
        return json.append(']').toString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
