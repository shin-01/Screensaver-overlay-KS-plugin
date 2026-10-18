// Screensaver Overlay renderer for Kiosk Satellite. Configuration and data arrive as a flat
// scalar JSON map in the location fragment; the document cannot reach Kiosk Satellite or
// Home Assistant directly.
(function () {
  "use strict";

  var DEFAULT_BACKGROUNDS = [
    "default_backgrounds/default-1.webp",
    "default_backgrounds/default-2.webp",
    "default_backgrounds/default-3.webp",
    "default_backgrounds/default-4.webp"
  ];

  var options = {};
  try {
    options = JSON.parse(decodeURIComponent(location.hash.slice(1) || "%7B%7D")) || {};
  } catch (error) {
    options = {};
  }

  var events = [];
  var calendarAlert = false;
  try {
    var calendar = JSON.parse(options.calendarEvents || "{}");
    events = calendar.events || [];
    calendarAlert = !!calendar.alert;
  } catch (error) {
    events = [];
  }

  var forecast = [];
  try {
    forecast = JSON.parse(options.hourlyForecast || "[]") || [];
  } catch (error) {
    forecast = [];
  }

  var valueEntities = [];
  try {
    valueEntities = JSON.parse(options.valueEntities || "[]") || [];
  } catch (error) {
    valueEntities = [];
  }

  var statusIcons = [];
  try {
    statusIcons = JSON.parse(options.statusIcons || "[]") || [];
  } catch (error) {
    statusIcons = [];
  }

  var showSensorValues = false;
  var infoTimer = undefined;
  var clockTimer = undefined;
  var backgroundTimer = undefined;
  var backgroundIndex = 0;
  var activeLayer = 0;

  var overlay = document.getElementById("overlay");
  var screen = overlay.querySelector(".screen");

  var show = function (key) { return options[key] !== false; };
  var html = function (value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  };

  var formatTemperature = function (value) {
    var text = String(value == null ? "" : value).replace(",", ".");
    var numeric = parseFloat(text);
    return isFinite(numeric) ? numeric.toFixed(1) : text;
  };

  var formatTime = function (date) {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  };

  var weatherIcons = {
    "clear": "day",
    "clear-night": "night",
    "cloudy": "cloudy",
    "fog": "fog",
    "hail": "hail",
    "lightning": "lightning",
    "lightning-rainy": "lightning-rainy",
    "partlycloudy": "partlycloudy",
    "pouring": "pouring",
    "rainy": "rainy",
    "snowy": "snowy",
    "snowy-rainy": "snowy-rainy",
    "sunny": "day",
    "windy": "windy",
    "windy-variant": "windy-variant",
    "exceptional": "clear"
  };

  var currentWeatherIcon = function () {
    var condition = options.weatherCondition;
    if (!condition) return "";
    if (condition === "partlycloudy") {
      return options.sunState === "above_horizon" ? "partlycloudy" : "partlycloudy-night";
    }
    if (options.rainState === "raining") return "raining";
    return weatherIcons[condition] || condition;
  };

  var temperaturesHtml = function () {
    var external = formatTemperature(options.externalTemperature);
    var internal = formatTemperature(options.internalTemperature);
    if (internal === "") {
      return '<div class="ext-temp">' + html(external) + "&#176;</div>";
    }
    return '<svg class="temperature-svg" version="1.1" viewBox="0 0 1152.78 354.73" aria-hidden="true">' +
      '<g><path class="temperature-home" fill="currentColor" d="M840.45 3.79c-4.57 0-9.14 1.65-12.74 4.97L555.94 147.19c-12.6 11.61-4.39 32.65 12.74 32.65h33.18c8.25 0 14.94 6.69 14.94 14.94v138.86c0 8.47 8.83 15.24 17.26 15.24h69.4c4.16 0 7.52-3.37 7.52-7.52s-3.37-7.52-7.52-7.52h-69.4c-.68 0-1.7-.52-2.21-.99V194.78c0-16.57-13.43-30-30-30h-33.09c-1.59 0-3.04-.96-3.6-2.44-.59-1.56-.25-2.93.98-4.07L837.9 19.83c.89-.82 1.88-.99 2.55-.99.67 0 1.65.17 2.54.99l282.6 138.44c1.24 1.14 1.56 2.51.97 4.07-.56 1.48-2.01 2.44-3.59 2.44h-29.34c-16.57 0-30 13.43-30 30v24.55c0 4.16 3.37 7.52 7.52 7.52s7.52-3.37 7.52-7.52v-24.55c0-8.25 6.69-14.94 14.94-14.94h29.43c17.14 0 25.35-21.04 12.74-32.65L853.18 8.75c-3.6-3.31-8.16-4.96-12.73-4.96z"/></g>' +
      '<text class="temperature-text" x="600" y="290" text-anchor="middle">' + html(external) + "&#176;</text>" +
      '<text class="temperature-text" x="850" y="290" text-anchor="middle">' + html(internal) + "&#176;</text>" +
      "</svg>";
  };

  var clockHtml = function () {
    var now = new Date();
    var dayName = now.toLocaleDateString(undefined, { weekday: "short" });
    var day = now.toLocaleDateString(undefined, { day: "2-digit" });
    var month = now.toLocaleDateString(undefined, { month: "2-digit" });
    var year = now.toLocaleDateString(undefined, { year: "2-digit" });
    return '<div class="clock"><div class="time"><span class="time-value">' +
      html(formatTime(now)) + '</span><div class="date"><div>' + html(dayName) +
      "</div><div>:</div><div>" + html(day) + "</div><div>:</div><div>" +
      html(month) + "</div><div>:</div><div>" + html(year) + "</div></div></div></div>";
  };

  var eventsHtml = function () {
    var limit = Math.max(1, Math.floor(Number(options.infoItemsLimit) || 5));
    var items = events.slice(0, limit);
    if (!items.length) return '<div class="no-events">No events</div>';
    return '<div class="events">' + items.map(function (event) {
      var start = new Date(event.start * 1000);
      var end = new Date(event.end * 1000);
      return '<div class="event"><div class="event-title">' + html(event.summary) +
        '</div><div class="event-time">' + html(formatTime(start)) + " - " +
        html(formatTime(end)) + "</div></div>";
    }).join("") + "</div>";
  };

  var valuesHtml = function () {
    var limit = Math.max(1, Math.floor(Number(options.infoItemsLimit) || 5));
    return '<div id="entityState">' + valueEntities.slice(0, limit).map(function (entity) {
      return '<div class="entity"><span class="friendly-name">' + html(entity.name) +
        '</span><div class="value"><span class="state">' + html(formatTemperature(entity.state)) +
        '</span><span class="unit">' + html(entity.unit) + "</span></div></div>";
    }).join("") + "</div>";
  };

  var statusIconsHtml = function () {
    return statusIcons.map(function (item) {
      return '<span class="marker" title="' + html(item.name) + '"></span>';
    }).join("");
  };

  var forecastHtml = function () {
    if (!show("showHourlyForecast") || !forecast.length) return "";
    var unit = options.weatherTemperatureUnit || "";
    var previous = "";
    var items = forecast.slice(0, 16).map(function (item) {
      var icon = weatherIcons[item.condition] || "clear";
      var showCondition = item.condition !== previous;
      previous = item.condition;
      var temperature = Number(item.temperature);
      var temperatureClass = "";
      if (isFinite(temperature)) {
        if (unit === "\u00b0F") temperatureClass = temperature < 41 ? "cold" : temperature < 55 ? "cool" : temperature < 77 ? "" : temperature <= 86 ? "warm" : "hot";
        else temperatureClass = temperature < 5 ? "cold" : temperature < 13 ? "cool" : temperature < 25 ? "" : temperature <= 30 ? "warm" : "hot";
      }
      var time = new Date(item.datetime);
      var hour = isNaN(time.getTime()) ? "" : formatTime(time);
      return '<div class="timeline-item">' +
        '<div class="condition">' + (showCondition ? '<img src="weather_icons/' + html(icon) + '.svg" alt="">' : "") + "</div>" +
        '<div class="details"><div class="hour">' + html(hour) + '</div>' +
        '<div class="temperature ' + temperatureClass + '">' + html(item.temperature) + html(unit) + "</div>" +
        (item.precipitation ? '<div class="precipitation">' + html(item.precipitation) + "</div>" : "") +
        "</div></div>";
    }).join("");
    return '<div class="forecast" style="--forecast-opacity:' +
      Number(options.hourlyForecastBackgroundOpacity == null ? 0.7 : options.hourlyForecastBackgroundOpacity) +
      ';"><div class="gradient-bar"></div><div class="timeline">' + items + "</div></div>";
  };

  var render = function () {
    var parts = [];
    if (calendarAlert) parts.push('<div class="cg-alert" aria-hidden="true"></div>');
    if (show("showStatusIcons")) parts.push('<div class="status-icons">' + statusIconsHtml() + "</div>");
    if (options.weatherDescription) {
      parts.push('<div class="weather-description">' + html(options.weatherDescription) + "</div>");
    }
    if (show("showWeatherIcon")) {
      var icon = currentWeatherIcon();
      parts.push('<div class="weather-icon" style="--icon-size:' +
        Number(options.weatherIconSize || 27) + 'vw">' +
        (icon ? '<img src="weather_icons/now_icon/' + html(icon) + '.svg" alt="">' : "") + "</div>");
    }
    if (show("showTemperatures")) parts.push('<div class="temperatures">' + temperaturesHtml() + "</div>");
    if (show("showClock")) parts.push(clockHtml());
    if (show("showInfo")) {
      parts.push('<div class="info" style="--info-size:' + Number(options.infoTextSize || 2) +
        "vh;--info-secondary-size:" + Math.max(0.8, Number(options.infoTextSize || 2) * 0.85) + 'vh">' +
        renderInfoContent() + "</div>");
    }
    var timeline = forecastHtml();
    if (timeline) parts.push(timeline);
    screen.innerHTML = parts.join("");
  };

  var renderInfoContent = function () {
    if (valueEntities.length && (!events.length || showSensorValues)) return valuesHtml();
    return eventsHtml();
  };

  var updateInfo = function () {
    var info = screen.querySelector(".info");
    if (info) info.innerHTML = renderInfoContent();
  };

  var updateClock = function () {
    var timeElement = screen.querySelector(".time-value");
    if (timeElement) timeElement.textContent = formatTime(new Date());
  };

  var backgroundImages = function () {
    var mode = String(options.backgroundMode || "black");
    if (mode.indexOf("image") === -1 && mode.indexOf("carousel") === -1) return [];
    return DEFAULT_BACKGROUNDS;
  };

  var setBackground = function () {
    var layers = overlay.querySelectorAll(".background-image");
    var images = backgroundImages();
    if (!images.length) {
      layers.forEach(function (layer) {
        layer.style.backgroundImage = "";
        layer.classList.remove("is-active");
      });
      overlay.classList.remove("has-background");
      return;
    }
    if (backgroundIndex >= images.length) backgroundIndex = 0;
    var nextLayer = layers.length > 1 ? layers[(activeLayer + 1) % layers.length] : layers[0];
    nextLayer.style.backgroundImage = 'url("' + images[backgroundIndex] + '")';
    nextLayer.classList.add("is-active");
    layers.forEach(function (layer, index) {
      if (index !== Array.prototype.indexOf.call(layers, nextLayer)) layer.classList.remove("is-active");
    });
    activeLayer = (activeLayer + 1) % Math.max(1, layers.length);
    overlay.classList.add("has-background");
  };

  var startBackgroundCarousel = function () {
    if (backgroundTimer !== undefined) window.clearInterval(backgroundTimer);
    backgroundIndex = 0;
    setBackground();
    var images = backgroundImages();
    if (images.length <= 1 || String(options.backgroundMode).indexOf("carousel") === -1) return;
    var seconds = Number(options.backgroundCarouselInterval);
    if (!isFinite(seconds) || seconds <= 0) seconds = 60;
    backgroundTimer = window.setInterval(function () {
      backgroundIndex = (backgroundIndex + 1) % backgroundImages().length;
      setBackground();
    }, seconds * 1000);
  };

  var startTimers = function () {
    if (clockTimer === undefined && show("showClock")) {
      clockTimer = window.setInterval(updateClock, 1000);
    }
    if (infoTimer === undefined && show("showInfo") && valueEntities.length && events.length) {
      infoTimer = window.setInterval(function () {
        showSensorValues = !showSensorValues;
        updateInfo();
      }, 7000);
    }
  };

  if (options.withAtAGlance) overlay.classList.add("with-glance");
  if (options.withCornerWidgets) overlay.classList.add("with-corners");
  render();
  startBackgroundCarousel();
  startTimers();

  window.addEventListener("resize", function () {
    render();
    startBackgroundCarousel();
  });
})();
