import { useQuery } from '@tanstack/react-query'
import { listWorkspaces } from './api'

export const workspacesQueryKey = ['workspaces'] as const

export function useWorkspaces(page = 0, size = 20) {
  return useQuery({
    queryKey: [...workspacesQueryKey, page, size],
    queryFn: () => listWorkspaces(page, size),
  })
}
