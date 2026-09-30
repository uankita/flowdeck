import { useQuery } from '@tanstack/react-query'
import { listBoards } from './api'

export const boardsQueryKey = (workspaceId: string) => ['workspaces', workspaceId, 'boards'] as const

export function useBoards(workspaceId: string, page = 0, size = 20) {
  return useQuery({
    queryKey: [...boardsQueryKey(workspaceId), page, size],
    queryFn: () => listBoards(workspaceId, page, size),
  })
}
