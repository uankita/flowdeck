import { Toaster } from 'sonner'
import { useDarkMode } from '../hooks/useDarkMode'

/** Mounted once at the app root; call `toast.success(...)` / `toast.error(...)` (from `sonner`) anywhere to show one. */
export function AppToaster() {
  const [isDark] = useDarkMode()

  return (
    <Toaster
      theme={isDark ? 'dark' : 'light'}
      position="bottom-right"
      richColors
      closeButton
    />
  )
}
