(function () {
  "use strict";

  var core = window.MeneneCore;
  var config = window.MENENE_CONFIG || {};
  var serverBase = String(config.serverBaseUrl || "").replace(/\/+$/, "");
  var PROGRESS_KEY = "menene-tv-progress-v1";
  var BACK_KEY = 10009;
  var state = {
    catalog: null,
    currentView: "loading",
    currentSeries: null,
    currentSeason: 0,
    currentEpisode: null,
    progress: loadProgress(),
    lastProgressWrite: 0,
    player: null,
    playerReady: false,
    playerSession: 0,
    endTimer: null,
    resumeAfterVisibility: false,
    toastTimer: null,
    lastFocus: null
  };

  var elements = {};

  function byId(id) { return document.getElementById(id); }
  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }
  function loadProgress() {
    try { return JSON.parse(localStorage.getItem(PROGRESS_KEY) || "{}"); }
    catch (_error) { return {}; }
  }
  function saveProgress() {
    try { localStorage.setItem(PROGRESS_KEY, JSON.stringify(state.progress)); }
    catch (_error) { showToast("Progresul nu a putut fi salvat."); }
  }
  function progressFor(episode) { return state.progress[episode.id] || { position: 0, duration: 0 }; }
  function progressPercent(episode) {
    var progress = progressFor(episode);
    return progress.duration ? Math.min(100, Math.round(progress.position * 100 / progress.duration)) : 0;
  }

  function bootstrap() {
    cacheElements();
    bindEvents();
    registerTvKeys();
    state.player = new window.MenenePlayer(elements.avPlayer, elements.htmlPlayer);
    state.player.backend.onTime = onPlayerTime;
    state.player.backend.onEnded = onPlayerEnded;
    state.player.backend.onError = function (message) { showToast(message); };
    loadLibrary();
  }

  function cacheElements() {
    ["header", "library-count", "connection-pill", "loading-view", "loading-message",
      "error-view", "error-message", "retry-button", "home-view", "hero-image",
      "hero-title", "hero-meta", "hero-play", "hero-play-label", "series-rail",
      "series-view", "series-back", "series-title", "season-tabs", "episode-grid",
      "player-view", "av-player", "html-player", "player-overlay", "player-back",
      "player-series", "player-title", "seek-bar", "current-time", "duration-time",
      "jump-back", "play-pause", "jump-forward", "toast", "exit-dialog",
      "exit-cancel", "exit-confirm"].forEach(function (id) {
        elements[id.replace(/-([a-z])/g, function (_match, letter) { return letter.toUpperCase(); })] = byId(id);
      });
  }

  function bindEvents() {
    elements.retryButton.addEventListener("click", loadLibrary);
    elements.seriesBack.addEventListener("click", showHome);
    elements.playerBack.addEventListener("click", function () { closePlayer(false); });
    elements.heroPlay.addEventListener("click", function () {
      var episode = continueEpisode(state.currentSeries) || core.firstEpisode(state.currentSeries);
      if (episode) startEpisode(state.currentSeries, episode);
    });
    elements.jumpBack.addEventListener("click", function () { jump(-10000); });
    elements.jumpForward.addEventListener("click", function () { jump(10000); });
    elements.playPause.addEventListener("click", togglePlayback);
    elements.seekBar.addEventListener("change", function () {
      if (!requirePlayerReady()) return;
      var target = state.player.duration() * Number(elements.seekBar.value) / 1000;
      state.player.seek(target);
      showToast("Poziție " + core.formatTime(target));
    });
    elements.exitCancel.addEventListener("click", closeExitDialog);
    elements.exitConfirm.addEventListener("click", exitApplication);
    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    document.addEventListener("focusin", function (event) {
      if (event.target.matches("[data-focusable]")) state.lastFocus = event.target;
      if (event.target.classList.contains("series-card")) {
        selectHero(event.target.dataset.seriesId);
      }
    });
  }

  function fetchJson(path) {
    return Promise.race([
      fetch(serverBase + path, { cache: "no-store" }).then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.json();
      }),
      new Promise(function (_resolve, reject) {
        setTimeout(function () { reject(new Error("timeout")); }, 8000);
      })
    ]);
  }

  function loadLibrary() {
    showOnly("loading");
    elements.loadingMessage.textContent = "Conectare la biblioteca Menene…";
    if (!/^https?:\/\//.test(serverBase)) {
      showError("Adresa serverului Menene nu este configurată.");
      return;
    }
    Promise.all([fetchJson("/api/v1/health"), fetchJson("/api/v1/catalog")])
      .then(function (results) {
        var health = results[0];
        var catalog = results[1];
        validateCatalog(catalog);
        state.catalog = catalog;
        elements.libraryCount.textContent = health.catalog.series + " seriale • " + health.catalog.episodes + " episoade";
        elements.connectionPill.classList.add("online");
        elements.connectionPill.textContent = "Bibliotecă online";
        renderHome();
      })
      .catch(function (error) {
        showError("Dell nu răspunde sau catalogul este invalid (" + error.message + ").");
      });
  }

  function validateCatalog(catalog) {
    if (!catalog || catalog.schemaVersion !== 1 || !Array.isArray(catalog.series)) {
      throw new Error("schema catalog incompatibilă");
    }
    catalog.series.forEach(function (series) {
      (series.seasons || []).forEach(function (season) {
        (season.episodes || []).forEach(function (episode) {
          if (episode.subtitle !== null) throw new Error("subtitrări nepermise");
          if (!episode.id || !episode.media) throw new Error("episod incomplet");
        });
      });
    });
  }

  function renderHome() {
    elements.seriesRail.innerHTML = state.catalog.series.map(function (series) {
      var cover = series.cover ? core.mediaUrl(serverBase, series.cover) : "assets/menene_series_story.webp";
      return '<button class="series-card focusable" data-focusable data-series-id="' + escapeHtml(series.id) + '">' +
        '<img src="' + escapeHtml(cover) + '" alt="" decoding="async" onerror="this.src=\'assets/menene_series_story.webp\'">' +
        '<span class="series-card-copy"><strong>' + escapeHtml(series.title) + '</strong>' +
        '<small>' + core.episodeCount(series) + ' episoade</small></span></button>';
    }).join("");

    elements.seriesRail.querySelectorAll(".series-card").forEach(function (card) {
      card.addEventListener("click", function () {
        showSeries(seriesById(card.dataset.seriesId));
      });
    });
    selectHero((state.currentSeries || state.catalog.series[0]).id);
    showOnly("home");
    focusFirst(elements.seriesRail);
  }

  function selectHero(seriesId) {
    var series = seriesById(seriesId);
    if (!series) return;
    state.currentSeries = series;
    elements.heroTitle.textContent = series.title;
    elements.heroMeta.textContent = (series.seasons || []).length + " sezoane • " + core.episodeCount(series) + " episoade";
    var hero = series.cover ? core.mediaUrl(serverBase, series.cover) : "assets/menene_hero_adventure.webp";
    elements.heroImage.src = hero;
    elements.heroImage.onerror = function () { elements.heroImage.src = "assets/menene_hero_adventure.webp"; };
    elements.heroPlayLabel.textContent = continueEpisode(series) ? "Continuă" : "Pornește";
  }

  function showSeries(series) {
    state.currentSeries = series;
    state.currentSeason = 0;
    elements.seriesTitle.textContent = series.title;
    renderSeasons();
    showOnly("series");
    focusFirst(elements.seasonTabs);
  }

  function renderSeasons() {
    var seasons = state.currentSeries.seasons || [];
    elements.seasonTabs.innerHTML = seasons.map(function (season, index) {
      var selected = index === state.currentSeason ? " selected" : "";
      return '<button class="season-tab focusable' + selected + '" data-focusable data-season="' + index + '">' +
        escapeHtml(season.title || ("Sezonul " + season.number)) + '</button>';
    }).join("");
    elements.seasonTabs.querySelectorAll(".season-tab").forEach(function (tab) {
      tab.addEventListener("click", function () {
        state.currentSeason = Number(tab.dataset.season);
        renderSeasons();
        focusFirst(elements.episodeGrid);
      });
    });
    renderEpisodes(seasons[state.currentSeason]);
  }

  function renderEpisodes(season) {
    var episodes = season ? season.episodes || [] : [];
    elements.episodeGrid.innerHTML = episodes.map(function (episode) {
      var artwork = episode.artwork ? core.mediaUrl(serverBase, episode.artwork) : "assets/menene_series_story.webp";
      var percent = progressPercent(episode);
      return '<button class="episode-card focusable" data-focusable data-episode-id="' + escapeHtml(episode.id) + '">' +
        '<img src="' + escapeHtml(artwork) + '" alt="" loading="lazy" decoding="async" onerror="this.src=\'assets/menene_series_story.webp\'">' +
        '<span class="episode-card-copy"><strong>' + escapeHtml(episode.title) + '</strong>' +
        '<small>' + core.formatTime(episode.durationMs) + '</small></span>' +
        '<span class="progress-track"><span class="progress-value" style="width:' + percent + '%"></span></span></button>';
    }).join("");
    elements.episodeGrid.querySelectorAll(".episode-card").forEach(function (card) {
      card.addEventListener("click", function () {
        startEpisode(state.currentSeries, episodeById(card.dataset.episodeId));
      });
    });
  }

  function startEpisode(series, episode) {
    if (!episode) return;
    state.currentSeries = series;
    state.currentEpisode = episode;
    state.playerSession += 1;
    var session = state.playerSession;
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = null;
    setPlayerReady(false);
    state.currentView = "player";
    elements.header.hidden = true;
    elements.homeView.hidden = true;
    elements.seriesView.hidden = true;
    elements.playerView.hidden = false;
    elements.playerSeries.textContent = series.title;
    elements.playerTitle.textContent = episode.title;
    elements.currentTime.textContent = "00:00";
    elements.durationTime.textContent = core.formatTime(episode.durationMs);
    elements.seekBar.value = "0";
    elements.playPause.textContent = "Ⅱ";
    var url = core.mediaUrl(serverBase, episode.media);
    state.player.open(url).then(function () {
      if (session !== state.playerSession || state.currentEpisode !== episode) return;
      setPlayerReady(true);
      var progress = progressFor(episode);
      if (progress.position > 15000 && progress.position < progress.duration - 15000) {
        state.player.seek(progress.position);
      }
      if (document.hidden) {
        state.resumeAfterVisibility = true;
        state.player.pause();
        elements.playPause.textContent = "▶";
        return;
      }
      elements.playPause.focus();
    }).catch(function () {
      if (session !== state.playerSession) return;
      setPlayerReady(false);
      showToast("Redarea nu a putut porni.");
      elements.playerBack.focus();
    });
    elements.playerBack.focus();
  }

  function onPlayerTime(position, duration) {
    if (!state.currentEpisode || !state.playerReady) return;
    var total = duration || state.currentEpisode.durationMs || 0;
    elements.currentTime.textContent = core.formatTime(position);
    elements.durationTime.textContent = core.formatTime(total);
    elements.seekBar.value = total ? String(Math.round(position * 1000 / total)) : "0";
    var now = Date.now();
    if (now - state.lastProgressWrite > 5000) {
      state.progress[state.currentEpisode.id] = { position: position, duration: total, updatedAt: now };
      state.lastProgressWrite = now;
      saveProgress();
    }
  }

  function saveCurrentProgress() {
    if (!state.currentEpisode || !state.playerReady) return;
    var current = state.player.currentTime();
    var duration = state.player.duration() || state.currentEpisode.durationMs || 0;
    state.progress[state.currentEpisode.id] = {
      position: current,
      duration: duration,
      updatedAt: Date.now()
    };
    saveProgress();
  }

  function handleVisibilityChange() {
    if (state.currentView !== "player" || !state.currentEpisode) return;
    if (document.hidden) {
      state.resumeAfterVisibility = state.playerReady && !state.player.isPaused();
      if (state.playerReady) {
        saveCurrentProgress();
        state.player.pause();
        elements.playPause.textContent = "▶";
      }
      return;
    }
    if (state.resumeAfterVisibility && state.playerReady) {
      state.player.play();
      elements.playPause.textContent = "Ⅱ";
    }
    state.resumeAfterVisibility = false;
  }

  function onPlayerEnded() {
    if (state.currentView !== "player" || !state.currentEpisode) return;
    var duration = state.player.duration() || state.currentEpisode.durationMs || 0;
    state.progress[state.currentEpisode.id] = { position: duration, duration: duration, updatedAt: Date.now() };
    saveProgress();
    showToast("Episod terminat");
    setPlayerReady(false);
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = setTimeout(function () { closePlayer(true); }, 700);
  }

  function closePlayer(keepStoredProgress) {
    if (state.currentView !== "player" || !state.currentEpisode) return;
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = null;
    var episode = state.currentEpisode;
    if (!keepStoredProgress && state.playerReady) {
      var current = state.player.currentTime();
      var duration = state.player.duration() || episode.durationMs || 0;
      state.progress[episode.id] = { position: current, duration: duration, updatedAt: Date.now() };
      saveProgress();
    }
    state.currentEpisode = null;
    state.playerSession += 1;
    state.resumeAfterVisibility = false;
    setPlayerReady(false);
    state.player.close();
    elements.playerView.hidden = true;
    elements.header.hidden = false;
    state.currentView = "series";
    elements.seriesView.hidden = false;
    renderSeasons();
    setTimeout(function () {
      var card = elements.episodeGrid.querySelector('[data-episode-id="' + episode.id + '"]');
      (card || elements.seriesBack).focus();
    }, 0);
  }

  function setPlayerReady(ready) {
    state.playerReady = ready;
    [elements.seekBar, elements.jumpBack, elements.playPause, elements.jumpForward]
      .forEach(function (control) { control.disabled = !ready; });
  }

  function requirePlayerReady() {
    if (state.playerReady) return true;
    showToast("Playerul se pregătește…");
    return false;
  }

  function togglePlayback() {
    if (!requirePlayerReady()) return;
    state.player.toggle();
    elements.playPause.textContent = state.player.isPaused() ? "▶" : "Ⅱ";
  }
  function jump(delta) {
    if (!requirePlayerReady()) return;
    state.player.jump(delta);
    showToast(delta < 0 ? "Înapoi 10 secunde" : "Înainte 10 secunde");
  }

  function handleKeyDown(event) {
    var code = event.keyCode;
    if (code === BACK_KEY || code === 27) {
      event.preventDefault();
      handleBack();
      return;
    }
    if (state.currentView === "player") {
      if (code === 10252) { event.preventDefault(); togglePlayback(); return; }
      if (code === 415) { event.preventDefault(); if (requirePlayerReady()) { state.player.play(); elements.playPause.textContent = "Ⅱ"; } return; }
      if (code === 19) { event.preventDefault(); if (requirePlayerReady()) { state.player.pause(); elements.playPause.textContent = "▶"; } return; }
      if (code === 412) { event.preventDefault(); jump(-10000); return; }
      if (code === 417) { event.preventDefault(); jump(10000); return; }
      if (document.activeElement === elements.seekBar && (code === 37 || code === 39)) {
        event.preventDefault(); jump(code === 37 ? -10000 : 10000); return;
      }
    }
    var direction = {37: "left", 38: "up", 39: "right", 40: "down"}[code];
    if (!direction) return;
    var current = document.activeElement;
    if (!current || !current.matches("[data-focusable]")) return;
    var candidates = Array.prototype.slice.call(document.querySelectorAll("[data-focusable]"));
    var target = core.closestInDirection(current, candidates, direction);
    if (target) {
      event.preventDefault();
      target.focus();
      target.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" });
    }
  }

  function handleBack() {
    if (!elements.exitDialog.hidden) { closeExitDialog(); return; }
    if (state.currentView === "player") { closePlayer(false); return; }
    if (state.currentView === "series") { showHome(); return; }
    if (state.currentView === "home") {
      elements.exitDialog.hidden = false;
      setTimeout(function () { elements.exitCancel.focus(); }, 0);
    }
  }

  function registerTvKeys() {
    if (!window.tizen || !tizen.tvinputdevice) return;
    ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaRewind", "MediaFastForward"].forEach(function (key) {
      try { tizen.tvinputdevice.registerKey(key); } catch (_error) {}
    });
  }

  function showOnly(view) {
    state.currentView = view;
    elements.header.hidden = view === "player";
    elements.loadingView.hidden = view !== "loading";
    elements.errorView.hidden = view !== "error";
    elements.homeView.hidden = view !== "home";
    elements.seriesView.hidden = view !== "series";
    elements.playerView.hidden = view !== "player";
  }
  function showHome() {
    renderHome();
  }
  function showError(message) {
    elements.errorMessage.textContent = message;
    elements.connectionPill.classList.remove("online");
    elements.connectionPill.textContent = "Bibliotecă offline";
    showOnly("error");
    setTimeout(function () { elements.retryButton.focus(); }, 0);
  }
  function showToast(message) {
    clearTimeout(state.toastTimer);
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    state.toastTimer = setTimeout(function () { elements.toast.hidden = true; }, 1800);
  }
  function closeExitDialog() {
    elements.exitDialog.hidden = true;
    setTimeout(function () { (state.lastFocus || elements.heroPlay).focus(); }, 0);
  }
  function exitApplication() {
    if (window.tizen && tizen.application) tizen.application.getCurrentApplication().exit();
    else window.close();
  }
  function focusFirst(container) {
    setTimeout(function () {
      var first = container.querySelector("[data-focusable]");
      if (first) first.focus();
    }, 0);
  }
  function seriesById(id) {
    return state.catalog.series.find(function (series) { return series.id === id; });
  }
  function episodeById(id) {
    var result = null;
    (state.currentSeries.seasons || []).some(function (season) {
      result = (season.episodes || []).find(function (episode) { return episode.id === id; });
      return Boolean(result);
    });
    return result;
  }
  function continueEpisode(series) {
    var candidate = null;
    var latest = 0;
    (series.seasons || []).forEach(function (season) {
      (season.episodes || []).forEach(function (episode) {
        var progress = progressFor(episode);
        if (progress.position > 15000 && progress.duration && progress.position < progress.duration - 15000 && progress.updatedAt > latest) {
          candidate = episode;
          latest = progress.updatedAt;
        }
      });
    });
    return candidate;
  }

  document.addEventListener("DOMContentLoaded", bootstrap);
})();
