(function (root, factory) {
  var api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  root.MeneneCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function formatTime(milliseconds) {
    var totalSeconds = Math.max(0, Math.floor((Number(milliseconds) || 0) / 1000));
    var hours = Math.floor(totalSeconds / 3600);
    var minutes = Math.floor((totalSeconds % 3600) / 60);
    var seconds = totalSeconds % 60;
    var body = String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0");
    return hours ? String(hours) + ":" + body : body;
  }

  function encodeMediaPath(path) {
    return String(path || "")
      .split("/")
      .filter(Boolean)
      .map(function (part) { return encodeURIComponent(part); })
      .join("/");
  }

  function mediaUrl(baseUrl, path) {
    return String(baseUrl).replace(/\/+$/, "") + "/media/" + encodeMediaPath(path);
  }

  function episodeCount(series) {
    return (series.seasons || []).reduce(function (total, season) {
      return total + (season.episodes || []).length;
    }, 0);
  }

  function firstEpisode(series) {
    var seasons = series.seasons || [];
    for (var i = 0; i < seasons.length; i += 1) {
      if (seasons[i].episodes && seasons[i].episodes.length) {
        return seasons[i].episodes[0];
      }
    }
    return null;
  }

  function closestInDirection(origin, candidates, direction) {
    var originRect = origin.getBoundingClientRect();
    var originX = originRect.left + originRect.width / 2;
    var originY = originRect.top + originRect.height / 2;
    var best = null;
    var bestScore = Infinity;

    candidates.forEach(function (candidate) {
      if (candidate === origin || candidate.disabled || candidate.offsetParent === null) return;
      var rect = candidate.getBoundingClientRect();
      var x = rect.left + rect.width / 2;
      var y = rect.top + rect.height / 2;
      var dx = x - originX;
      var dy = y - originY;
      var primary;
      var secondary;

      if (direction === "left") { primary = -dx; secondary = Math.abs(dy); }
      if (direction === "right") { primary = dx; secondary = Math.abs(dy); }
      if (direction === "up") { primary = -dy; secondary = Math.abs(dx); }
      if (direction === "down") { primary = dy; secondary = Math.abs(dx); }
      if (!(primary > 2)) return;

      var score = primary + secondary * 2.4;
      if (score < bestScore) {
        best = candidate;
        bestScore = score;
      }
    });
    return best;
  }

  return {
    closestInDirection: closestInDirection,
    encodeMediaPath: encodeMediaPath,
    episodeCount: episodeCount,
    firstEpisode: firstEpisode,
    formatTime: formatTime,
    mediaUrl: mediaUrl
  };
});
