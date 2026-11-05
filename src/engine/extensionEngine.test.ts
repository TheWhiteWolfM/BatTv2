import test from 'node:test';
import assert from 'node:assert/strict';
import {
  evaluateUblockRequest,
  evaluatePopupAttempt,
  resolveShortlinkUrl,
  parseCustomFilterRules,
  sanitizeHtmlWithExtensions,
} from './extensionEngine';

test('uBlock Origin blocks known ad and tracker domains and respects toggle state', () => {
  const blockedAd = evaluateUblockRequest('https://cdn.popads.net/popunder.js', true);
  assert.equal(blockedAd.blocked, true);
  assert.equal(blockedAd.matchedRule, '||popads.net^');

  const allowedWhenDisabled = evaluateUblockRequest('https://cdn.popads.net/popunder.js', false);
  assert.equal(allowedWhenDisabled.blocked, false);

  const cleanContent = evaluateUblockRequest('https://archive.org/details/movies', true);
  assert.equal(cleanContent.blocked, false);
});

test('uBlock Origin parses and enforces custom user filter rules', () => {
  const rules = parseCustomFilterRules(`
    ! Comentario de usuario
    ||spamstream-ads.org^
    casino-banner.net
  `);
  assert.deepEqual(rules, ['spamstream-ads.org', 'casino-banner.net']);

  const res = evaluateUblockRequest('https://static.spamstream-ads.org/banner.js', true, rules);
  assert.equal(res.blocked, true);
  assert.equal(res.matchedRule, 'custom:spamstream-ads.org');
});

test('Popup Blocker (strict) blocks window.open and overlay clickjacking unless whitelisted', () => {
  const blockedAttempt = evaluatePopupAttempt(
    'https://malicious-ad-redirect.com/offer',
    'window.open',
    true,
    ['archive.org']
  );
  assert.equal(blockedAttempt.allowed, false);

  const whitelistedAttempt = evaluatePopupAttempt(
    'https://sub.archive.org/player',
    'window.open',
    true,
    ['archive.org']
  );
  assert.equal(whitelistedAttempt.allowed, true);
});

test('Violentmonkey shortlink bypasser resolves query params, Base64 tokens, and known shorteners', () => {
  // 1. Direct query param ?url=
  const directQuery = resolveShortlinkUrl(
    'https://ouo.io/qs/12345?url=https%3A%2F%2Fexample.com%2Fvideo.mp4',
    true
  );
  assert.equal(directQuery.isShortlink, true);
  assert.equal(directQuery.bypassed, true);
  assert.equal(directQuery.finalUrl, 'https://example.com/video.mp4');

  // 2. Base64 query param ?r=
  const b64Target = Buffer.from('https://stream.example.org/hls/live.m3u8').toString('base64');
  const b64Query = resolveShortlinkUrl(`https://linkvertise.com/48192?r=${b64Target}`, true);
  assert.equal(b64Query.bypassed, true);
  assert.equal(b64Query.finalUrl, 'https://stream.example.org/hls/live.m3u8');

  // 3. Built-in demo shortlink
  const demoShortlink = resolveShortlinkUrl('https://ouo.io/pelicula-1080p', true);
  assert.equal(demoShortlink.bypassed, true);
  assert.ok(demoShortlink.finalUrl.endsWith('TearsOfSteel.mp4'));
});

test('sanitizeHtmlWithExtensions strips ad scripts, clickjack overlays, rewrites shortlinks, and extracts video streams', () => {
  const dirtyHtml = `
    <html>
      <head>
        <script src="https://propellerads.com/tag.js"></script>
      </head>
      <body>
        <div style="position:fixed;z-index:99999;opacity:0;" onclick="window.open('https://popads.net/1')"></div>
        <a href="https://ouo.io/go?url=https%3A%2F%2Fcdn.server.com%2Fmovie-1080p.mp4">Ver Película</a>
        <script>
          const streamSource = "https://live.tvchannel.org/stream/master.m3u8";
        </script>
      </body>
    </html>
  `;

  const result = sanitizeHtmlWithExtensions(dirtyHtml, {
    ublockEnabled: true,
    popupStrictEnabled: true,
    violentmonkeyEnabled: true,
    lowRamStripAnimations: true,
  });

  assert.equal(result.blockedAdsCount, 1);
  assert.ok(result.blockedPopupsCount >= 1);
  assert.equal(result.bypassedLinksCount, 1);
  assert.ok(result.sanitizedHtml.includes('href="https://cdn.server.com/movie-1080p.mp4"'));
  assert.ok(result.detectedStreams.includes('https://live.tvchannel.org/stream/master.m3u8'));
  assert.ok(result.ramSavedKb > 0);
});
