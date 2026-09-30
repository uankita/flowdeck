import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // A 4xx won't fix itself on retry (and a 401 is already handled by
        // the axios interceptor's own refresh-and-replay, not query
        // retries) — only transport failures and 5xx are worth another try.
        if (isAxiosError(error) && error.response && error.response.status < 500) {
          return false
        }
        return failureCount < 2
      },
    },
  },
})
