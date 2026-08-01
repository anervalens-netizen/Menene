(function (root) {
  "use strict";

  function HtmlVideoBackend(video) {
    var self = this;
    this.video = video;
    this.onTime = function () {};
    this.onEnded = function () {};
    this.onError = function () {};
    this.boundTime = this.emitTime.bind(this);
    this.boundEnded = function () { self.onEnded(); };
    this.boundError = this.emitError.bind(this);
  }

  HtmlVideoBackend.prototype.open = function (url) {
    this.video.hidden = false;
    this.video.src = url;
    this.video.addEventListener("timeupdate", this.boundTime);
    this.video.addEventListener("ended", this.boundEnded);
    this.video.addEventListener("error", this.boundError);
    return this.video.play();
  };
  HtmlVideoBackend.prototype.emitTime = function () {
    this.onTime(this.currentTime(), this.duration());
  };
  HtmlVideoBackend.prototype.emitError = function () {
    this.onError("Redarea video a eșuat.");
  };
  HtmlVideoBackend.prototype.play = function () { return this.video.play(); };
  HtmlVideoBackend.prototype.pause = function () { this.video.pause(); };
  HtmlVideoBackend.prototype.isPaused = function () { return this.video.paused; };
  HtmlVideoBackend.prototype.seek = function (milliseconds) {
    this.video.currentTime = Math.max(0, milliseconds) / 1000;
  };
  HtmlVideoBackend.prototype.currentTime = function () { return (this.video.currentTime || 0) * 1000; };
  HtmlVideoBackend.prototype.duration = function () { return (this.video.duration || 0) * 1000; };
  HtmlVideoBackend.prototype.close = function () {
    this.video.pause();
    this.video.removeAttribute("src");
    this.video.load();
    this.video.hidden = true;
    this.video.removeEventListener("timeupdate", this.boundTime);
    this.video.removeEventListener("ended", this.boundEnded);
    this.video.removeEventListener("error", this.boundError);
  };

  function AvPlayBackend(surface) {
    this.surface = surface;
    this.onTime = function () {};
    this.onEnded = function () {};
    this.onError = function () {};
    this.paused = false;
    this.total = 0;
    this.ready = false;
    this.generation = 0;
  }

  AvPlayBackend.prototype.open = function (url) {
    var self = this;
    var generation = ++this.generation;
    this.ready = false;
    this.paused = true;
    this.total = 0;
    this.surface.hidden = false;
    return new Promise(function (resolve, reject) {
      var settled = false;
      function fail(message) {
        if (generation !== self.generation || settled) return;
        settled = true;
        self.ready = false;
        self.onError(message);
        reject(new Error(message));
      }
      try {
        webapis.avplay.open(url);
        webapis.avplay.setDisplayRect(0, 0, 1920, 1080);
        webapis.avplay.setDisplayMethod("PLAYER_DISPLAY_MODE_FULL_SCREEN");
        webapis.avplay.setListener({
          onbufferingstart: function () {},
          onbufferingprogress: function () {},
          onbufferingcomplete: function () {},
          oncurrentplaytime: function (milliseconds) {
            if (generation === self.generation) self.onTime(milliseconds, self.total);
          },
          onevent: function () {},
          onstreamcompleted: function () {
            if (generation === self.generation) self.onEnded();
          },
          onerror: function (error) {
            var message = "Redarea a eșuat: " + error;
            if (self.ready) self.onError(message); else fail(message);
          },
          onerrormsg: function (_error, message) {
            var detail = "Redarea a eșuat: " + message;
            if (self.ready) self.onError(detail); else fail(detail);
          },
          onsubtitlechange: function () {},
          ondrmevent: function () {}
        });
        webapis.avplay.prepareAsync(function () {
          if (generation !== self.generation || settled) return;
          try {
            self.total = webapis.avplay.getDuration();
            webapis.avplay.play();
            self.ready = true;
            self.paused = false;
            settled = true;
            resolve();
          } catch (error) {
            fail("Redarea nu a putut porni: " + error);
          }
        }, function (error) {
          fail("Fișierul nu poate fi pregătit: " + error);
        });
      } catch (error) {
        fail("Playerul nu a putut fi deschis: " + error);
      }
    });
  };
  AvPlayBackend.prototype.play = function () {
    if (!this.ready) return false;
    webapis.avplay.play();
    this.paused = false;
    return true;
  };
  AvPlayBackend.prototype.pause = function () {
    if (!this.ready) return false;
    webapis.avplay.pause();
    this.paused = true;
    return true;
  };
  AvPlayBackend.prototype.isPaused = function () { return this.paused; };
  AvPlayBackend.prototype.seek = function (milliseconds) {
    if (!this.ready) return false;
    webapis.avplay.seekTo(Math.max(0, Math.min(milliseconds, this.total || milliseconds)));
    return true;
  };
  AvPlayBackend.prototype.currentTime = function () {
    try { return webapis.avplay.getCurrentTime(); } catch (_error) { return 0; }
  };
  AvPlayBackend.prototype.duration = function () { return this.total; };
  AvPlayBackend.prototype.close = function () {
    this.generation += 1;
    this.ready = false;
    this.paused = true;
    this.total = 0;
    try { webapis.avplay.stop(); } catch (_error) {}
    try { webapis.avplay.close(); } catch (_error) {}
    this.surface.hidden = true;
  };

  function MenenePlayer(surface, video) {
    this.backend = root.webapis && root.webapis.avplay
      ? new AvPlayBackend(surface)
      : new HtmlVideoBackend(video);
  }
  MenenePlayer.prototype.open = function (url) { return this.backend.open(url); };
  MenenePlayer.prototype.play = function () { return this.backend.play(); };
  MenenePlayer.prototype.pause = function () { return this.backend.pause(); };
  MenenePlayer.prototype.toggle = function () {
    return this.backend.isPaused() ? this.backend.play() : this.backend.pause();
  };
  MenenePlayer.prototype.seek = function (milliseconds) { this.backend.seek(milliseconds); };
  MenenePlayer.prototype.jump = function (delta) {
    this.backend.seek(this.backend.currentTime() + delta);
  };
  MenenePlayer.prototype.currentTime = function () { return this.backend.currentTime(); };
  MenenePlayer.prototype.duration = function () { return this.backend.duration(); };
  MenenePlayer.prototype.isPaused = function () { return this.backend.isPaused(); };
  MenenePlayer.prototype.close = function () { this.backend.close(); };

  root.MenenePlayer = MenenePlayer;
})(typeof globalThis !== "undefined" ? globalThis : this);
