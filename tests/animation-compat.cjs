const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'build', 'animation-compat');
const assets = path.join(root, 'app', 'src', 'main', 'assets');
const source = path.join(root, 'app/src/main/java/dev/motion2video/app/ImportedContent.java');
fs.mkdirSync(output, {recursive: true});
const fixture = fs.readFileSync(path.join(__dirname, 'fixtures/local-particles.html'), 'utf8');
const font = fs.readFileSync(path.join(assets, 'fonts/anton-Anton-Regular.ttf'), 'base64');
fs.writeFileSync(path.join(output, 'input.html'), fixture.replace('<head>', '<head><script>window.testFont=' + JSON.stringify(font) + '</script>'));
execFileSync('java', ['-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', output, source, path.join(__dirname, 'java/ImportedContentTest.java')]);
execFileSync('java', ['-cp', output, 'ImportedContentTest', path.join(output, 'input.html'), output, path.join(assets, 'motion-clock.js'), path.join(assets, 'fonts.css')]);
(async () => {
  const browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH || chromium.executablePath(), args: ['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
  try {
    const page = await browser.newPage();
    const errors = [], denied = [], violations = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', msg => {if (msg.type() === 'error') errors.push(msg.text());});
    page.on('requestfailed', request => denied.push(request.url()));
    await page.exposeFunction('testCspViolation', directive => violations.push(directive));
    await page.addInitScript(() => document.addEventListener('securitypolicyviolation', e => window.testCspViolation(e.effectiveDirective)));
    const files = new Map([
      ['https://motion.local/fonts.css', 'fonts.css'],
      ['https://motion.local/gsap.min.js', 'gsap.min.js'],
      ['https://motion.local/vendor/lottie.min.js', 'vendor/lottie.min.js'],
      ['https://cdn.jsdelivr.net/npm/three@0.159.0/build/three.min.js', 'vendor/three.min.js'],
    ]);
    for (const name of fs.readdirSync(path.join(assets, 'fonts'))) files.set('https://motion.local/fonts/' + name, 'fonts/' + name);
    // Simulate WebView's main-document response at the real synthetic origin
    // and StageAssets' approved resource responses. No route reaches the network.
    await page.route('**/*', async route => {
      const url = decodeURI(route.request().url());
      if (url === 'https://motion.local/') return route.fulfill({path: path.join(output, 'offline.html'), contentType: 'text/html; charset=utf-8'});
      const file = files.get(url);
      if (!file) {denied.push(url); return route.abort();}
      return route.fulfill({path: path.join(assets, file), headers: {'access-control-allow-origin': '*'}, contentType: file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : 'font/ttf'});
    });
    await page.goto('https://motion.local/');
    await page.waitForFunction(() => window.secondFrame || window.particleError);
    assert.deepEqual({errors, denied, violations}, {errors: [], denied: [], violations: []});
    const frames = await page.evaluate(() => [firstFrame, secondFrame]);
    for (const frame of frames) assert(frame.canvas === 255 && frame.particles > 50, JSON.stringify(frames));
    assert.notEqual(frames[0].centroid, frames[1].centroid, 'GSAP did not move the WebGL points');
    assert.equal(await page.evaluate(() => document.compatMode), 'CSS1Compat');
    await page.evaluate(() => {
      const event = new Event('webglcontextlost', {bubbles: false});
      window.fixtureGL.dispatchEvent(event);
    });
    assert.equal(await page.evaluate(() => window.__m2vContextFailed), true);
    console.log('PASS: capture-phase WebGL context loss detection.');
    console.log('PASS: offline embedded textures/fonts, GSAP + Three.js particles, Canvas2D, Lottie, WebAssembly and animation frames; zero errors/blocked requests.');
  } finally {await browser.close();}
})().catch(error => {console.error(error); process.exitCode = 1;});
