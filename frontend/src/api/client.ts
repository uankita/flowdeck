import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { refreshAccessToken } from './authRefresh'
import { tokenStore } from './tokenStore'

/**
 * The one axios instance every feature's API module calls through. Two
 * interceptors:
 *
 * - **Request**: attaches `Authorization: Bearer <accessToken>` from
 *   {@link tokenStore}, when there is one.
 * - **Response**: on a 401 that isn't from `/api/auth/**` itself and hasn't
 *   already been retried once, refreshes the access token (see
 *   {@link refreshAccessToken} for why that's single-flighted) and replays
 *   the original request with the new one. If the refresh itself fails —
 *   the refresh token is gone, expired, or revoked — the session is over;
 *   {@link onSessionExpired} is how the rest of the app finds out, since an
 *   axios interceptor has no router or component tree to redirect through
 *   itself.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
})

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const accessToken = tokenStore.getAccessToken()
  if (accessToken) {
    // AxiosHeaders (rather than a plain object) is what config.headers
    // actually is by the time an interceptor sees it in axios 1.x — using
    // its .set() keeps whatever headers were already there intact.
    const headers = AxiosHeaders.from(config.headers)
    headers.set('Authorization', `Bearer ${accessToken}`)
    config.headers = headers
  }
  return config
})

let onSessionExpired: (() => void) | null = null

/** {@link AuthBootstrap} registers this once, to redirect to /login when a refresh conclusively fails. */
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler
}

const AUTH_ENDPOINT_PATTERN = /^\/api\/auth\//

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config: RetryableRequestConfig | undefined = error.config
    const status = error.response?.status
    const isAuthEndpoint = config?.url ? AUTH_ENDPOINT_PATTERN.test(config.url) : true

    if (status !== 401 || !config || config._retried || isAuthEndpoint) {
      return Promise.reject(error)
    }

    config._retried = true
    try {
      const newAccessToken = await refreshAccessToken()
      const headers = AxiosHeaders.from(config.headers)
      headers.set('Authorization', `Bearer ${newAccessToken}`)
      config.headers = headers
      return apiClient(config)
    } catch (refreshError) {
      onSessionExpired?.()
      return Promise.reject(refreshError)
    }
  },
)
