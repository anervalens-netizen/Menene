"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const core = require("../app/js/core.js");

test("formats TV playback times", () => {
  assert.equal(core.formatTime(0), "00:00");
  assert.equal(core.formatTime(65_000), "01:05");
  assert.equal(core.formatTime(3_665_000), "1:01:05");
});

test("encodes every media path segment without losing hierarchy", () => {
  assert.equal(
    core.encodeMediaPath("Bluey/Season 01/Episodul #1.mp4"),
    "Bluey/Season%2001/Episodul%20%231.mp4"
  );
  assert.equal(
    core.mediaUrl("http://192.168.0.19:8765/", "Bluey/E 01.mp4"),
    "http://192.168.0.19:8765/media/Bluey/E%2001.mp4"
  );
});

test("counts and selects episodes", () => {
  const series = {
    seasons: [
      { episodes: [] },
      { episodes: [{ id: "one" }, { id: "two" }] }
    ]
  };
  assert.equal(core.episodeCount(series), 2);
  assert.equal(core.firstEpisode(series).id, "one");
});

test("auto-hide is armed only while ready playback is visible", () => {
  assert.equal(core.shouldAutoHideControls(true, false, false), true);
  assert.equal(core.shouldAutoHideControls(false, false, false), false);
  assert.equal(core.shouldAutoHideControls(true, true, false), false);
  assert.equal(core.shouldAutoHideControls(true, false, true), false);
});
