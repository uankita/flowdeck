import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthTokens } from '../api/useAuthTokens'

/**
 * Layout route (used as `<Route element={<ProtectedRoute />}>`, wrapping
 * child routes) — renders them via `<Outlet />` when there's an access
 * token, otherwise redirects to `/login`.
 *
 * <p>Doesn't need its own "is the session still being restored" check:
 * {@link AuthBootstrap}, mounted above the whole route tree, already blocks
 * rendering of any route — this one included — until that's resolved one
 * way or the other. By the time this component's body runs, the answer is
 * final for this page load.
 */
export function ProtectedRoute() {
  const { accessToken } = useAuthTokens()
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
