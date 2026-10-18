// Animated weather-mood background: an original canvas scene driven by the
// current weather condition, day/night and rain state. Runs entirely inside
// the renderer document with requestAnimationFrame.
(function () {
  "use strict";

  var MOOD_PRESETS = {
    "sunny":           { clouds: 0.00, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 0.00, storm: 0.00 },
    "clear-night":     { clouds: 0.00, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 0.00, storm: 0.00 },
    "partlycloudy":    { clouds: 0.15, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 0.05, storm: 0.00 },
    "cloudy":          { clouds: 0.60, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 0.10, storm: 0.00 },
    "fog":             { clouds: 0.35, rain: 0.0,  snow: 0.0,  fog: 1.0, wind: 0.03, storm: 0.00 },
    "rainy":           { clouds: 0.75, rain: 0.55, snow: 0.0,  fog: 0.1, wind: 0.25, storm: 0.05 },
    "pouring":         { clouds: 1.00, rain: 1.0,  snow: 0.0,  fog: 0.2, wind: 0.55, storm: 0.45 },
    "snowy":           { clouds: 0.65, rain: 0.0,  snow: 1.0,  fog: 0.1, wind: 0.20, storm: 0.00 },
    "snowy-rainy":     { clouds: 0.85, rain: 0.5,  snow: 0.7,  fog: 0.1, wind: 0.30, storm: 0.10 },
    "hail":            { clouds: 0.90, rain: 0.5,  snow: 0.3,  fog: 0.1, wind: 0.40, storm: 0.35 },
    "lightning":       { clouds: 0.95, rain: 0.3,  snow: 0.0,  fog: 0.1, wind: 0.45, storm: 0.80 },
    "lightning-rainy": { clouds: 1.00, rain: 0.85, snow: 0.0,  fog: 0.1, wind: 0.50, storm: 0.80 },
    "windy":           { clouds: 0.10, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 1.0,  storm: 0.00 },
    "windy-variant":   { clouds: 0.70, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 1.0,  storm: 0.05 },
    "exceptional":     { clouds: 0.45, rain: 0.0,  snow: 0.0,  fog: 0.0, wind: 0.10, storm: 0.00 }
  };

  function lerp(a, b, t) { return a + (b - a) * t; }

  function WeatherMood(canvas, options) {
    this.canvas = canvas;
    this.context = canvas.getContext("2d");
    this.options = options || {};
    var preset = MOOD_PRESETS[this.condition()] || MOOD_PRESETS["exceptional"];
    this.values = { clouds: preset.clouds, rain: preset.rain, snow: preset.snow,
                     fog: preset.fog, wind: preset.wind, storm: preset.storm };
    this.target = { clouds: preset.clouds, rain: preset.rain, snow: preset.snow,
                     fog: preset.fog, wind: preset.wind, storm: preset.storm };
    this.time = 0;
    this.lastStamp = null;
    this.drops = [];
    this.flakes = [];
    this.clouds = [];
    this.flash = 0;
    this.nextStrike = 3;
    for (var i = 0; i < 7; i++) {
      this.clouds.push({
        x: Math.random(), y: 0.08 + Math.random() * 0.30,
        scale: 0.5 + Math.random() * 0.9, speed: 0.3 + Math.random() * 0.5,
        seed: Math.random() * 1000
      });
    }
    this.resize();
  }

  WeatherMood.prototype.condition = function () {
    var condition = this.options.weatherCondition || "";
    if (this.options.rainState === "raining" && condition.indexOf("rain") === -1 && condition !== "pouring") {
      return "rainy";
    }
    return condition;
  };

  WeatherMood.prototype.night = function () {
    return this.options.sunState === "below_horizon";
  };

  WeatherMood.prototype.palette = function () {
    var night = this.night();
    var storm = this.values.storm;
    var top = night ? [10, 14, 26] : [110, 165, 220];
    var bottom = night ? [26, 32, 48] : [175, 215, 240];
    top = [top[0] * (1 - storm * 0.55), top[1] * (1 - storm * 0.55), top[2] * (1 - storm * 0.35)];
    bottom = [bottom[0] * (1 - storm * 0.55), bottom[1] * (1 - storm * 0.55), bottom[2] * (1 - storm * 0.45)];
    return { top: top, bottom: bottom };
  };

  WeatherMood.prototype.resize = function () {
    var ratio = Math.min(window.devicePixelRatio || 1, 2);
    var width = this.canvas.clientWidth || window.innerWidth;
    var height = this.canvas.clientHeight || window.innerHeight;
    this.canvas.width = Math.round(width * ratio);
    this.canvas.height = Math.round(height * ratio);
    this.width = this.canvas.width;
    this.height = this.canvas.height;
  };

  WeatherMood.prototype.update = function (seconds) {
    this.time += seconds;
    var easing = Math.min(1, seconds * 1.2);
    var keys = ["clouds", "rain", "snow", "fog", "wind", "storm"];
    var preset = MOOD_PRESETS[this.condition()] || MOOD_PRESETS["exceptional"];
    if (this.options.rainState === "raining" && preset.rain < 0.5) preset = MOOD_PRESETS["rainy"];
    this.target = { clouds: preset.clouds, rain: preset.rain, snow: preset.snow,
                     fog: preset.fog, wind: preset.wind, storm: preset.storm };
    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      this.values[key] = lerp(this.values[key], this.target[key], easing);
    }
    // Lightning strikes while the storm builds.
    if (this.values.storm > 0.3) {
      this.nextStrike -= seconds;
      if (this.nextStrike <= 0) {
        this.flash = 0.9 + Math.random() * 0.4;
        this.nextStrike = 2 + Math.random() * 6;
      }
    }
    this.flash = Math.max(0, this.flash - seconds * 2.4);
    this.updateParticles(seconds);
  };

  WeatherMood.prototype.updateParticles = function (seconds) {
    var rain = this.values.rain, snow = this.values.snow, wind = this.values.wind;
    var needed = Math.round(rain * 140);
    while (this.drops.length < needed) {
      this.drops.push({ x: Math.random(), y: Math.random(), speed: 0.7 + Math.random() * 0.5 });
    }
    while (this.drops.length > needed) this.drops.pop();
    for (var i = 0; i < this.drops.length; i++) {
      var drop = this.drops[i];
      drop.y += drop.speed * seconds * 1.4;
      drop.x += (0.25 + wind * 0.5) * seconds * 0.4;
      if (drop.y > 1.05) { drop.y = -0.05; drop.x = Math.random(); }
      if (drop.x > 1.1) drop.x -= 1.2;
    }
    var flakesNeeded = Math.round(snow * 90);
    while (this.flakes.length < flakesNeeded) {
      this.flakes.push({ x: Math.random(), y: Math.random(), speed: 0.08 + Math.random() * 0.08, sway: Math.random() * Math.PI * 2 });
    }
    while (this.flakes.length > flakesNeeded) this.flakes.pop();
    for (var j = 0; j < this.flakes.length; j++) {
      var flake = this.flakes[j];
      flake.sway += seconds * 2;
      flake.y += flake.speed * seconds;
      flake.x += Math.sin(flake.sway) * seconds * 0.05 + wind * seconds * 0.08;
      if (flake.y > 1.03) { flake.y = -0.03; flake.x = Math.random(); }
      if (flake.x > 1.05) flake.x -= 1.1;
      if (flake.x < -0.05) flake.x += 1.1;
    }
  };

  WeatherMood.prototype.draw = function () {
    var context = this.context, width = this.width, height = this.height;
    var night = this.night();
    var colors = this.palette();
    var sky = context.createLinearGradient(0, 0, 0, height);
    sky.addColorStop(0, "rgb(" + Math.round(colors.top[0]) + "," + Math.round(colors.top[1]) + "," + Math.round(colors.top[2]) + ")");
    sky.addColorStop(1, "rgb(" + Math.round(colors.bottom[0]) + "," + Math.round(colors.bottom[1]) + "," + Math.round(colors.bottom[2]) + ")");
    context.fillStyle = sky;
    context.fillRect(0, 0, width, height);

    // Sun or moon glow.
    if (this.values.storm < 0.7) {
      var glowX = width * 0.78, glowY = height * 0.16;
      var glow = context.createRadialGradient(glowX, glowY, 0, glowX, glowY, height * 0.5);
      if (night) {
        glow.addColorStop(0, "rgba(235, 240, 255, 0.35)");
        glow.addColorStop(1, "rgba(235, 240, 255, 0)");
      } else {
        glow.addColorStop(0, "rgba(255, 245, 215, 0.55)");
        glow.addColorStop(1, "rgba(255, 245, 215, 0)");
      }
      context.fillStyle = glow;
      context.fillRect(0, 0, width, height);
    }

    this.drawClouds(context, width, height, night);

    if (this.values.rain > 0.01) this.drawRain(context, width, height);
    if (this.values.snow > 0.01) this.drawSnow(context, width, height);
    if (this.values.fog > 0.01) this.drawFog(context, width, height, night);

    if (this.flash > 0.02) {
      context.fillStyle = "rgba(255, 255, 255, " + Math.min(0.85, this.flash * 0.5) + ")";
      context.fillRect(0, 0, width, height);
    }
  };

  WeatherMood.prototype.drawClouds = function (context, width, height, night) {
    var clouds = this.values.clouds;
    if (clouds < 0.02) return;
    for (var i = 0; i < this.clouds.length; i++) {
      var cloud = this.clouds[i];
      var visible = clouds > cloud.scale * 0.55 || i < 2;
      if (!visible) continue;
      var opacity = Math.min(1, clouds * 1.4) * 0.75;
      var x = (cloud.x + this.time * 0.004 * cloud.speed * (1 + this.values.wind)) % 1.4 - 0.2;
      var y = cloud.y;
      var scale = cloud.scale * (0.6 + clouds * 0.8);
      var cloudWidth = width * 0.34 * scale;
      var cloudHeight = cloudWidth * 0.32;
      var px = x * width, py = y * height;
      var shade = night ? 0.16 : 0.55;
      shade *= (1 - this.values.storm * 0.5);
      context.fillStyle = "rgba(" + Math.round(shade * 255) + "," + Math.round(shade * 255) + "," + Math.round(shade * 255 + 10) + "," + opacity + ")";
      context.beginPath();
      context.ellipse(px, py, cloudWidth, cloudHeight, 0, 0, Math.PI * 2);
      context.fill();
      context.beginPath();
      context.ellipse(px - cloudWidth * 0.4, py + cloudHeight * 0.15, cloudWidth * 0.5, cloudHeight * 0.7, 0, 0, Math.PI * 2);
      context.fill();
      context.beginPath();
      context.ellipse(px + cloudWidth * 0.45, py + cloudHeight * 0.2, cloudWidth * 0.45, cloudHeight * 0.6, 0, 0, Math.PI * 2);
      context.fill();
    }
  };

  WeatherMood.prototype.drawRain = function (context, width, height) {
    context.strokeStyle = "rgba(190, 215, 240, 0.55)";
    context.lineWidth = Math.max(1, width / 900);
    context.beginPath();
    for (var i = 0; i < this.drops.length; i++) {
      var drop = this.drops[i];
      var x = drop.x * width, y = drop.y * height;
      var length = height * 0.02 * drop.speed;
      context.moveTo(x, y);
      context.lineTo(x - length * (0.2 + this.values.wind * 0.6), y + length);
    }
    context.stroke();
  };

  WeatherMood.prototype.drawSnow = function (context, width, height) {
    context.fillStyle = "rgba(255, 255, 255, 0.85)";
    for (var i = 0; i < this.flakes.length; i++) {
      var flake = this.flakes[i];
      var radius = Math.max(1, width / 480 * (0.5 + flake.speed * 3));
      context.beginPath();
      context.arc(flake.x * width, flake.y * height, radius, 0, Math.PI * 2);
      context.fill();
    }
  };

  WeatherMood.prototype.drawFog = function (context, width, height, night) {
    var layers = 3;
    for (var i = 0; i < layers; i++) {
      var bandY = height * (0.15 + i * 0.3);
      var band = context.createLinearGradient(0, bandY - height * 0.2, 0, bandY + height * 0.25);
      var alpha = this.values.fog * (0.22 - i * 0.04);
      band.addColorStop(0, "rgba(" + (night ? "150" : "225") + "," + (night ? "155" : "228") + "," + (night ? "165" : "232") + ",0)");
      band.addColorStop(0.5, "rgba(" + (night ? "150" : "225") + "," + (night ? "155" : "228") + "," + (night ? "165" : "232") + "," + alpha + ")");
      band.addColorStop(1, "rgba(" + (night ? "150" : "225") + "," + (night ? "155" : "228") + "," + (night ? "165" : "232") + ",0)");
      context.fillStyle = band;
      context.fillRect(0, bandY - height * 0.25, width, height * 0.5);
    }
  };

  WeatherMood.prototype.frame = function (stamp) {
    if (this.lastStamp === null) this.lastStamp = stamp;
    var seconds = Math.min(0.1, (stamp - this.lastStamp) / 1000);
    this.lastStamp = stamp;
    this.update(seconds);
    this.draw();
    var mood = this;
    window.requestAnimationFrame(function (next) { mood.frame(next); });
  };

  WeatherMood.prototype.start = function () {
    var mood = this;
    window.requestAnimationFrame(function (next) { mood.frame(next); });
  };

  window.ScreensaverOverlayWeatherMood = WeatherMood;
})();
