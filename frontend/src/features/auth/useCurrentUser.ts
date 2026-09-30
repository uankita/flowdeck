import { useQuery } from '@tanstack/react-query'
import { getCurrentUser } from './api'

export const currentUserQueryKey = ['me'] as const

/** Only ever rendered inside routes the app's `ProtectedRoute` already guards, so no `enabled` gate is needed here. */
export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    staleTime: 5 * 60_000,
  })
}
