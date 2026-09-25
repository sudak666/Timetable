import js from '@eslint/js';
import ts from 'typescript-eslint';
import globals from 'globals';

export default ts.config(
  { ignores: ['dist', 'public/sw.js', 'supabase/functions'] },
  js.configs.recommended,
  ...ts.configs.strict,
  {
    languageOptions: { globals: { ...globals.browser } },
    rules: {
      '@typescript-eslint/no-non-null-assertion': 'off',
      'no-restricted-properties': ['error', { property: 'innerHTML', message: 'Використовуй lit-html render()' }],
    },
  },
);
