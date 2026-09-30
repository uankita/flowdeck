import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // A 4xx will not fix itself; only retry transport/5xx failures.
        if (error instanceof ApiError && error.status < 500) {
          return false
        }
        return failureCount < 2
      },
    },
  },
})

export const queryKeys = {
  boards: ['boards'] as const,
  board: (boardKey: string) => ['boards', boardKey] as const,
}
