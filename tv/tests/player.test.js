"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const playerModule = require.resolve("../app/js/player.js");

function createHarness() {
  const calls = { play: 0, pause: 0, seek: 0, stop: 0, close: 0 };
  const mock = {
    open() {},
    setDisplayRect() {},
    setDisplayMethod() {},
    setListener(listener) { mock.listener = listener; },
    prepareAsync(success, failure) {
      mock.prepareSuccess = success;
      mock.prepareFailure = failure;
    },
    getDuration() { return 120000; },
    getCurrentTime() { return 5000; },
    play() { calls.play += 1; },
    pause() { calls.pause += 1; },
    seekTo() { calls.seek += 1; },
    stop() { calls.stop += 1; },
    close() { calls.close += 1; }
  };
  global.webapis = { avplay: mock };
  delete require.cache[playerModule];
  require(playerModule);
  const player = new global.MenenePlayer({ hidden: true }, {});
  return { calls, mock, player };
}

test.afterEach(() => {
  delete global.webapis;
  delete global.MenenePlayer;
  delete require.cache[playerModule];
});

test("AVPlay resolves open only after prepare and guards early controls", async () => {
  const { calls, mock, player } = createHarness();
  let resolved = false;
  const opening = player.open("http://media/E01.mp4").then(() => { resolved = true; });

  await Promise.resolve();
  assert.equal(resolved, false);
  assert.equal(player.backend.seek(1000), false);
  assert.equal(player.backend.play(), false);
  assert.equal(calls.seek, 0);
  assert.equal(calls.play, 0);

  mock.prepareSuccess();
  await opening;
  assert.equal(resolved, true);
  assert.equal(calls.play, 1);
  assert.equal(player.backend.seek(1000), true);
  assert.equal(calls.seek, 1);
});

test("close invalidates a stale AVPlay prepare callback", () => {
  const { calls, mock, player } = createHarness();
  player.open("http://media/E01.mp4");
  player.close();
  mock.prepareSuccess();

  assert.equal(calls.play, 0);
  assert.equal(calls.stop, 1);
  assert.equal(calls.close, 1);
  assert.equal(player.backend.ready, false);
});
