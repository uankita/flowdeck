/**
 * Holds the current auth tokens outside React, as a plain external store
 * (subscribe/getSnapshot — the shape {@link https://react.dev/reference/react/useSyncExternalStore
 * useSyncExternalStore} expects). Two things need to read and write this
 * without going through component props: the axios interceptor in
 * `client.ts` (which runs outside the component tree entirely) and any
 * component that needs to know "is someone logged in" (via
 * {@link useAuthTokens}).
 *
 * **Storage split, deliberately:**
 * - `accessToken` lives only in this module's memory. It never touches
 *   `localStorage`/`sessionStorage`, so an XSS payload that can run JS can
 *   still see it (nothing stops that), but it can't read it back out of
 *   storage after the fact, and it's gone the moment the tab closes or
 *   reloads.
 * - `refreshToken` is mirrored to `localStorage` so a reload doesn't force
 *   a fresh login — {@link AuthBootstrap} uses it to silently re-establish
 *   a session on load. This is the standard trade-off for an API that
 *   takes the refresh token in the request body rather than an httpOnly
 *   cookie (which would need the backend to set it, and isn't how this
 *   backend's `/api/auth/refresh` is designed): some persistence, in
 *   exchange for the refresh token being readable by any JS that runs on
 *   the page. The backend's rotation-with-reuse-detection (see the backend
 *   README's Authentication section) is exactly the mitigation for a
 *   leaked refresh token being replayed.
 */

const REFRESH_TOKEN_STORAGE_KEY = 'flowdeck-refresh-token'

export interface AuthTokens {
  accessToken: string | null
  refreshToken: string | null
}

type Listener = () => void

let state: AuthTokens = {
  accessToken: null,
  refreshToken: localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY),
}

const listeners = new Set<Listener>()

function emit() {
  for (const listener of listeners) {
    listener()
  }
}

export const tokenStore = {
  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },

  getSnapshot(): AuthTokens {
    return state
  },

  getAccessToken(): string | null {
    return state.accessToken
  },

  getRefreshToken(): string | null {
    return state.refreshToken
  },

  setTokens(accessToken: string, refreshToken: string): void {
    state = { accessToken, refreshToken }
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken)
    emit()
  },

  clear(): void {
    state = { accessToken: null, refreshToken: null }
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY)
    emit()
  },
}
