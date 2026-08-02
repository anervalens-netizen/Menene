(function () {
  "use strict";

  var core = window.MeneneCore;
  var config = window.MENENE_CONFIG || {};
  var serverBase = String(config.serverBaseUrl || "").replace(/\/+$/, "");
  var PROGRESS_KEY = "menene-tv-progress-v1";
  var BACK_KEY = 10009;
  var EPISODE_PAGE_SIZE = 8;
  var EPISODE_COLUMNS = 4;
  var HOME_COLUMNS = 4;
  var state = {
    catalog: null,
    currentView: "loading",
    currentSeries: null,
    currentSeason: 0,
    currentEpisode: null,
    currentEpisodePage: 0,
    progress: loadProgress(),
    lastProgressWrite: 0,
    player: null,
    playerReady: false,
    playerSession: 0,
    endTimer: null,
    controlsTimer: null,
    resumeAfterVisibility: false,
    toastTimer: null,
    lastFocus: null,
    heroSeriesId: null,
    heroTimer: null
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
      "error-view", "error-message", "retry-button", "home-view", "hero-artwork", "hero-image",
      "hero-title", "hero-meta", "hero-play", "hero-play-label", "series-list",
      "series-view", "series-back", "series-title", "season-tabs", "episode-grid", "episode-page-status",
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
    elements.seriesList.addEventListener("click", function (event) {
      var card = cardFromEvent(event, elements.seriesList, "series-card");
      if (card) showSeries(seriesById(card.dataset.seriesId));
    });
    elements.playPause.addEventListener("click", togglePlayback);
    elements.episodeGrid.addEventListener("click", function (event) {
      var card = event.target;
      while (card && card !== elements.episodeGrid && !card.classList.contains("episode-card")) {
        card = card.parentNode;
      }
      if (card && card !== elements.episodeGrid) {
        startEpisode(state.currentSeries, episodeById(card.dataset.episodeId));
      }
    });
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
    elements.playerView.addEventListener("pointerdown", showPlayerControls);
    elements.playerView.addEventListener("touchstart", showPlayerControls);
    document.addEventListener("focusin", function (event) {
      if (event.target.matches("[data-focusable]")) state.lastFocus = event.target;
      if (event.target.classList.contains("series-card")) {
        var seriesId = event.target.dataset.seriesId;
        clearTimeout(state.heroTimer);
        state.heroTimer = setTimeout(function () {
          state.heroTimer = null;
          if (state.currentView === "home") selectHero(seriesId);
        }, 220);
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

  function cardFromEvent(event, container, className) {
    var card = event.target;
    while (card && card !== container && !card.classList.contains(className)) card = card.parentNode;
    return card && card !== container ? card : null;
  }

  function artworkUrl(path, fallback) {
    if (!path) return fallback;
    var revision = (state.catalog && state.catalog.catalogRevision) || config.appVersion || "1.1.1";
    return core.mediaUrl(serverBase, path) + "?v=" + encodeURIComponent(revision);
  }

  function prepareArtworkFrames(container, fallback) {
    Array.prototype.forEach.call(container.querySelectorAll(".artwork-frame img"), function (image) {
      var frame = image.parentNode;
      frame.style.backgroundImage = "";
      image.onerror = function () {
        image.onerror = null;
        image.src = fallback;
        frame.style.backgroundImage = "";
      };
    });
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
        showError("Biblioteca NAS nu răspunde sau catalogul este invalid (" + error.message + ").");
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
    elements.seriesList.innerHTML = state.catalog.series.map(function (series, index) {
      var cover = artworkUrl(series.cardArtwork || series.cover, "assets/menene_series_story.webp");
      return '<button class="series-card focusable" data-focusable data-series-id="' + escapeHtml(series.id) + '" data-grid-index="' + index + '">' +
        '<span class="artwork-frame series-artwork">' +
        '<img src="' + escapeHtml(cover) + '" alt="" decoding="async"></span>' +
        '<span class="series-card-copy"><strong>' + escapeHtml(series.displayTitle || series.title) + '</strong>' +
        '<small>' + core.episodeCount(series) + ' episoade</small></span></button>';
    }).join("");
    prepareArtworkFrames(elements.seriesList, "assets/menene_series_story.webp");
    selectHero((state.currentSeries || state.catalog.series[0]).id, true);
    showOnly("home");
    focusFirst(elements.seriesList);
  }

  function setHeroArtworkBackground(url) {
    var safeUrl = String(url || "").replace(/"/g, "%22");
    elements.heroArtwork.style.backgroundImage = 'url("' + safeUrl + '")';
  }

  function selectHero(seriesId, force) {
    var series = seriesById(seriesId);
    if (!series) return;
    if (!force && state.heroSeriesId === seriesId) return;
    state.heroSeriesId = seriesId;
    state.currentSeries = series;
    elements.heroTitle.textContent = series.displayTitle || series.title;
    elements.heroMeta.textContent = (series.seasons || []).length + " sezoane • " + core.episodeCount(series) + " episoade";
    var fallback = "assets/menene_hero_adventure.webp";
    var hero = artworkUrl(series.heroArtwork || series.cover, fallback);
    setHeroArtworkBackground(hero);
    elements.heroImage.src = hero;
    elements.heroImage.onerror = function () {
      elements.heroImage.onerror = null;
      setHeroArtworkBackground(fallback);
      elements.heroImage.src = fallback;
    };
    elements.heroPlayLabel.textContent = continueEpisode(series) ? "Continuă" : "Pornește";
  }

  function showSeries(series) {
    state.currentSeries = series;
    clearTimeout(state.heroTimer);
    state.heroTimer = null;
    state.currentSeason = 0;
    state.currentEpisodePage = 0;
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
        state.currentEpisodePage = 0;
        renderSeasons();
        focusFirst(elements.episodeGrid);
      });
    });
    renderEpisodes(seasons[state.currentSeason]);
  }

  function renderEpisodes(season) {
    var episodes = season ? season.episodes || [] : [];
    var page = core.pageSlice(episodes, state.currentEpisodePage, EPISODE_PAGE_SIZE);
    state.currentEpisodePage = page.page;
    elements.episodeGrid.innerHTML = page.items.map(function (episode, index) {
      var artwork = artworkUrl(episode.cardArtwork || episode.artwork, "assets/menene_series_story.webp");
      var percent = progressPercent(episode);
      var globalIndex = page.start + index;
      return '<button class="episode-card focusable" data-focusable data-page-index="' + index +
        '" data-episode-index="' + globalIndex + '" data-episode-id="' + escapeHtml(episode.id) + '">' +
        '<span class="artwork-frame episode-artwork">' +
        '<img src="' + escapeHtml(artwork) + '" alt="" decoding="async"></span>' +
        '<span class="episode-card-copy"><strong>' + escapeHtml(episode.displayTitle || episode.title) + '</strong>' +
        '<small>' + core.formatTime(episode.durationMs) + '</small></span>' +
        '<span class="progress-track"><span class="progress-value" style="width:' + percent + '%"></span></span></button>';
    }).join("");
    prepareArtworkFrames(elements.episodeGrid, "assets/menene_series_story.webp");
    if (!page.total) {
      elements.episodePageStatus.textContent = "Niciun episod";
    } else {
      elements.episodePageStatus.textContent = "Episoade " + (page.start + 1) + "–" +
        Math.min(page.start + EPISODE_PAGE_SIZE, page.total) + " din " + page.total + " • Pagina " + (page.page + 1) + "/" + page.pageCount;
    }
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
    showPlayerControls();
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
      showPlayerControls();
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
      clearPlayerControlsTimer();
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
      showPlayerControls();
    }
    state.resumeAfterVisibility = false;
  }

  function onPlayerEnded() {
    if (state.currentView !== "player" || !state.currentEpisode) return;
    var duration = state.player.duration() || state.currentEpisode.durationMs || 0;
    state.progress[state.currentEpisode.id] = { position: duration, duration: duration, updatedAt: Date.now() };
    saveProgress();
    showToast("Episod terminat");
    showPlayerControls();
    setPlayerReady(false);
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = setTimeout(function () { closePlayer(true); }, 700);
  }

  function closePlayer(keepStoredProgress) {
    if (state.currentView !== "player" || !state.currentEpisode) return;
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = null;
    clearPlayerControlsTimer();
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
    elements.playerView.classList.remove("is-controls-hidden");
    elements.header.hidden = false;
    state.currentView = "series";
    elements.seriesView.hidden = false;
    updateEpisodeCardProgress(episode);
    setTimeout(function () {
      var card = elements.episodeGrid.querySelector('[data-episode-id="' + episode.id + '"]');
      (card || elements.seriesBack).focus();
    }, 0);
  }

  function updateEpisodeCardProgress(episode) {
    var card = elements.episodeGrid.querySelector('[data-episode-id="' + episode.id + '"]');
    var value = card && card.querySelector(".progress-value");
    if (value) value.style.width = progressPercent(episode) + "%";
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

  function setPlaybackPaused(paused) {
    if (!requirePlayerReady()) return false;
    if (paused) state.player.pause(); else state.player.play();
    elements.playPause.textContent = state.player.isPaused() ? "▶" : "Ⅱ";
    showPlayerControls();
    return true;
  }

  function togglePlayback() {
    setPlaybackPaused(!state.player.isPaused());
  }
  function jump(delta) {
    if (!requirePlayerReady()) return;
    state.player.jump(delta);
    showToast(delta < 0 ? "Înapoi 10 secunde" : "Înainte 10 secunde");
    showPlayerControls();
  }

  function clearPlayerControlsTimer() {
    if (state.controlsTimer) clearTimeout(state.controlsTimer);
    state.controlsTimer = null;
  }

  function showPlayerControls() {
    if (state.currentView !== "player") return;
    clearPlayerControlsTimer();
    elements.playerView.classList.remove("is-controls-hidden");
    if (core.shouldAutoHideControls(state.playerReady, state.player.isPaused(), document.hidden)) {
      state.controlsTimer = setTimeout(hidePlayerControls, 4000);
    }
  }

  function hidePlayerControls() {
    state.controlsTimer = null;
    if (state.currentView !== "player" || !state.playerReady || state.player.isPaused() || document.hidden) return;
    elements.playerView.classList.add("is-controls-hidden");
  }

  function focusElement(element) {
    if (!element) return false;
    element.focus();
    return true;
  }

  function currentSeasonData() {
    return (state.currentSeries.seasons || [])[state.currentSeason] || { episodes: [] };
  }

  function selectedSeasonTab() {
    return elements.seasonTabs.querySelector(".season-tab.selected");
  }

  function renderEpisodePage(page, preferredIndex) {
    state.currentEpisodePage = page;
    renderEpisodes(currentSeasonData());
    var cards = Array.prototype.slice.call(elements.episodeGrid.querySelectorAll(".episode-card"));
    return focusElement(cards[Math.max(0, Math.min(preferredIndex, cards.length - 1))]);
  }
  function handleStructuredNavigation(event, current, direction) {
    var items;
    var index;
    var target;

    if (state.currentView === "home") {
      if (current === elements.heroPlay && direction === "down") {
        event.preventDefault();
        return focusElement(elements.seriesList.querySelector(".series-card"));
      }
      if (current.classList.contains("series-card")) {
        items = Array.prototype.slice.call(elements.seriesList.querySelectorAll(".series-card"));
        index = items.indexOf(current);
        if (direction === "up" && index < HOME_COLUMNS) {
          target = elements.heroPlay;
        } else {
          var homeIndex = core.nextGridIndex(index, items.length, HOME_COLUMNS, direction);
          if (homeIndex >= 0) target = items[homeIndex];
        }
        if (target) {
          event.preventDefault();
          return focusElement(target);
        }
      }
      return false;
    }

    if (state.currentView !== "series") return false;
    if (current === elements.seriesBack && direction === "down") {
      event.preventDefault();
      return focusElement(selectedSeasonTab());
    }
    if (current.classList.contains("season-tab")) {
      items = Array.prototype.slice.call(elements.seasonTabs.querySelectorAll(".season-tab"));
      index = items.indexOf(current);
      if (direction === "left" && index > 0) target = items[index - 1];
      if (direction === "right" && index + 1 < items.length) target = items[index + 1];
      if (direction === "up") target = elements.seriesBack;
      if (direction === "down") target = elements.episodeGrid.querySelector(".episode-card");
      if (target) {
        event.preventDefault();
        return focusElement(target);
      }
      return false;
    }
    if (!current.classList.contains("episode-card")) return false;

    items = Array.prototype.slice.call(elements.episodeGrid.querySelectorAll(".episode-card"));
    index = Number(current.dataset.pageIndex);
    var nextIndex = core.nextGridIndex(index, items.length, EPISODE_COLUMNS, direction);
    if (nextIndex >= 0) {
      event.preventDefault();
      return focusElement(items[nextIndex]);
    }

    var total = (currentSeasonData().episodes || []).length;
    var pageCount = Math.max(1, Math.ceil(total / EPISODE_PAGE_SIZE));
    var column = index % EPISODE_COLUMNS;
    if (direction === "down" && state.currentEpisodePage + 1 < pageCount) {
      event.preventDefault();
      return renderEpisodePage(state.currentEpisodePage + 1, column);
    }
    if (direction === "up" && state.currentEpisodePage > 0) {
      event.preventDefault();
      return renderEpisodePage(state.currentEpisodePage - 1, EPISODE_COLUMNS + column);
    }
    if (direction === "up" && state.currentEpisodePage === 0) {
      event.preventDefault();
      return focusElement(selectedSeasonTab());
    }
    return false;
  }

  function handleKeyDown(event) {
    var code = event.keyCode;
    if (state.currentView === "player") showPlayerControls();
    if (code === BACK_KEY || code === 27) {
      event.preventDefault();
      handleBack();
      return;
    }
    if (state.currentView === "player") {
      if (code === 10252) { event.preventDefault(); togglePlayback(); return; }
      if (code === 415) { event.preventDefault(); setPlaybackPaused(false); return; }
      if (code === 19) { event.preventDefault(); setPlaybackPaused(true); return; }
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
    if (handleStructuredNavigation(event, current, direction)) return;
    if (state.currentView === "home" || state.currentView === "series") return;

    var focusRoot = elements.exitDialog.hidden ? elements.playerView : elements.exitDialog;
    var candidates = Array.prototype.slice.call(focusRoot.querySelectorAll("[data-focusable]"));
    var target = core.closestInDirection(current, candidates, direction);
    if (target) {
      event.preventDefault();
      focusElement(target);
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
    showOnly("home");
    selectHero(state.currentSeries.id, true);
    setTimeout(function () {
      var card = elements.seriesList.querySelector('[data-series-id="' + state.currentSeries.id + '"]');
      focusElement(card || elements.heroPlay);
    }, 0);
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
      if (first) focusElement(first);
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
