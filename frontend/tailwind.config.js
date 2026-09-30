/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: '#ffffff',
          muted: '#f6f7f9',
          sunken: '#eceef1',
        },
        ink: {
          DEFAULT: '#14171f',
          muted: '#5b6472',
          faint: '#8b94a3',
        },
        accent: {
          DEFAULT: '#3b5bdb',
          soft: '#e7ecfd',
        },
      },
      boxShadow: {
        card: '0 1px 2px rgba(20, 23, 31, 0.06), 0 1px 3px rgba(20, 23, 31, 0.04)',
        lift: '0 8px 24px rgba(20, 23, 31, 0.16)',
      },
    },
  },
  plugins: [],
}
