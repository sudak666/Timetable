// Промо-картки Google Play у стилі «Щоденник»: 1080×1920, папір на тлі, заголовок від руки, телефон на скотчі.
// node promo.mjs <корінь проєкту> <тека знімків> <тека результату>
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { writeFileSync, mkdirSync } from 'node:fs';

const [root, shots, out] = process.argv.slice(2);
mkdirSync(out, { recursive: true });
const require = createRequire(join(root, 'package.json'));
const { chromium } = require('@playwright/test');
const f = (p) => pathToFileURL(p).href;
const CAVEAT = f(join(root, 'src/assets/fonts/caveat-700.woff2'));
const NUNITO = f(join(root, 'android/core/designsystem/src/main/res/font/nunito_800.ttf'));
const NUNITO9 = f(join(root, 'android/core/designsystem/src/main/res/font/nunito_900.ttf'));

// *слово* — під маркером. dark — знімок у темній темі (статус-бар світлим текстом).
const SLIDES = [
  { id: 1, shot: 'c-today', bg: 'grid', title: ['Скільки', 'до *дзвінка?*'], sub: 'Таймер уроку, перерви\nй завдання на день', tilt: -2 },
  { id: 2, shot: 'd-t2', bg: 'board', dark: true, title: ['Розклад —', 'як *у щоденнику*'], sub: 'Кожен предмет — свій зошит,\nа «зараз» видно одразу', tilt: 2 },
  { id: 3, shot: 'p-g1', bg: 'kraft', title: ['Оцінки стають', '*кишеньковими*'], sub: 'Курс задаєте ви,\nа гаманець росте до мрії', tilt: -2, deco: 'twelve' },
  { id: 4, shot: 'p-g2', bg: 'lines', title: ['Наліпки', 'за *успіхи*'], sub: 'Досягнення, челенджі\nй шлях нагород', tilt: 2 },
  { id: 5, shot: 'c-look', bg: 'graph', title: ['Свій зошит:', '*папір* і аватари'], sub: 'Лінійка, крапки, міліметрівка, крафт —\nвідкриваються з рівнями', tilt: -2 },
  { id: 6, shot: 'c-hw', bg: 'dots', title: ['Домашка', 'не *загубиться*'], sub: 'Прострочене — зверху,\nзроблене — закреслене', tilt: 2 },
  { id: 7, shot: 'd-t4', bg: 'board', dark: true, title: ['На перерві', '*не нудно*'], sub: 'Вікторина, цікаві факти\nй рюкзак на завтра', tilt: -2 },
  { id: 8, shot: 'p-ok', bg: 'grid', title: ['Батькам —', '*один дотик*'], sub: 'Підтвердити оцінку,\nдати бонус чи челендж', tilt: 2, deco: 'check' },
];

const fibers = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240' stroke-linecap='round' stroke-width='2'%3E%3Cpath d='M114 158l8 2M134 158l12-6M80 98l6 2M96 100l12 2M86 88l16 4M56 88l18 0M6 198l14-2M102 94l8 2M42 168l8-6M152 12l8 0M114 230l20 0M2 64l10 4M64 228l8 4M166 32l10 6M216 218l18 0' stroke='%237a5a2e' stroke-opacity='.2'/%3E%3Cpath d='M90 66l14 6M218 14l6 2M220 122l18-10M28 102l10-4M40 168l8-4M8 174l16 10M222 88l16-6M2 10l20 2M120 6l16-4M154 56l12 0M210 66l12 6M170 52l10-2M104 236l14 2M230 182l14-2M190 140l10-2M136 4l18 4M24 84l18-4M130 150l12-6M94 58l10-2' stroke='%23fff' stroke-opacity='.45'/%3E%3C/svg%3E\")";

