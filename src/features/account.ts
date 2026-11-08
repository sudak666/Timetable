import type { Member } from './rewards';

/** Слово-підтвердження для видалення акаунта (як в Android). */
export const DELETE_CONFIRM_WORD = 'ВИДАЛИТИ';

export const isDeleteConfirmed = (typed: string | null): boolean => (typed ?? '').trim().toUpperCase() === DELETE_CONFIRM_WORD;

/**
 * Наслідки видалення — та сама логіка, що й у school_delete_account() на сервері:
 * останній з батьків видаляє всю сім'ю, інакше — лише свій акаунт і свої дані.
 */
export function deletionWarnings(isParent: boolean, members: readonly Member[], myId: string, familyName: string): string[] {
  const otherParents = members.filter((m) => m.role === 'parent' && m.user_id !== myId).length;
  const children = members.filter((m) => m.role === 'child').map((m) => m.name || 'дитина');
  const out = ['Твій акаунт і вхід буде видалено назавжди. Відновити його неможливо.'];
  if (!isParent) out.push('Усі твої оцінки, бонуси, челенджі, домашку й розклад буде видалено.');
  else if (otherParents > 0) out.push(`Сім’я «${familyName}» лишиться другому з батьків — їхні дані не зникнуть.`);
  else {
    out.push(`Ти — єдиний з батьків, тому сім’ю «${familyName}» буде видалено повністю.`);
    if (children.length) {
      out.push(`Разом із нею зникнуть оцінки, гроші, домашка й розклад: ${children.join(', ')}.`);
      out.push('Акаунти дітей лишаться, але без сім’ї — їм треба буде приєднатися знову.');
    }
  }
  return out;
}
