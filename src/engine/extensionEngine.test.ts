import test from 'node:test';
import assert from 'node:assert/strict';
import {
  evaluateUblockRequest,
  evaluatePopupAttempt,
  resolveShortlinkUrl,
  parseCustomFilterRules,
  sanitizeHtmlWithExtensions,
  detectVideoPlayerServer,
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
  assert.ok(demoShortlink.finalUrl.endsWith('.mp4'));
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
        <iframe src="https://voe.sx/e/abc123xyz"></iframe>
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
  assert.ok(result.sanitizedHtml.includes('allowfullscreen="true"'));
  assert.ok(result.detectedStreams.includes('https://live.tvchannel.org/stream/master.m3u8'));
  assert.ok(result.ramSavedKb > 0);
});

test('detectVideoPlayerServer recognizes VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH, and Twitch.tv', () => {
  assert.equal(detectVideoPlayerServer('https://voe.sx/e/12345')?.id, 'voe');
  assert.equal(detectVideoPlayerServer('https://streamtape.com/e/xyz')?.id, 'streamtape');
  assert.equal(detectVideoPlayerServer('https://streamwish.to/e/999')?.id, 'streamwish');
  assert.equal(detectVideoPlayerServer('https://do7go.com/e/fhmt0r14nuyq')?.id, 'streamwish');
  assert.equal(detectVideoPlayerServer('https://ok.ru/videoembed/1234')?.id, 'okru');
  assert.equal(detectVideoPlayerServer('https://byse.sx/e/abcd')?.id, 'byse');
  assert.equal(detectVideoPlayerServer('https://bysebuho.com/e/qdtkxpgp9n2t')?.id, 'byse');
  assert.equal(detectVideoPlayerServer('https://primeload.co/embed/IXbQKcQwo2Bq')?.id, 'primeload');
  assert.equal(detectVideoPlayerServer('https://kisskh.do/Drama/test')?.id, 'kisskh');
  assert.equal(detectVideoPlayerServer('https://www.twitch.tv/caedrel')?.id, 'twitch');
});

test('Violentmonkey decodes embedshortener.co JWT episode links and uBlock blocks rotated popunder scripts', () => {
  const realVoeUrl = 'https://voe.sx/e/2cnrrrru3lya';
  const jwtPayload = Buffer.from(
    JSON.stringify({ link: Buffer.from(realVoeUrl).toString('base64') })
  ).toString('base64');
  const fakeJwtUrl = `https://embedshortener.co/e/eyJhbGciOiJIUzI1NiJ9.${jwtPayload}.sig`;

  const resolved = resolveShortlinkUrl(fakeJwtUrl, true);
  assert.equal(resolved.isShortlink, true);
  assert.equal(resolved.bypassed, true);
  assert.equal(resolved.finalUrl, realVoeUrl);

  assert.equal(
    evaluateUblockRequest('https://kg.blurtsbrerd.com/rl055mugwmjbwmVUp/143240', true).blocked,
    true
  );
  assert.equal(
    evaluateUblockRequest('https://aarems.org/1/c38639b693a37040742d58a61b6b8409', true).blocked,
    true
  );
  assert.equal(
    evaluateUblockRequest(
      'https://dramachino.com/wp-content/plugins/advanced-ads-layer/public/assets/js/layer.js',
      true
    ).blocked,
    true
  );
});

test('sanitizeHtmlWithExtensions removes sandbox from player iframes for VOE/Byse compatibility and strips Cloudflare challenge scripts', () => {
  const htmlWithSandboxedVoe = `
    <html>
      <head>
        <script src="/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1"></script>
      </head>
      <body>
        <iframe src="https://voe.sx/e/abc123xyz" sandbox="allow-scripts allow-same-origin"></iframe>
      </body>
    </html>
  `;
  const res = sanitizeHtmlWithExtensions(htmlWithSandboxedVoe, {
    ublockEnabled: true,
    popupStrictEnabled: true,
    violentmonkeyEnabled: true,
  });
  assert.ok(!res.sanitizedHtml.includes('sandbox='));
  assert.ok(!res.sanitizedHtml.includes('cdn-cgi/challenge-platform'));
  assert.equal(detectVideoPlayerServer('https://bysefujedu.com/e/123')?.id, 'byse');
  assert.equal(detectVideoPlayerServer('https://n1mwq.org/e/123')?.id, 'byse');
  assert.equal(detectVideoPlayerServer('https://sfastwish.com/e/123')?.id, 'streamwish');
  assert.equal(resolveShortlinkUrl('https://voe.sx/e/abc123xyz', true).bypassed, false);
});
