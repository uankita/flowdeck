import { useCallback, useEffect, useState } from 'react'

const STORAGE_KEY = 'flowdeck-theme'

function prefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function readInitialTheme(): boolean {
  // Mirrors index.html's inline script, which already applied the class
  // before this hook's first render — read that same decision back rather
  // than recomputing it, so the two can never disagree.
  return document.documentElement.classList.contains('dark')
}

/**
 * Dark-mode state, synced with the `dark` class on `<html>` and persisted to
 * `localStorage` under {@link STORAGE_KEY}. `index.html` has a small inline
 * script that applies the same class before first paint (see its comment) —
 * this hook takes over from there, it doesn't duplicate that initial
 * decision.
 */
export function useDarkMode(): [boolean, () => void] {
  const [isDark, setIsDark] = useState(readInitialTheme)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark)
    localStorage.setItem(STORAGE_KEY, isDark ? 'dark' : 'light')
  }, [isDark])

  // If the user hasn't made an explicit choice yet, follow the OS live
  // (e.g. it switches to dark at sunset) — but only until they toggle here,
  // at which point their choice overrides the OS every time.
  useEffect(() => {
    if (localStorage.getItem(STORAGE_KEY)) {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setIsDark(prefersDark())
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  const toggle = useCallback(() => setIsDark((prev) => !prev), [])

  return [isDark, toggle]
}
