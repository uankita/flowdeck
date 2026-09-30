/** @type {import('tailwindcss').Config} */
export default {
  // Class-based, not media-query-based: a manual toggle (see
  // src/hooks/useDarkMode.ts) needs to win over the OS preference, and
  // media-query mode can't do that.
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Every color is a CSS variable (defined for both :root and .dark in
        // index.css), not a fixed hex pair. This is what lets `bg-surface`,
        // `text-ink`, etc. just work in both themes with no `dark:` prefix
        // needed anywhere in component code — the variable swaps, the
        // utility class doesn't. `<alpha-value>` is Tailwind's placeholder
        // for opacity modifiers (`bg-surface/50`) to keep working through
        // the indirection.
        surface: {
          DEFAULT: 'rgb(var(--color-surface) / <alpha-value>)',
          muted: 'rgb(var(--color-surface-muted) / <alpha-value>)',
          sunken: 'rgb(var(--color-surface-sunken) / <alpha-value>)',
        },
        ink: {
          DEFAULT: 'rgb(var(--color-ink) / <alpha-value>)',
          muted: 'rgb(var(--color-ink-muted) / <alpha-value>)',
          faint: 'rgb(var(--color-ink-faint) / <alpha-value>)',
        },
        accent: {
          DEFAULT: 'rgb(var(--color-accent) / <alpha-value>)',
          soft: 'rgb(var(--color-accent-soft) / <alpha-value>)',
        },
        border: 'rgb(var(--color-border) / <alpha-value>)',
        danger: {
          DEFAULT: 'rgb(var(--color-danger) / <alpha-value>)',
          soft: 'rgb(var(--color-danger-soft) / <alpha-value>)',
        },
        success: 'rgb(var(--color-success) / <alpha-value>)',
      },
      boxShadow: {
        card: '0 1px 2px rgba(20, 23, 31, 0.06), 0 1px 3px rgba(20, 23, 31, 0.04)',
        lift: '0 8px 24px rgba(20, 23, 31, 0.16)',
      },
      // Named on top of (not instead of) Tailwind's default numeric scale —
      // the numeric scale still handles ordinary component-level spacing
      // (p-2, gap-4, ...); these name the handful of *layout-level* gaps
      // that recur across every page, so they're consistent because they're
      // the same token, not just eyeballed to matching values.
      spacing: {
        gutter: '1.5rem',
        section: '2.5rem',
      },
      keyframes: {
        'skeleton-pulse': {
          '0%, 100%': { opacity: 1 },
          '50%': { opacity: 0.5 },
        },
      },
      animation: {
        'skeleton-pulse': 'skeleton-pulse 1.5s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