const BG = {
  grid: 'linear-gradient(90deg,transparent 88px,rgba(200,50,31,.45) 88px 92px,transparent 92px),linear-gradient(rgba(31,63,166,.10) 2px,transparent 2px) 0 0/54px 54px,linear-gradient(90deg,rgba(31,63,166,.10) 2px,transparent 2px) 0 0/54px 54px,#fbf8f0',
  lines: 'linear-gradient(90deg,transparent 88px,rgba(200,50,31,.45) 88px 92px,transparent 92px),linear-gradient(transparent 80px,rgba(31,63,166,.17) 80px 82px) 0 0/100% 82px,#fbf8f0',
  dots: 'radial-gradient(circle,rgba(31,63,166,.30) 3px,transparent 3.6px) 27px 27px/54px 54px,#fbf8f0',
  graph: 'linear-gradient(rgba(208,102,28,.32) 2px,transparent 2px) 0 0/90px 90px,linear-gradient(90deg,rgba(208,102,28,.32) 2px,transparent 2px) 0 0/90px 90px,linear-gradient(rgba(208,102,28,.13) 1px,transparent 1px) 0 0/18px 18px,linear-gradient(90deg,rgba(208,102,28,.13) 1px,transparent 1px) 0 0/18px 18px,#fbf8f0',
  kraft: `${fibers},#e4d2ae`,
  board: 'radial-gradient(60% 30% at 20% 12%,rgba(237,235,227,.07),transparent 70%),radial-gradient(55% 28% at 85% 70%,rgba(237,235,227,.06),transparent 70%),linear-gradient(rgba(237,235,227,.035) 2px,transparent 2px) 0 0/54px 54px,linear-gradient(90deg,rgba(237,235,227,.035) 2px,transparent 2px) 0 0/54px 54px,#16221d',
};

const DECO = {
  // Червона «12» в обведенні ручкою — як у щоденнику.
  twelve: `<div class="deco twelve"><svg viewBox="0 0 200 200"><path d="M100 18c48 2 82 34 80 80s-40 82-86 80S16 140 20 96 58 20 112 26" fill="none" stroke="#c8321f" stroke-width="7" stroke-linecap="round"/></svg><span>12</span></div>`,
  // Зелена наліпка-галочка.
  check: `<div class="deco check"><svg viewBox="0 0 100 100"><path d="M26 52l16 16 34-36" fill="none" stroke="#fff" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/></svg></div>`,
};

