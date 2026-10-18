# Screensaver Overlay for Kiosk Satellite

A [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin that ports the look and
options of the [Screensaver Overlay](https://github.com/madmicio/screensaver_overlay) Home
Assistant integration into a Kiosk Satellite screensaver renderer.

The screensaver shows, on a black or image background:

- clock with weekday and date
- current weather icon with an optional textual description
- external temperature, optionally paired with an internal temperature in the house layout
- hourly forecast timeline with colored temperatures and precipitation
- upcoming calendar events for the next 7 days, with a discreet red dot while a `cg_alert`
  event is active
- sensor values with friendly name and unit, alternating with calendar events
- status markers for configured entities while they are active

Kiosk Satellite keeps owning idle activation, dismissal, brightness, screen-off, motion and
presence wake, pixel shift, widgets and Now Playing layouts, exactly as with the stock
screensavers. Select **Screensaver Overlay (Screensaver Overlay)** under
**Screensaver > Screensaver mode** or in a screensaver schedule.

## Settings

All settings are global for this kiosk and save automatically:

| Group | Setting | Meaning |
| --- | --- | --- |
| Weather | Weather entity | Main weather entity for icon, temperature and forecast |
| Weather | Weather description entity | Text under the weather icon |
| Weather | Rain sensor | When its state is `raining`, the rain icon is used |
| Weather | Show weather icon, icon size, hourly forecast, forecast background opacity | Layout of the weather blocks |
| Temperatures | External / internal temperature entities | External overrides the weather temperature; internal shows the house layout |
| Info | Calendars, value entities, status icon entities | Content lists |
| Info | Show info block, info text size, maximum info items | Layout of the info block |
| Layout | Show clock, show status icons | Block visibility |
| Layout | At a Glance row is visible, Corner widgets are visible | Turn these on to match the stock screensaver extras: with At a Glance the info block moves up and the hourly forecast sits higher so nothing hides under the bottom row; with corner widgets the status icons, weather description and alert dot move below the widget line |
| Background | Black / Default images / Default images carousel, carousel interval | Background behavior |

Entity states update live through Kiosk Satellite's Home Assistant connection. Calendar events
refresh every 5 minutes; the hourly forecast follows Home Assistant's forecast subscription.

## Differences from the Home Assistant integration

- Brightness control, Fully Kiosk, Companion App notifications, screen switches and the
  per-browser configuration of the original integration are not part of this plugin: Kiosk
  Satellite provides brightness, screen control and wake behavior natively.
- Background images are the four bundled defaults. The WebView that renders plugin screensavers
  cannot reach the network or Home Assistant uploads, so user-uploaded backgrounds are not
  supported.
- There is one configuration per kiosk rather than one per browser.

## Building

```sh
python3 tools/test.py
python3 tools/build.py
```

Both need a JDK 17 or newer and an Android SDK with a platform and build-tools, with
`ANDROID_HOME` and `JAVA_HOME` set as described in the
[plugin SDK guide](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world/blob/main/docs/creating-plugins.md).

The built ZIP in `dist/` can be installed through **Plugin Manager > Developer Tools >
Install from ZIP**. Release packages are produced by the GitHub Actions workflow on a
published release tag.

## Credits

This plugin reuses the weather icons, font, default backgrounds and visual design of
[madmicio/screensaver_overlay](https://github.com/madmicio/screensaver_overlay). The plugin
code is licensed under Apache-2.0; see [LICENSE](LICENSE).
