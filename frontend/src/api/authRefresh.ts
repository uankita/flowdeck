import axios from 'axios'
import type { components } from './schema'
import { tokenStore } from './tokenStore'

type TokenPair = Required<components['schemas']['TokenPairResponse']>

// A bare axios instance, deliberately not the intercepted `apiClient` from
// client.ts: routing this call through that instance's response interceptor
// would let a failed refresh recurse into... another refresh attempt.
const rawClient = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '' })

let inFlight: Promise<string> | null = null

async function performRefresh(): Promise<string> {
  const refreshToken = tokenStore.getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  try {
    const { data } = await rawClient.post<TokenPair>('/api/auth/refresh', { refreshToken })
    tokenStore.setTokens(data.accessToken, data.refreshToken)
    return data.accessToken
  } catch (error) {
    tokenStore.clear()
    throw error
  }
}

/**
 * Exchanges the stored refresh token for a new token pair, updating
 * {@link tokenStore}. Single-flighted: concurrent callers (e.g. three
 * queries that all 401 around the same moment, or the interceptor racing
 * {@link AuthBootstrap}'s startup check) share one in-progress request
 * rather than each firing their own.
 *
 * That sharing isn't just an efficiency nicety — it's load-bearing. The
 * backend rotates refresh tokens on every use and treats a second use of an
 * already-rotated token as reuse, revoking the *entire* session family (see
 * the backend README's Authentication section). Two concurrent refresh
 * calls with the same stored token would race to do exactly that to a
 * perfectly innocent user, purely because two requests 401'd at once.
 *
 * @throws whatever axios throws if the refresh token is missing, invalid, or revoked — callers should treat any rejection as "the session is gone."
 */
export function refreshAccessToken(): Promise<string> {
  if (!inFlight) {
    inFlight = performRefresh().finally(() => {
      inFlight = null
    })
  }
  return inFlight
}
