/**
 * Клік по затемненню навколо модального <dialog>. Сам `e.target === dialog` для цього не годиться:
 * той самий target буває й при дотику до внутрішніх полів (padding) вікна — і воно мовчки закривалося
 * від дотику біля краю (24.11.2026: так губилося вікно «Задай новий пароль» на телефоні).
 */
export function isBackdropClick(d: HTMLDialogElement, e: MouseEvent): boolean {
  if (e.target !== d) return false;
  const r = d.getBoundingClientRect();
  return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
}
