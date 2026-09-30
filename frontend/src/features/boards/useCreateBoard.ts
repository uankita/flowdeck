import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getErrorMessage } from '../../api/problemDetail'
import { createBoard, type CreateBoardRequest } from './api'
import { boardsQueryKey } from './useBoards'

export function useCreateBoard(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateBoardRequest) => createBoard(workspaceId, body),
    onSuccess: (board) => {
      void queryClient.invalidateQueries({ queryKey: boardsQueryKey(workspaceId) })
      toast.success(`Created board "${board.name}"`)
    },
    onError: (error) => {
      toast.error(getErrorMessage(error))
    },
  })
}
