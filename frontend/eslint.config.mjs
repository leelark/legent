import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';

const eslintConfig = [
  ...nextVitals,
  ...nextTypescript,
  {
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-require-imports': 'off',
      'import/no-anonymous-default-export': 'off',
      'prefer-const': 'warn',
      'react-hooks/error-boundaries': 'off',
      'react-hooks/set-state-in-effect': 'off',
    },
  },
  {
    ignores: [
      '.next/**',
      'out/**',
      'build/**',
      'next-env.d.ts',
      'test-results/**',
      'public-smoke-*.png',
    ],
  },
];

export default eslintConfig;
