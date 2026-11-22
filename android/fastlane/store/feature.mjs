// Банер Google Play 1024×500 у стилі «Щоденник». node feature.mjs <корінь> <тека знімків> <результат.png>
import { createRequire } from 'node:module';
import { join, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { writeFileSync } from 'node:fs';
const [root, shots, outPng] = process.argv.slice(2);
const require = createRequire(join(root, 'package.json'));
const { chromium } = require('@playwright/test');
const f = (p) => pathToFileURL(p).href;
const CAVEAT = f(join(root, 'src/assets/fonts/caveat-700.woff2'));
const NUNITO = f(join(root, 'android/core/designsystem/src/main/res/font/nunito_800.ttf'));
const NUNITO9 = f(join(root, 'android/core/designsystem/src/main/res/font/nunito_900.ttf'));
const ICON = f(join(root, 'android/fastlane/metadata/android/uk-UA/images/icon.png'));
const phone = (img, x, y, r, dark) => `<div class="ph" style="left:${x}px;top:${y}px;transform:rotate(${r}deg)"><div class="sc" style="background:${dark ? '#16221d' : '#fbf8f0'}"><img src="${f(join(shots, img + '.png'))}"></div></div>
<div class="tape" style="left:${x + 92}px;top:${y - 16}px;transform:rotate(${r - 4}deg)"></div>`;
const html = `<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{font-family:Caveat;src:url('${CAVEAT}') format('woff2');font-weight:700}
@font-face{font-family:Nunito;src:url('${NUNITO}');font-weight:800}
@font-face{font-family:Nunito;src:url('${NUNITO9}');font-weight:900}
*{margin:0;box-sizing:border-box}
body{width:1024px;height:500px;overflow:hidden;position:relative;font-family:Nunito;
 background:linear-gradient(90deg,transparent 52px,rgba(200,50,31,.4) 52px 54px,transparent 54px),linear-gradient(rgba(31,63,166,.09) 1px,transparent 1px) 0 0/26px 26px,linear-gradient(90deg,rgba(31,63,166,.09) 1px,transparent 1px) 0 0/26px 26px,#fbf8f0}
.icon{position:absolute;left:96px;top:92px;width:118px;height:118px;border-radius:30px;overflow:hidden;box-shadow:0 0 0 3px #1f3fa6,0 10px 22px rgba(0,0,0,.16);transform:rotate(-4deg)}
.icon img{width:100%;height:100%}
h1{position:absolute;left:92px;top:228px;font:700 80px/1 Caveat;color:#1f3fa6;letter-spacing:-1px}
p{position:absolute;left:96px;top:330px;font:800 25px/1.35 Nunito;color:#23262f;width:420px}
.mk{background:linear-gradient(transparent 12%,rgba(255,225,74,.95) 12% 92%,transparent 92%);padding:0 6px;border-radius:6px}
.ph{position:absolute;width:236px;height:500px;border-radius:34px;background:#101216;padding:8px;box-shadow:0 18px 34px -8px rgba(0,0,0,.4)}
.sc{width:100%;height:100%;border-radius:27px;overflow:hidden;padding-top:16px}
.sc img{width:100%;display:block}
.tape{position:absolute;width:110px;height:34px;background:repeating-linear-gradient(90deg,rgba(255,225,74,.7) 0 11px,rgba(255,232,120,.62) 11px 22px);
 clip-path:polygon(0 8%,5% 0,10% 10%,15% 0,85% 0,90% 9%,95% 0,100% 10%,100% 92%,95% 100%,90% 90%,85% 100%,15% 100%,10% 90%,5% 100%,0 90%)}
</style></head><body>
<div class="icon"><img src="${ICON}"></div>
<h1>Дванадцятка</h1>
<p>Розклад, оцінки й домашка — <span class="mk">для всієї сім’ї</span></p>
${phone('c-today', 560, 78, -6, false)}
${phone('d-t2', 768, 46, 5, true)}
</body></html>`;
const file = join(dirname(outPng), 'feature.html');
writeFileSync(file, html);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
await page.goto(f(file));
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(150);
await page.screenshot({ path: outPng });
await browser.close();
console.log('done');
