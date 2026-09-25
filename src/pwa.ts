import { sb, errText } from './api';
import { set, state } from './state';
import { alert } from './ui/dialog';

const VAPID = 'BOs6iYFRnxkDtLRmZyTkVqiZemoxT_H7tVeFzJOkDbPHYF3H6klBHyv-0jrZKuWZp4tqkLbInAOu-AIHMmHfoH8';
interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }

let reg: ServiceWorkerRegistration | null = null;
let installEv: InstallEvent | null = null;
const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
const standalone = matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;

export async function initPwa(): Promise<void> {
  addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installEv = e as InstallEvent; set({ canInstall: true }); });
  if (isIOS && !standalone) set({ canInstall: true });
  if (!('serviceWorker' in navigator) || import.meta.env.DEV) return;
  try {
    reg = await navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`, { scope: import.meta.env.BASE_URL });
    const sub = await reg.pushManager?.getSubscription();
    set({ pushOn: !!sub });
  } catch { /* SW недоступний (приватний режим тощо) */ }
}

export async function installApp(): Promise<void> {
  if (installEv) {
    await installEv.prompt();
    if ((await installEv.userChoice).outcome === 'accepted') set({ canInstall: false });
    installEv = null;
  } else void alert('На iPhone: натисни «Поділитися» ⬆️ внизу Safari → «На Початковий екран» ➕');
}

const b64 = (s: string) => Uint8Array.from(atob((s + '='.repeat((4 - (s.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));

export async function togglePush(): Promise<void> {
  if (!reg || !('PushManager' in window))
    return void alert(isIOS && !standalone ? 'На iPhone спершу встанови застосунок на головний екран (кнопка «📱 Встановити»), а потім увімкни сповіщення звідти.' : 'Цей браузер не підтримує сповіщення.');
  const cur = await reg.pushManager.getSubscription();
  if (cur) {
    await sb.from('school_push').delete().eq('endpoint', cur.endpoint);
    await cur.unsubscribe();
    return set({ pushOn: false, pushMsg: { text: 'Сповіщення вимкнено', ok: true } });
  }
  if (Notification.permission === 'denied')
    return void alert('Сповіщення заблоковані для цього сайту. Натисни 🔒 біля адреси сайту → «Дозволи» → «Сповіщення» → «Дозволити», онови сторінку й натисни кнопку ще раз.');
  if ((await Notification.requestPermission()) !== 'granted') return set({ pushMsg: { text: 'Дозвіл на сповіщення не надано' } });
  try {
    const s = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64(VAPID) });
    const { error } = await sb.from('school_push').upsert({ endpoint: s.endpoint, user_id: state.me!.id, sub: s.toJSON() });
    if (error) throw error;
    set({ pushOn: true, pushMsg: { text: state.role === 'parent' ? '✅ Готово! Прийде сповіщення, коли дитина додасть оцінку.' : '✅ Готово! Прийде сповіщення про підтвердження оцінок, бонуси й челенджі.', ok: true } });
  } catch (e) {
    set({ pushMsg: { text: 'Не вдалося: ' + errText(e) } });
  }
}
