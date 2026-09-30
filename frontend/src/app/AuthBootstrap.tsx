import { type ReactNode, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { refreshAccessToken } from '../api/authRefresh'
import { setSessionExpiredHandler } from '../api/client'
import { tokenStore } from '../api/tokenStore'
import { FullPageSpinner } from '../components/FullPageSpinner'

/**
 * Wraps the whole route tree (public and protected routes alike). Two jobs:
 *
 * 1. **Silent session restore on load.** The access token lives only in
 *    memory (see `tokenStore`'s Javadoc), so a page reload always starts
 *    with none — but a persisted refresh token means there's a real session
 *    to recover, not a login screen to show. This blocks rendering
 *    everything below it until that one attempt resolves, so
 *    `ProtectedRoute` never has to distinguish "definitely logged out" from
 *    "haven't checked yet" — by the time anything renders, it's the former
 *    or the session is restored.
 * 2. **Registers the session-expired handler** `client.ts`'s response
 *    interceptor calls when a refresh conclusively fails (bad/revoked
 *    refresh token) — an axios interceptor has no router of its own to
 *    redirect through, so it calls back into this instead.
 */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  const [isBootstrapping, setIsBootstrapping] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    setSessionExpiredHandler(() => navigate('/login', { replace: true }))
  }, [navigate])

  useEffect(() => {
    let cancelled = false

    if (!tokenStore.getRefreshToken()) {
      setIsBootstrapping(false)
      return
    }

    refreshAccessToken()
      .catch(() => {
        // No valid session to restore — ProtectedRoute redirects once this
        // resolves; nothing else to do here.
      })
      .finally(() => {
        if (!cancelled) {
          setIsBootstrapping(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  if (isBootstrapping) {
    return <FullPageSpinner label="Restoring your session…" />
  }

  return <>{children}</>
}