const html = (s) => {
  const board = s.bg === 'board';
  const title = s.title.map((l) => l.replace(/\*(.+?)\*/g, '<span class="mk">$1</span>')).join('<br>');
  const statusInk = s.dark ? '#edebe3' : '#23262f';
  const statusBg = s.dark ? '#16221d' : '#fbf8f0';
  return `<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{font-family:Caveat;src:url('${CAVEAT}') format('woff2');font-weight:700}
@font-face{font-family:Nunito;src:url('${NUNITO}');font-weight:800}
@font-face{font-family:Nunito;src:url('${NUNITO9}');font-weight:900}
*{margin:0;box-sizing:border-box}
html,body{width:1080px;height:1920px;overflow:hidden}
body{background:${BG[s.bg]};font-family:Nunito;position:relative}
.head{position:absolute;left:130px;right:70px;top:120px}
h1{font:700 138px/1.0 Caveat;color:${board ? '#edebe3' : '#1f3fa6'};letter-spacing:-1px}
.mk{position:relative;white-space:nowrap;z-index:0}
.mk::before{content:"";position:absolute;z-index:-1;left:-10px;right:-14px;bottom:8px;height:44px;border-radius:10px 22px 16px 26px;transform:rotate(-1.5deg);
  background:${board ? 'transparent' : 'rgba(255,225,74,.9)'};${board ? 'border-bottom:9px solid #f2d25c;border-radius:0 0 60% 40%/0 0 100% 100%;height:28px;bottom:-4px;transform:rotate(-1deg)' : ''}}
p.sub{margin-top:30px;font:800 44px/1.3 Nunito;color:${board ? 'rgba(237,235,227,.86)' : '#23262f'};white-space:pre-line}
.phone{position:absolute;left:150px;top:${s.title.length > 1 ? 640 : 560}px;width:780px;height:1720px;border-radius:84px;background:#101216;padding:18px;
  transform:rotate(${s.tilt}deg);box-shadow:0 30px 60px -10px rgba(0,0,0,.38),0 8px 18px rgba(0,0,0,.22)}
.screen{width:100%;height:100%;border-radius:68px;overflow:hidden;background:${statusBg};position:relative}
.bar{height:58px;display:flex;align-items:center;justify-content:space-between;padding:6px 52px 0;font:800 27px Nunito;color:${statusInk};position:relative}
.cam{position:absolute;left:50%;top:16px;width:30px;height:30px;margin-left:-15px;border-radius:50%;background:#101216}
.icons{display:flex;gap:12px;align-items:center}
.shot{display:block;width:100%;margin-top:-6px}
.tape{position:absolute;width:230px;height:70px;background:repeating-linear-gradient(90deg,rgba(255,225,74,.62) 0 22px,rgba(255,232,120,.58) 22px 44px);
  clip-path:polygon(0 8%,4% 0,8% 10%,12% 0,16% 8%,20% 0,80% 0,84% 9%,88% 0,92% 8%,96% 0,100% 10%,100% 92%,96% 100%,92% 90%,88% 100%,84% 92%,80% 100%,20% 100%,16% 90%,12% 100%,8% 92%,4% 100%,0 90%);
  box-shadow:0 2px 4px rgba(0,0,0,.08);z-index:3}
.tape.l{transform:rotate(-38deg)}.tape.r{transform:rotate(38deg)}
.deco{position:absolute;z-index:4}
.twelve{right:40px;top:420px;width:200px;height:200px;transform:rotate(10deg)}
.twelve svg{position:absolute;inset:0}
.twelve span{position:absolute;inset:0;display:grid;place-items:center;font:700 130px/1 Caveat;color:#c8321f;padding-top:10px}
.check{right:70px;top:150px;width:170px;height:170px;border-radius:50%;background:#00705a;box-shadow:0 0 0 10px #fff,0 10px 24px rgba(0,0,0,.25);transform:rotate(-8deg)}
</style></head><body>
<div class="head"><h1>${title}</h1><p class="sub">${s.sub}</p></div>
${s.deco ? DECO[s.deco] : ''}
<div class="phone" id="ph"><div class="screen">
  <div class="bar"><span>10:40</span><i class="cam"></i><span class="icons">
    <svg width="30" height="24" viewBox="0 0 30 24"><path d="M15 22L1 7a20 20 0 0 1 28 0z" fill="${statusInk}"/></svg>
    <svg width="26" height="24" viewBox="0 0 26 24"><path d="M2 22h4v-6H2zm6 0h4V12H8zm6 0h4V7h-4zm6 0h4V2h-4z" fill="${statusInk}"/></svg>
    <svg width="46" height="24" viewBox="0 0 46 24"><rect x="1" y="3" width="38" height="18" rx="5" fill="none" stroke="${statusInk}" stroke-width="2.4"/><rect x="5" y="7" width="30" height="10" rx="2" fill="${statusInk}"/><rect x="41" y="9" width="4" height="6" rx="1.5" fill="${statusInk}"/></svg>
  </span></div>
  <img class="shot" src="${f(join(shots, s.shot + '.png'))}">
</div></div>
<div class="tape l" style="left:${150 - 70}px;top:${(s.title.length > 1 ? 640 : 560) + 10}px"></div>
<div class="tape r" style="left:${930 - 160}px;top:${(s.title.length > 1 ? 640 : 560) + 10}px"></div>
</body></html>`;
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
for (const s of SLIDES) {
  const file = join(out, `slide-${s.id}.html`);
  writeFileSync(file, html(s));
  await page.goto(f(file));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(150);
  await page.screenshot({ path: join(out, `${s.id}.png`) });
}
await browser.close();
console.log('done');
