import js from '@eslint/js';
import ts from 'typescript-eslint';
import globals from 'globals';

export default ts.config(
  { ignores: ['dist', 'public/theme-init.js', 'public/sw.js', 'supabase/functions'] },
  js.configs.recommended,
  ...ts.configs.strict,
  {
    languageOptions: { globals: { ...globals.browser } },
    rules: {
      '@typescript-eslint/no-non-null-assertion': 'off',
      'no-restricted-properties': ['error', { property: 'innerHTML', message: 'Використовуй lit-html render()' }],
    },
  },
  // Межі шарів: lib/data/features — чиста логіка, без UI, view та глобального стану.
  {
    files: ['src/lib/**', 'src/data/**', 'src/features/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [
        { group: ['**/views/*', '**/ui/*', '**/main', '**/api', '**/state'], message: 'Нижній шар не імпортує верхні (views/ui/api/state).' },
      ] }],
    },
  },
  {
    files: ['src/ui/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [
        { group: ['**/views/*', '**/main'], message: 'ui-kit не залежить від екранів.' },
      ] }],
    },
  },
);
