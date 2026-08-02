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

  function shouldAutoHideControls(playerReady, isPaused, documentHidden) {
    return Boolean(playerReady && !isPaused && !documentHidden);
  }

  function pageSlice(items, page, pageSize) {
    var source = Array.isArray(items) ? items : [];
    var size = Math.max(1, Number(pageSize) || 1);
    var pageCount = Math.max(1, Math.ceil(source.length / size));
    var safePage = Math.max(0, Math.min(Number(page) || 0, pageCount - 1));
    var start = safePage * size;
    return {
      items: source.slice(start, start + size),
      page: safePage,
      pageCount: pageCount,
      start: start,
      total: source.length
    };
  }

  function nextGridIndex(index, count, columns, direction) {
    var current = Number(index);
    var total = Number(count);
    var width = Math.max(1, Number(columns) || 1);
    if (!(current >= 0 && current < total)) return -1;
    var column = current % width;
    if (direction === "left" && column > 0) return current - 1;
    if (direction === "right" && column < width - 1 && current + 1 < total) return current + 1;
    if (direction === "up" && current - width >= 0) return current - width;
    if (direction === "down" && current + width < total) return current + width;
    return -1;
  }

  return {
    closestInDirection: closestInDirection,
    encodeMediaPath: encodeMediaPath,
    episodeCount: episodeCount,
    firstEpisode: firstEpisode,
    formatTime: formatTime,
    nextGridIndex: nextGridIndex,
    pageSlice: pageSlice,
    shouldAutoHideControls: shouldAutoHideControls,
    mediaUrl: mediaUrl
  };
});
